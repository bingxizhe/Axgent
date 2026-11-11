# 权限分级、自身界面防护与技能私有化 实施方案

状态：待确认  
范围：`Axgent/android`（Kotlin/Compose）与 `Axgent/src`（Rhino JS）  
目标：完成用户提出的 5 项需求，并给出可复现的真机验证清单。

## 1. 核心决策

| 议题 | 决策 |
| --- | --- |
| 全局权限配置位置 | `ui-config.json` 顶层 `privilege` 对象；默认 `accessibility=true, shizuku=true, root=false` |
| Agent 权限开关位置 | `ui-config.json → capabilities`，新增 `a11y`、`shizuku`，沿用 `root`；默认均为 `true/true/false` |
| 自动处理弹窗 | 顶层 `autoHandlePopup`，默认 `true`；仅处理白名单系统弹窗，绝不自动授予无障碍/Shizuku/root 权限 |
| 权限等级语义 | 无障碍（UI 直连）→ Shizuku（shell）→ Root（su）；上层关闭时不得使用该层，且不得越级调用 |
| 硬限制位置 | Kotlin `PrivilegeManager`（最终执行闸门）+ JS 工具执行层（每次调用实时读取配置）双层校验 |
| 自身界面防护 | 当前前台包为 `com.axgent.agent`（或 `org.autojs.*`）时，仅允许 `gui_back`、`gui_home`、`gui_launchApp`（不含自身）、文件读写/编辑/创建；其余 GUI/观察操作硬拦截 |
| 脚本绕过防护 | 文件工具与 root/shizuku shell 双层禁止写入 AutoX 脚本目录、应用私有目录、`/data/local/tmp`；root 命令黑名单补充 `settings put secure enabled_accessibility_services`、`pm grant`、`app_process`、写 `ui-config.json` 等 |
| 技能存储 | `filesDir/agent/skills`（应用私有），每技能目录内 `work/` 为技能工作目录；运行时 runs/memory/config 保持 `/sdcard/Download/phone-agent` 不变，避免破坏 shell 工具与既有协议 |
| 技能导入导出 | 导出：每个技能生成 zip 到 `Downloads/Axgent/skills/<folder>.zip`；导入：支持路径输入 + 系统文件选择器，zip 解压前做路径穿越/大小/文件数校验 |
| Shizuku 能力 | 原 root 工具逐一审计；能通过 shell 完成的（如 pm list packages、dumpsys notification、settings）统一走 `PrivilegeManager.exec` 优先 Shizuku；VD/vd_server 经实测不能则标记 root-only |

## 2. 配置与联动

### 2.1 JSON 结构

```json
{
  "privilege": { "accessibility": true, "shizuku": true, "root": false },
  "autoHandlePopup": true,
  "capabilities": {
    "a11y": true,
    "shizuku": true,
    "root": false
  }
}
```

### 2.2 联动规则（Kotlin 与 JS 均需实现）

1. 任一全局开关关闭：对应 `capabilities` 立即写为 `false`。
2. `privilege.accessibility` 打开：`capabilities.a11y = true`。
3. `privilege.shizuku` 打开：`capabilities.shizuku = true`。
4. `privilege.root` 打开：`capabilities.root` 保持原值（新用户默认 `false`）。
5. 载入配置时归一化：全局关闭则 Agent 对应权限强制 `false`；全局 root 关闭则 Agent root 强制 `false`。
6. Agent 侧尝试开启被全局关闭的权限时，`setCapability` 直接拒绝并保持 `false`（UI 同时置灰）。

## 3. 代码改动清单

### 3.1 Kotlin

| 文件 | 改动 |
| --- | --- |
| `AgentModels.kt` | 新增 `PrivilegeConfig`；`AgentCapabilities` 增 `a11y`、`shizuku`；`UiConfig` 增 `privilege`、`autoHandlePopup`；`AgentPaths` 增 `init(context)`、私有 `skillsLibraryDir`、`skillExportsDir` |
| `AgentStore.kt` | 解析/保存 `privilege`、`autoHandlePopup`；`normalizeCaps()`；`setPrivilege(key, enabled)` 实现联动；`setCapability` 对 `a11y/shizuku/root` 做全局前置校验；虚拟屏启动前校验全局 + Agent Root |
| `AgentSettingsScreens.kt` | 「权限分级」区新增三个全局开关 + 「自动处理弹窗」开关；显示“可用/已允许”两态；全局关闭时联动提示 |
| `AgentUi.kt` | 能力面板新增「无障碍权限」「Shizuku 权限」开关；全局关闭时置灰；Root 开关仅在全局 Root 开启时可点 |
| `PrivilegeManager.kt` | 新增 `execShell(command, allowShizuku, allowRoot)`；新增 `readPolicy()`（读 `ui-config.json`）；`shizukuExec/rootExec` 前置校验；`ensureAccessibility/ensureWhitelist` 受全局与 Agent 权限约束；`status()` 输出 `raw` 与 `allowed` |
| `AgentVdRuntime.kt` | 所有 `su`/root 启动路径前置 `PrivilegeManager.canUseRootForAgent()`；失败返回明确错误 |
| `AgentSkillStore.kt` | 技能根目录改为私有目录；新增 `exportZip(folder)`、`importZip(File)`、zip 安全校验；`importDirectory` 保留但目标改为私有目录；单技能 `work/` 自动创建 |
| `AgentSkillScreens.kt` | 每个技能增加「导出」；新增「导入 ZIP」按钮 + 系统文件选择器；显示私有存储路径提示 |

### 3.2 JS

| 文件 | 改动 |
| --- | --- |
| `src/core/priv-policy.js`（新增） | 每次调用读取 `ui-config.json` 的 `privilege`、`capabilities`；导出 `global()`、`caps()`、`allowA11y()`、`allowShizuku()`、`allowRoot()`、`refresh()` |
| `src/tools/privilege.js` | `exec(command)` 改为 `PM.execShellGated(command, allowShizuku, allowRoot)`；`status()` 合并全局与 Agent 权限；新增 `can(level)` |
| `src/tools/shell-ui.js` | `ready()` 使用 `allowShizuku() || allowRoot()`；所有 shell 动作经 `priv.exec`，不再自行调用 su |
| `src/perception/a11y.js` | `nativeReady()` 同时要求全局无障碍 + Agent 无障碍；自身包识别加入 `com.axgent.agent`；`snapshot`/`tapNode`/`setTextNode` 增加自身界面拦截（返回明确错误，不执行点击/输入/观察） |
| `src/tools/index.js` | `execute()` 每次读取实时配置；新增权限矩阵 `TOOL_PERMISSION`；GUI 工具在无 a11y/Shizuku/root 可用时硬拦截；`gui_*` 与 `mobile` 增加自身包检查；`root_shell` 同时校验全局 root + Agent root；文件工具新增受保护路径（脚本目录、私有目录、`/data/local/tmp`）；`schemas()` 使用同步后的能力快照 |
| `src/tools/file.js` | `isProtected` 扩展私有目录/脚本目录/临时脚本目录；禁止创建/编辑会落入 AutoX 脚本目录的 `.js/.sh` |
| `src/core/popup.js`（新增） | 自动弹窗识别与处理；受 `autoHandlePopup` 开关控制；只处理白名单；返回 `{handled, pkg, action}` |
| `src/core/agent.js` | 每步观察前调用 `popup.handle(ctx)`；处理后重新快照；系统提示补充全局/Agent 权限状态；移除“无障碍未开即终止”的旧硬编码，改为按可用后端判断 |
| `src/skills/index.js`、`src/skills/distill.js` | 技能目录解析改为私有 `filesDir/agent/skills`，保留旧外部目录只读兼容一次迁移 |
| `src/config/default.js` | 增加 `privilege`、`autoHandlePopup` 默认值；扩充 `root.denyPatterns` |
| `src/main.js` | 启动前置检查改用 `priv-policy`；虚拟屏仅在有权限时启用；记录权限状态日志 |

## 4. 自身界面与脚本绕过防护细则

### 4.1 允许的自身界面操作

- `gui_back`：返回
- `gui_home`：回桌面
- `gui_launchApp`：仅启动非 `com.axgent.agent` / 非 `org.autojs.*` 的应用
- 文件工具：`file_read`、`file_list`、`file_stat`、`file_search`、`file_write`、`file_append`、`file_edit`、`file_mkdir`、`file_copy`（受沙箱与敏感路径约束）

### 4.2 禁止

- 对 Axgent 界面的 `gui_tap`、`gui_tapXY`、`gui_longPress`、`gui_key`、`gui_setText`、`gui_swipe`
- `mobile` 在物理屏对 Axgent 界面的 click/swipe/set_value/type/key/observe
- 任何 shell `input`/`am start` 到 Axgent 自身 Activity
- 读取/写入应用私有目录、`ui-config.json`、`approval*`、`ask/answer`、脚本目录

### 4.3 脚本绕过防护

1. 文件工具禁止写入 `/sdcard/phone-agent/src`（已存在）并扩展到应用私有目录、`/data/local/tmp`、`/sdcard/Android/data/com.axgent.agent`。
2. `root_shell` 黑名单新增：`settings put secure enabled_accessibility_services`、`settings put secure accessibility_enabled`、`pm grant com.axgent.agent`、`app_process`、`/data/local/tmp/`、`ui-config.json`。
3. `file_write`/`file_edit`/`file_batch` 对 `.js/.jsx/.mjs/.sh/.dex` 目标若位于 AutoX 会扫描/执行的目录，直接拒绝。
4. 技能导入 zip 拒绝绝对路径、`..`、符号链接、超限文件。

## 5. 自动处理弹窗

- 开关：`autoHandlePopup`（设置 → 运行与安全）。
- 处理时机：Agent 每步观察前；处理后重新读取界面，最多连续处理 3 个弹窗。
- 白名单：
  - ColorOS 无障碍引导：`com.oplus.safecenter` 的 `AccessibilityGuideCloseActivity`，点「保持开启」/「确定」
  - 系统运行时权限中的「允许」「仅在使用中允许」（仅对非 Axgent 包）
  - ANR/崩溃/更新提示的「等待」「关闭」「稍后」
- 明确禁止自动点击：Shizuku 授权、Root 授权、无障碍服务开关、安装/卸载确认、Axgent 自身任何设置。
- 开关关闭时 `popup.handle` 直接返回未处理。

## 6. 测试与验收

构建：`Axgent/android` 下执行 `.\gradlew :app:assembleAgentProd --offline`，安装最新 APK。

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 关闭全局无障碍/Shizuku/Root | Agent 三项能力自动变 false；对应工具返回硬拦截错误 |
| 2 | 重新打开全局无障碍/Shizuku | Agent a11y/shizuku 自动 true；root 保持 false |
| 3 | 全局 Root 开、Agent Root 关 | `root_shell`、VD、所有 su 路径均拒绝；日志无 su 调用 |
| 4 | 仅 Shizuku 可用 | app_list、notify_read、settings 类操作成功；VD 明确报 root-only |
| 5 | Agent 操作 Axgent 界面 | 点击/输入/观察被拒；back/home/文件操作正常 |
| 6 | 写脚本绕过 | 写 `/sdcard/phone-agent/src/x.js`、私有目录、`/data/local/tmp/x.sh` 全部拒绝 |
| 7 | 自动弹窗开 | ColorOS 引导弹窗被自动点「保持开启」；Shizuku/Root 授权弹窗不被自动处理 |
| 8 | 自动弹窗关 | 弹窗保持不动 |
| 9 | 技能导出 | 生成 zip，解压内容与技能目录一致 |
| 10 | 技能导入 | 正常 zip 导入成功；含 `../` 的 zip 被拒 |
| 11 | 重启/升级 | 权限配置、Agent 能力、技能均持久化；旧外部技能可迁移一次 |

## 7. 假设与默认值

- “工作目录”按技能工作目录 `skills/<folder>/work` 处理；Agent 运行日志/记忆仍留在外部目录以兼容现有 shell 工具。
- 导出默认保存到 `Downloads/Axgent/skills`；导入同时支持路径输入和系统文件选择器。
- 自动弹窗默认开启，但不自动授予任何敏感权限；如用户希望默认关闭，只需改默认值。
- VD 是否支持 Shizuku 以真机实测为准；实测不通过则保持 root-only 并在 UI 标注。
