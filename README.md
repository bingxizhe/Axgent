# Axgent

[中文](README.md) | [English](README.en.md)

在 Android 手机上运行的通用 GUI Agent：用自然语言下达任务，Agent 自己看屏幕、点按滑动、填表、读写文件。

- 执行引擎：内置 AutoX.js v7（`android/`，v7.2.4），脚本在 **Rhino（ES5）** 上运行
- 界面：原生 Compose（雾蓝玻璃主题），支持中文 / English 切换
- 包名 / 入口：`com.axgent.agent` / `org.autojs.autojs.agent.AgentChatActivity`
- 下载：[GitHub Releases](https://github.com/bingxizhe/Axgent/releases)

## 功能特性

- **自然语言任务**：直接描述目标，Agent 自行规划、观察、操作并给出结果
- **屏幕理解**：控件树 + 截图 + OCR，带画面稳定检测；无障碍不可用时可通过 Shizuku 读取
- **GUI 操作**：点击、长按、滑动、输入、返回、回桌面、启动应用
- **三级权限**：无障碍 → Shizuku → Root 逐级降级；全局与 Agent 双层开关，工具层硬拦截
- **后台虚拟屏**：在独立 Display 上运行其他应用，物理主屏不被打扰；支持并行子 Agent
- **技能**：SKILL.md 渐进式加载，存放在应用私有目录，支持 ZIP 导入导出
- **模型管理**：多服务商、对话模型与视觉模型分开配置，随时切换
- **安全默认**：禁止操作 Axgent 自身界面、文件沙箱、回收站、审批确认、Root 审计
- **中英双语**：首次启动按系统语言自动选择，可在设置中切换

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/BLUEPRINT.md](docs/BLUEPRINT.md) | 设计基线（历史决策与边界，含现状差异） |
| [docs/TOOLS.md](docs/TOOLS.md) | 工具目录、参数、风险与硬限制 |
| [docs/UI.md](docs/UI.md) | 界面结构、页面栈/返回、设置页、性能 |
| [docs/VD.md](docs/VD.md) | 后台虚拟屏、并行子 Agent、控件树后端实测 |
| [docs/VERIFY.md](docs/VERIFY.md) | 真机验证记录 |
| [docs/CACHE.md](docs/CACHE.md) | 上下文与 KV Cache 优化 |
| [docs/WEB.md](docs/WEB.md) | Web 搜索 / 抓取 provider 链 |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | 帧率与耗时测量方法 |

## 构建与安装

要求：JDK 17、Android SDK 34（`android/` 已有 Gradle Wrapper，无需单独装 Gradle）。

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# 构建 agent 变体（首次或改依赖时去掉 --offline）
Push-Location .\android
.\gradlew :app:assembleAgentProd --offline
Pop-Location

# 产物：android\app\build\outputs\apk\agent\prod\app-agent-arm64-v8a-prod.apk
& $adb install -r .\android\app\build\outputs\apk\agent\prod\app-agent-arm64-v8a-prod.apk
& $adb shell am start -n com.axgent.agent/org.autojs.autojs.agent.AgentChatActivity
```

> 首次构建需要联网拉依赖；后续可加 `--offline`。构建失败时先看 `android/local.properties` 里的 SDK 路径。

## 运行方式

App 内置脚本（`assets/agent-src`）：首次安装、App 升级或目标缺失时，会自动部署到 `/sdcard/phone-agent/src`；设备上的 `config/local.js` 不会被覆盖。

1. 首次启动按引导开启：无障碍、所有文件访问、电池白名单、通知（悬浮窗可选）
2. 在输入框发任务 → 自动运行，界面实时显示步骤；后台虚拟屏模式下不占用物理屏
3. 停止：输入框右侧「停止」、通知栏操作，或前台模式下双击音量下键（长按 = 暂停/继续）
4. 日志：`/sdcard/Download/phone-agent/runs/<时间戳>/`（`main.log` / `steps.jsonl` / `final.md`）

## 目录

```
src/                   Agent 内核（Rhino ES5）
  main.js              入口：配置校验、虚拟屏准备、任务循环
  core/                agent 循环、LLM、上下文、记忆、并行、护栏
  tools/               工具注册表与实现（文件/感知/web/浏览器/虚拟屏…）
  perception/          控件树序列化、截图/OCR、稳定检测
  skills/              技能库（SKILL.md）与自动沉淀
  config/              default.js 默认配置；local.js 本地密钥（不入库）
  ui/                  JS 侧面板/预览辅助
android/               Android 工程（AutoX v7 引擎 + Axgent Compose UI）
  app/                 应用模块；产品 UI 在 app/src/main/java/org/autojs/autojs/agent
runtime/               虚拟屏/工具端原生源码（产物在 android/app/src/main/assets/vd）
  vd-server-go/        Go 控制服务（:3070）
  vd-tool-java/        DaemonMain / ToolMain
  agent-hook-apk/      可选 LSPosed hook
scripts/               PC 侧辅助脚本（推送、日志导出等）
docs/                  设计与验证文档
```

运行数据（设备上）：

```
/sdcard/phone-agent/src/                 脚本部署目录
/sdcard/Download/phone-agent/runs/       每次任务日志与工具结果
/sdcard/Download/phone-agent/memory/     FACT.md + journal.jsonl
/sdcard/Download/phone-agent/ui-config.json   UI 写入的配置（模型、能力、外观、权限）
/data/data/com.axgent.agent/files/agent/skills/  私有 SKILL.md 技能库（每技能带 work/，外部不可直接修改）
/sdcard/Download/Axgent/skills/          技能 zip 导出目录
```

## 配置

- 默认值：[src/config/default.js](src/config/default.js)，schema 完整、可提交
- 本地开发：`src/config/local.js` 只写要覆盖的字段（已 gitignore）

```js
module.exports = {
  llm: { baseUrl: 'https://api.deepseek.com', apiKey: 'sk-...', model: 'deepseek-flash' },
  // 全局权限分级：Root 默认关闭；Agent 侧还需单独开启 capabilities.root
  privilege: { accessibility: true, shizuku: true, root: false }
};
```

- App 内设置的模型、能力开关、全局权限分级、自动处理弹窗、沙箱目录、外观与语言写到 `ui-config.json`，会覆盖脚本默认值
- 密钥只保存在本机文件，不上传、不写日志、不在界面回显

## 能力与工具

- 工具集共 58 个候选，按模式与能力开关动态裁剪：前台模式 52 个；开启后台虚拟屏后为 **42 个**——`gui_*`、`shot`、`look`、`ocr`、`wait_text`、`app_list`、`notify_read` 由统一的 `mobile` 工具替代，`root_shell` 仅在「Agent Root权限」开启时出现，`a11y_ensure` 在 Shizuku 或 Root 可用时出现
- 能力开关（设置 → 能力）在 schema 生成与 `execute` 两处硬拦截，不依赖提示词
- 后台虚拟屏：父任务用内部槽位 7，子 Agent 用 1..N；`spawn_task` 并行执行，上限 `min(6, maxParallelDisplays)`，子 Agent 不能再 spawn
- 控件树后端：优先走无障碍服务直连（多线程并发、无锁）；无障碍不可用时回退 ToolMain 串行
- 权限分级：前台 GUI 操作按「无障碍 → Shizuku → root」降级；特权设置（开启无障碍、ColorOS 白名单）按「Shizuku → root」执行。设置 → 运行与安全 可查看三级状态并加入白名单
- 全局权限开关：无障碍 / Shizuku / Root 三类独立控制。关闭时应用与 Agent 都不得使用该层；Agent 能力面板同步提供三项开关，全局关闭时置灰；全局无障碍/Shizuku 开启时 Agent 对应权限自动跟随开启，Root 保持默认关闭
- 自身界面防护：当前前台为 `com.axgent.agent` 时，Agent 只能返回、回桌面、启动其他应用和读写文件；观察、点击、输入、滑动、按键均被代码硬拦截
- 自动处理弹窗：设置 → 运行与安全 的全局开关，只处理 ColorOS 无障碍引导、普通运行时权限、ANR/更新提示等白名单；绝不自动授予 Shizuku/Root/无障碍权限
- 无障碍自动恢复：任务途中检测到无障碍掉线时，自动用 Shizuku（无 root 也可）重新写入服务并等待重连；Agent Root 关闭时绝不会借全局 Root 用 `su` 回退。模型也可显式调用 `a11y_ensure`

## 安全默认值

- 全局 `privilege.root` 与 Agent `capabilities.root` 默认均为 **false**：`root_shell` 不进入工具集，执行前还会同时校验两级开关并记录 `root-audit.jsonl`
- 后台虚拟屏与 `root_shell` 仍为 root 专属；Shizuku 用于 GUI 操作、`pm list packages`、通知栏、secure settings 等 shell 能力，但不会把 `root_shell` 降级为 shell
- Root 硬黑名单（即使开关开启也拒绝）：自行开启无障碍、`pm grant`、`app_process`、写 `ui-config.json`、写 `/data/local/tmp/`、拉起 Axgent 自身 Activity
- 文件工具禁止访问应用私有目录、AutoX 脚本目录、`/data/local/tmp` 与导出目录，防止 Agent 创建脚本绕过权限
- 技能库位于应用私有目录，外部修改不生效；设置 → 技能 支持单个技能导出 zip 与导入 zip（拒绝路径穿越/超限文件）
- 设置 → 运行与安全 → 权限分级：显示无障碍 / Shizuku / Root 状态，手动把 App 加入 ColorOS `accessibility_turn_off_skip_package` 白名单（写入顺序 Shizuku → root）。该白名单覆盖周期性提醒；**首次启用/重装后的那一次安全提示仍会出现**，点「保持开启」即可
- 文件工具默认只能操作 `/sdcard/Download/phone-agent` 与 `/sdcard/Documents`
- 删除进回收站（`runs/.trash/`），不直接 unlink
- `runs/`、`conversations/`、`src/`、`task.txt`、`control.txt`、`ui-config.json` 等系统路径禁止被文件工具写/删；`approval.txt` / `answer.txt` 等人工确认通道同样受保护，Agent 不能自己写入绕审批
- `web_fetch` 只允许 http/https，默认阻断内网地址与带凭据的 URL
- 单任务默认上限 25 步 / 10 分钟；上下文窗口由服务商返回，达 80% 自动压缩

## 实测环境

| 项 | 值 |
| --- | --- |
| 设备 | OnePlus PLC110（Android 16 / API 36，1272×2800 @560dpi） |
| 最低 / 目标 SDK | 27 / 34 |
| AutoX | 内置 v7.2.4（Rhino 引擎，脚本按 ES5 写） |
| Root | KernelSU（可选；后台虚拟屏与 `root_shell` 需要） |
| 默认模型 | `deepseek-flash`（对话）+ `glm-4.6v-flash`（视觉） |

## 借鉴与致谢

Axgent 的实现参考了以下项目；下面写清具体借鉴了什么，未列出的部分均为原创。

### AutoX.js

底层执行引擎基于 [AutoX.js](https://github.com/kkevsekk1/AutoX)（v7）。本项目使用的 `android/` 是定制分支，改动集中在 `app/src/main/java/org/autojs/autojs/agent/`：Agent 对话界面、权限分级、工具桥接与虚拟屏编排。

### Cherry Studio

[CherryHQ/cherry-studio](https://github.com/CherryHQ/cherry-studio) 是跨平台 AI 工作站，Axgent 借鉴了它的信息架构与若干交互模式：

- **服务商 / 模型分层**：服务商保存连接信息（Base URL、API Key、启用状态），对话模型与视觉模型分别引用服务商；同一套连接可复用于两类模型，模型可独立启停与切换
- **模型管理界面**：服务商列表、模型库、从预设创建连接、模型搜索与多选添加的交互组织方式
- **SKILL.md 渐进式加载**：技能目录只向模型暴露已启用技能的名称与描述，模型调用 `skill_load` 时才读取正文，避免一次性占满上下文
- **上下文用量提示与自动压缩**：输入区显示上下文使用率，达到服务商窗口的 80% 时自动压缩历史
- **对话管理**：最近会话、全部会话、搜索与批量删除的组织方式

代码为独立实现，未复制 Cherry Studio 源码。

### agent-mobile-use

[AcidGr/agent-mobile-use](https://github.com/AcidGr/agent-mobile-use) 提供 Android 虚拟副屏与后台控制底座，Axgent 的 `runtime/` 与 `android/app/src/main/assets/vd` 来自该项目：

- **虚拟副屏链路**：`vd-server-go` 控制网关（:3070）、`vd-tool-java` 的 `DaemonMain` / `ToolMain` 工具端，以及 Display 的创建、画面镜像与输入注入
- **LSPosed hook**：`agent-hook-apk` 用于跨屏调度、输入法隔离与免弹窗文字注入（可选安装）
- **KSU 模块与流体云通知**：root 授权、开机拉起与 ColorOS 流体云实时活动
- **并行副屏**：多 Display 同时运行、子 Agent 各占一个槽位；Axgent 在其上实现了 `mobile` 工具、`spawn_task` 与统一的控件树/截图后端

### axgent-promo-video

`axgent-promo-video` 是 Axgent 的 Remotion 宣传视频工程，Axgent 从中沿用了品牌视觉：

- **标识与配色**：Axgent Logo、雾蓝玻璃色板（mist-glass）与浅深色推导方式
- **动效节奏**：界面淡入淡出、选中态与背景呼吸的时长曲线
- 该工程只用于宣传素材，不参与 APK 构建

## 许可证范围（重要）

- **Axgent 原创代码**（`src/`、`docs/`、`agent/` 下的 Kotlin/Compose 代码等）：[MIT License](./LICENSE)。
- **`android/` 执行引擎**：包含基于 [AutoX.js](https://github.com/kkevsekk1/AutoX) 的定制分支。上游 `android/LICENSE.md` 采用 **MPL-2.0 + 非商业性使用限制**，并另附 `android/LICENSE-GPL-V2.md`；因此 `android/` 及其衍生物**不适用 MIT**，也不得用于商业或盈利用途。
- **`runtime/` 与 `android/app/src/main/assets/vd`**：来自 [agent-mobile-use](https://github.com/AcidGr/agent-mobile-use)，遵循其项目许可证。
- 分发整仓或 APK 时，必须同时遵守上述上游许可证；如需商用，请先取得相应授权，或替换/移除受限组件。
