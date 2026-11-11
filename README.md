# Axgent

在 Android 手机上运行的通用 GUI Agent：用自然语言下达任务，Agent 自己看屏幕、点按滑动、填表、读写文件。

- 执行引擎：内置 AutoX.js v7（`android/`，v7.2.4），脚本在 **Rhino（ES5）** 上运行
- 界面：原生 Compose（雾蓝玻璃主题）
- 包名 / 入口：`com.axgent.agent` / `org.autojs.autojs.agent.AgentChatActivity`

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/BLUEPRINT.md](docs/BLUEPRINT.md) | 设计基线 v1.0（历史决策与边界，含现状差异） |
| [docs/TOOLS.md](docs/TOOLS.md) | 工具目录、参数、风险与硬限制 |
| [docs/UI.md](docs/UI.md) | 界面结构、页面栈/返回、设置页、性能 |
| [docs/VD.md](docs/VD.md) | 后台虚拟屏、并行子 Agent、控件树后端实测 |
| [docs/VERIFY.md](docs/VERIFY.md) | 真机验证记录（按时间追加） |
| [docs/CACHE.md](docs/CACHE.md) | 上下文与 KV Cache 优化 |
| [docs/WEB.md](docs/WEB.md) | Web 搜索 / 抓取 provider 链 |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | 帧率与耗时测量方法 |
| [docs/HANDOFF-PROMPT.md](docs/HANDOFF-PROMPT.md) | 交接文档生成提示词模板 |

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

## 构建规范（必须遵守）

1. **禁止整体复制/迁移外部项目**：不得把 AutoX 全量仓库、第三方示例工程或历史仓库整包搬进 Axgent。
2. **只整合必要部分**：引入依赖时，仅保留 Axgent 实际构建与运行所需的模块、资源、组件，并放入 Axgent 既有目录结构（`src/`、`android/`、`runtime/` 等），保持精简、必要、可完整构建。
3. **不得携带无关内容**：上游的示例工程、产品线文档、其他 App 的 Activity/资源、语言包、历史仓库（`.git`）一律不进入 Axgent 仓库。
4. **新增依赖需说明必要性**：任何新模块/大体积资源（模型、so、zip）都要能回答「哪个功能需要它、去掉会坏什么」，并在本次改动说明里写清。

> 现状：`android/` 目前是 AutoX v7.2.4 的工作副本（已移除上游文档、示例、测试与 `.git`）。按本规范，仍需继续收敛为「Axgent 真正需要的模块 + 资源」，而不是整包保留。

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

- App 内设置的模型、能力开关、全局权限分级、自动处理弹窗、沙箱目录、外观写到 `ui-config.json`，会覆盖脚本默认值
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
- 单任务默认上限 25 步 / 10 分钟；token 总量不设限（上下文窗口由服务商返回，达 80% 自动压缩）

## 实测环境

| 项 | 值 |
| --- | --- |
| 设备 | OnePlus PLC110（Android 16 / API 36，1272×2800 @560dpi） |
| 最低 / 目标 SDK | 27 / 34 |
| AutoX | 内置 v7.2.4（Rhino 引擎，脚本按 ES5 写） |
| Root | KernelSU（可选；后台虚拟屏与 `root_shell` 需要） |
| 默认模型 | `deepseek-flash`（对话）+ `glm-4.6v-flash`（视觉） |

## 验证记录（最近一次）

| 用例 | 结果 |
| --- | --- |
| 单子任务（设置 → 关于本机 → 读版本） | ✅ run `20261109-035917`：8 步，控件树后端为「无障碍直连」 |
| 并行双子 Agent（读 Android 版本 + 读时钟） | ✅ run `20261109-040803`：Display 17/18 同时运行，dump 时间戳重叠、无锁等待，40s 汇总完成 |
| 页面栈 / 返回 | ✅ 2026-11-10：对话 → 设置 → 关于 → 模型管理 → 新建连接 逐级返回；旋转重建后栈保持 |
| 模型入口 | ✅ 2026-11-10：输入卡模型按钮已移除，统一从顶部标题栏选择，菜单在按钮下方展开 |
| 模块收敛 | ✅ 2026-11-11：删除 `:inrt`/`:apkbuilder`、`dist.zip`、Node/ONNX/MLKit/Tesseract/Quickie，APK 137.0→61.2MB；run `20261111-015733` 虚拟屏 Display 25，41 个工具，`file_list` 正常 |
| 权限分级 | ✅ 2026-11-11：Shizuku 授权后 `:service` 以 shell UID 运行，白名单/开启无障碍走 Shizuku；root 兜底可用；无无障碍时 GUI 降级 run `20261111-234429` 读到 Android 16 |

更早的用例（文件、GUI 闭环、熔断、OCR、Web 搜索、技能、暂停/终止、流体云等）见 [docs/VERIFY.md](docs/VERIFY.md)。

## 开源发布前检查

按「构建规范」收敛后再发布：只保留 Axgent 需要、能完整构建的最小模块与资源集，不整包搬上游。

- **第一步：依赖收敛**。`android/` 已去掉上游文档、示例、测试与历史仓库；剩余模块逐个判定后：`:app` 构建必需的 `:autojs` / `:automator` / `:common` / `:paddleocr`、`assets/vd`、`agent-src`、PaddleOCR 模型保留；`:inrt`、`:apkbuilder`（AutoX 打包产品线）已删除；`:codeeditor` 仅保留编译用代码，不再携带 `dist.zip`。运行时同步移除：Javet Node 引擎（`.mjs`，原生库约 86MB）、ONNX 检测、Google ML Kit OCR、Tesseract、Quickie 扫码（AutoX 开发/编辑器产品线，Agent 均不使用）。
- **密钥**：`src/config/local.js`（本机 API Key）已被 `.gitignore` 排除；设备上的 `ui-config.json` 不在仓库里。提交前可跑一次 `git grep -nE "sk-[A-Za-z0-9]{10,}"` 复核。
- **嵌套仓库**：`android/.git` 已移到 workspace 的 `../.upstream-backup/`；`runtime/` 只保留源码，不再带历史仓库。
- **体积与 LFS**：当前 4031 文件 / 约 69.2MB（`android/` 约 68MB，其中 `paddleocr` 49MB；`runtime/` 仅 0.6MB 源码）；单文件均小于 20MB，GitHub 普通仓库可直接容纳，无需强制 LFS。APK 约 64.9MB（PaddleOCR/OpenCV 原生库为主）。
- **JDK / 代理**：仓库里的 `android/gradle.properties` 不含本机路径；JDK 17 通过 `JAVA_HOME` 或用户级 `~/.gradle/gradle.properties` 的 `org.gradle.java.home` 指定。
- **本地配置**：克隆后自建 `android/local.properties`（`sdk.dir=...`）。
- **权限**：manifest 继承 AutoX 的完整权限集（通讯录、短信、定位等），上架应用商店前建议按实际功能裁剪。

## 许可证

本项目使用 [MIT License](./LICENSE)。

## 致谢

底层执行引擎基于 [AutoX.js](https://github.com/kkevsekk1/AutoX)（v7）。本项目使用的 `android/` 是定制分支，改动集中在 `app/src/main/java/org/autojs/autojs/agent/`。
