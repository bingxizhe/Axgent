# 后台虚拟屏（App 内 root 桥）

## 来源

本方案把 [AcidGr/agent-mobile-use](https://github.com/AcidGr/agent-mobile-use) v0.8.10 的运行时带进 Phone Agent，而不是要求先刷完整的 KSU 模块：

- `agent_vd.dex`：VirtualDisplay、帧缓存、显示控制；
- `agent_tools.dex`：UiAutomation 控件树与文字注入；
- `vd_server`：本地 3070 REST 网关；
- `run_daemon.sh`：通过 root `app_process` 启动 DaemonMain。

源码/上游许可证：MIT。二进制位于 `android/app/src/main/assets/vd/`。

## 启动链路

```text
设置 → 运行与安全 → 后台虚拟屏
        ↓
AgentStore.startTask
        ↓
AgentVdRuntime.ensureServer
  1. 检查 127.0.0.1:3070/api/status
  2. 没有则通过 su 把 assets/vd/* 复制到 /data/local/tmp
  3. su 启动 vd_server（root）
        ↓
JS agent.run
  4. POST /api/start
  5. POST /api/mode {mode:"background"}
  6. 只向模型暴露 mobile 工具
        ↓
mobile action → /api/action
preview    → /api/screenshot
pause      → 只停止模型，保留 Display 和 App
resume     → 继续使用原 Display
stop       → 移除该 Display 上的任务 + /api/mode idle + /api/stop
```

## 前置条件

- KernelSU / APatch / Magisk 已启用 root；
- 在 root 管理器中给 `com.axgent.agent`（正式包名另行调整）授权；
- Android 15/16 真机；当前设备已满足系统版本；
- LSPosed 不是基础 VirtualDisplay 的硬前提，但跨屏焦点、IME 隔离和通知增强仍依赖上游 `agent_hook.apk` 的 LSPosed hook。
- 本机已启用：Vector（LSPosed 2.x fork，管理器入口是通知栏「Vector 已加载」）中安装 `com.agent.mobileuse`（`agent-hook-apk`），作用域勾选 `DeepSeek Agent`、`System Framework`、`系统界面`，重启后模块状态 `[ACTIVE]`；`/api/status` 返回 `lsposed_active:true`。

## 接口

```text
GET  /api/status
POST /api/start
POST /api/stop
POST /api/mode       {"mode":"foreground|background|idle"}
POST /api/action     observe/click/swipe/set_value/type/key/wait/launch_app
GET  /api/dump_ui?raw=1
GET  /api/screenshot
GET  /api/apps?query=...
```

`mobile` 工具是虚拟屏模式下的唯一 UI 工具，避免 AutoX 的无障碍树与虚拟屏 Display 不一致。

## 生命周期

- `AgentChatActivity` 启动时，如果配置开启了后台虚拟屏，会在后台线程调用 `ensureServer`；因此设备重启、App 重装后打开 App 会自动重建 `/data/local/tmp` 运行时。
- `run_daemon.sh` 必须使用 LF 行尾；Windows CRLF 会让 Android `/system/bin/sh` 报 `syntax error: unmatched 'if'`，不要重新复制未规范化的脚本。
- `ASSET_VERSION` 变化时，App 会重启旧 `vd_server` 并替换运行时；未变化则复用现有进程，避免每次启动都销毁副屏。
- Root 授权由 KernelSU/APatch/Magisk 管理。`adb install -r` 通常保留 UID，但**卸载重装或包身份变化会丢失授权**，需要在 root 管理器重新授权。

## 当前验证状态

- 运行时 assets 已打包；
- 已实测 root 启动链路：文件复制到 `/data/local/tmp`，`vd_server` 监听 `127.0.0.1:3070`；
- 已实测 `/api/start` 创建真实 Display：`display_id=9`、`1264x2800 @ 560 DPI`；
- 已实测 `/api/mode background`、`/api/apps`、`/api/action launch_app`、`/api/action click`、`/api/screenshot`；副屏可启动设置并进入电池页；
- App 侧脚本在虚拟屏模式下已跳过 AutoX 无障碍前置检查；
- 已启用 LSPosed hook（见上）：IME 隔离实测——在 Display 4 的「设置搜索框」上点击并注入文字，物理屏 `mInputShown=false`、焦点仍是 Phone Agent，副屏 Settings 与物理屏 Agent 可同时各自持有焦点；`lsposed_active:true`。
- 尚无 LSPosed 时，跨屏焦点/IME/通知增强可能降级；基础显示、截图、点击已验证；
- App 内 `AgentStore.startTask` 已实测：run `20261031-051411`，日志出现 `后台虚拟屏就绪：Display 10，模式 background`，连续 `mobile -> ok` 启动设置并完成；
- 发送按钮与自动化测试共用 `startTask`；本轮修复了主线程 HTTP/root 准备、`Text file busy`、4 秒启动超时三个静默失败点。

## 2026-10-31 二次验收（build81/82 + 脚本补丁）

- 正常 Composer 手动发送（触摸输入 + 点发送）闭环：run `20261031-054936`，`后台虚拟屏就绪：Display 11`，多次 `mobile -> ok`，任务结束后 `/api/status` 为 `stopped`、`display_id=-1`。
- 暂停保留副屏：run `20261031-060033`（Display 16）；写入 pause 后 `/api/status` 仍为 `running` 且 Display 不变，resume 后继续出步。
- 终止释放副屏：同一 run 点界面停止按钮后，日志 `用户停止（流式中断)` → `虚拟屏任务清理：Display 16 移除 1 个` → `/api/stop` 释放 Display；物理屏焦点保持 Phone Agent。
- 真 VD 悬浮预览：`.preview.png` 由 `/api/screenshot` 写入，悬浮窗显示副屏画面（设置/桌面帧均验证）；关闭预览不终止任务（run `20261031-061819` 关闭后仍 `running`）。
- 预览显示限制：副屏前台若为启用 `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` 的页面（实测是「设置 → 无障碍」），Android 会全局隐藏非系统悬浮窗（`mForceHideNonSystemOverlayWindow=true`），此时预览不可见；离开该页面后自动恢复，不是权限或代码缺陷。
- 重启恢复（实测）：真实 `adb reboot` 后 `vd_server` 进程消失；打开 App 后 `AgentVdRuntime.ensureServer` 重新复制 `/data/local/tmp` 运行时并启动 `vd_server`（新 pid 12342），`.version` 保持 2，`/api/status` 正常。注意：`/data/local/tmp` 位于 `/data` 分区，重启**不会**清空文件，只会杀掉进程；旧交接里“tmpfs 会清空”的说法不准确。
- 完成态修复：模型常忘记调用 `task_done`，旧逻辑把有正文的结束判为“未完成”，UI 会红字失败并提示续跑。`agent.js` 现在把“有正文且非用户中止”的无工具输出按完成处理；run `20261031-134120` 日志出现 `模型未调用工具：正文视为最终回答，按完成处理`，`checkpoint.done=True`，日志不再出现 `任务未完成，可续跑`。
- 乱码修复：`agent.js` 中历史 `�?` 坏字符（停止/暂停/卡死/续跑等文案）已全部修正为正常中文。
- 预览收尾：`preview.js` 在 agent `done` 后立即停止截屏，避免 Display 释放后继续轮询产生 `preview: HTTP 404`。
- 卸载重装：`adb uninstall` + `adb install` 成功；UID 变化导致 KernelSU 授权失效，必须人工重新授权（用户已授权，`su` 恢复后 `vd_server` 可重启）；`/data/local/tmp` 与 `/sdcard/Download/phone-agent` 都保留。首次引导完成时旧实现用内存默认值覆盖磁盘配置，导致 `models`/`visionModels` 清空、`virtualDisplay` 回 false；已修 `AgentStore.setOnboarded`（只写 onboarded 标记并 `loadConfig()`，不再 `saveConfig(config)`），build82 已安装。设备配置已从 `/sdcard/phone-agent/src/config/local.js` 恢复：`deepseek-flash` + `glm-4.6v-flash`、`virtualDisplay=true`。
- build82 回归：run `20261031-142144`（Display 5，`lsposed_active:true`，`done=true`，清理 1 个副屏任务，物理屏焦点保持 Phone Agent）。

## 终止收尾 / 错误提示 / 配置备份（build89–91，2026-10-31）

- 终止落盘标记：`AgentStore.stop` / `onExternalStop` 在 `control=stop` 之外，还会给最新 run 写 `.stopped`。`applyState` 把它当作已结束（DONE），`buildRunMessages` 在没有结论时补一条「用户终止」；脚本被强杀/来不及写 checkpoint 也不会在下次打开时显示“运行中/失败”。实测：暂停中强停 App → 重开 App，Display 19 被释放，run `153025` 显示 `完成 / 用户终止`。
- 虚拟屏错误横幅：`AgentStore.vdError` + `VdBanner`（[AgentOnboarding.kt](file:///c:/Users/huash/Documents/phone-agent/android/app/src/main/java/org/autojs/autojs/agent/AgentOnboarding.kt)）。root 未授权时按钮为「去授权」并打开 KernelSU；其他错误为「重试」。从 KernelSU 返回后 `onResume` 自动重试一次。root 被拒场景未实测（需要撤销授权）。
- 配置备份：`saveConfig` 写盘前把上一版复制到 `ui-config.bak`；`loadConfig` 在 `ui-config.json` 缺失时自动从备份恢复。实测删除 `ui-config.json` 后重启 App，配置自动恢复（models=1、virtualDisplay=true、deepseek-flash）。
- 前台服务：确认 `AgentTaskService` 实际启动（`dumpsys activity services` 有 ServiceRecord、通知渠道 `phone_agent_task` 存在）；通知栏「暂停/继续/终止」按钮未单独点按回归（与悬浮预览共用 `AgentStore.onExternalPause/Resume/Stop` 路径，后者已实测）。

## 暂停 / 继续 / 终止（build83–88，2026-10-31）

操作入口：对话页输入框右侧的「暂停/继续」按钮 + 红色终止按钮、通知栏动作、悬浮预览的暂停/继续/终止；三处都会同步 `AgentStore.status`。

- 暂停：`AgentStore.pauseTask` 写 `control.txt=pause`、`paused.txt`，状态置 PAUSED；脚本的流式回调检测 `state.paused` 立即中止模型输出并回到循环顶部等待，**保留同一个 Display、vd_server 进程和已加载资源**。实测 run `20261031-145111`：流式中暂停 → 同一 Display 10 上继续执行。
- 继续：`resumeTask` 写 `control.txt=resume` 并删除 `paused.txt`，脚本 `resumeFromPause` 后从下一步继续，无需重建虚拟屏。
- 暂停发生在虚拟屏准备阶段：`startTaskReady` 保留 `paused.txt`（`keepPaused`），`main.js` 启动时检测到该文件会先 `agent.pause()`，因此脚本在第一步 LLM 请求之前就处于暂停。实测 run `20261031-150842/151509`：暂停 30s+ 内没有任何 `llm usage`，点继续后才开始请求。
- 终止：`AgentStore.stop`/`onExternalStop` 立即写 `control.txt=stop`、清 `paused.txt`、清内存中的实时文本/待办/待确认，并由 `AgentVdRuntime.stopDisplayAsync()` **直接调用 `/api/stop`**；脚本侧同时检测 stop 中止流式输出并写最终状态。实测 run `20261031-150551`：点终止后 <1s `/api/status` 变 `stopped`，日志 `用户停止（流式中断)`/`人工未接管，已停止`。
- 销毁副屏不会再把副屏里的 App 顶到物理屏：`AgentVdRuntime.stopDisplay` 在 `/api/stop` 前先用 root 执行 `am stack list` + `am stack remove` 清理该 Display 的非 home root task（与 `agent.js clearVdTasks` 双保险）。实测暂停后终止：物理屏焦点保持 Phone Agent，脚本日志显示 `虚拟屏任务清理：Display N 移除 0 个`（Kotlin 已先移除）。
- 进程被杀/重启兜底：`AgentStore.scriptLaunched` 只在本进程真正拉起脚本后为 true；App 启动时若发现残留 Display 且本进程没跑脚本，就按终止处理（停 Display、清 paused.txt、状态置 DONE）。实测强停 App 后重开：残留 Display 11 被释放。
- 相关 UI 状态修复：同一句任务重复发送时旧 run 的 checkpoint 会把新任务状态刷回上一次结果；暂停在第一步前时 checkpoint 还不存在，曾被 10 秒“引擎未启动”误判成 FAILED。`refresh` 现在同时校验 run 目录新鲜度，并允许未生成 checkpoint 的新 run 进入对话列表。

## 两处已修复的“物理屏被顶掉”

1. **副屏销毁时的任务迁移**：Android 移除 Display 时会把副屏任务迁移到默认屏并置前，任务结束后用户看到的是副屏里的设置页。`src/core/agent.js` 新增 `clearVdTasks`：销毁前解析 `am stack list`，对目标 Display 的非 home root task 执行 `am stack remove`。日志应出现 `虚拟屏任务清理：Display N 移除 M 个`。
2. **脚本启动瞬间跳无障碍设置**：AutoX 在无障碍未开启时，`auto.service`/`events.observeKey()` 会调用 `goToAccessibilitySetting()` 并抛异常；旧 `main.js` 在虚拟屏模式仍访问这些 API，导致每次发任务都把物理屏切到系统「无障碍」页。现在 `main.js` 在 `virtualDisplay` 模式下不访问 `auto`/`events`，音量键热键跳过（用界面停止按钮或通知操作），日志为 `音量键未启用：虚拟屏模式跳过音量键（用界面或通知停止）`。

## Hook 模块重建（0.8.11，2026-11-02）

- 移除 `HookEntry.hookActionButton()` 及调用，侧键不再被劫持拉起 `DemoDialogActivity`；保留 VD/IME/截图排除 hook。
- 因原签名 key 不在本机，使用新 debug key 重建 `com.agent.mobileuse 0.8.11 / versionCode 811` 并卸载重装；签名 key 位于 `runtime/agent-hook-apk/debug.keystore`，后续升级不再需要卸载。
- **重装后尚未在 Vector 重新启用模块、也未重启设备**；重启前 VD hook 不应视为生效。回归判据：logcat 出现 `[AgentMobileUseHook] System server loaded`，侧键无 DeepSeek Agent 弹出，VD 任务日志出现 `后台虚拟屏就绪：Display N` 且多次 `mobile -> ok`。
- 当前 UI 配置 `virtualDisplay=false`（本轮协议/权限测试使用的临时值），最终回归前需按用户设置恢复为 `true`。

## 2026-11-09 UiAutomation 串行瓶颈实测（可行性验证）

问题：并行的物理输入（`input -d`）本身不排队，但读控件树、节点转坐标、写值都走 ToolMain，而
UiAutomation 是系统单实例，因此子 Agent 的观察/写入被 `/data/local/tmp/tool_cmd.lock` 强制串行；
每次调用还要新起 `app_process` 并在 disconnect 后固定等待 400ms。

### 实测 1：双 UiAutomation 连接（失败）

两个 `app_process com.agent.ToolMain daemon` 同时启动，第二个立即失败：

```
java.lang.IllegalStateException: UiAutomationService ... already registered!
  at com.android.server.accessibility.UiAutomationManager.registerUiTestAutomationServiceLocked
```

结论：UiAutomation 全局单实例，无法通过多进程/多实例解决。

### 实测 2：串行 ToolMain 单次耗时

`sh /data/local/tmp/tool_cmd.sh "tree 9"` 连续 3 次：**976 / 874 / 977 ms**。

### 实测 3：AccessibilityService 并发查询（成功）

- 同时启用 6 个 AccessibilityService（同一进程，独立连接）：`a11y=p1,p2,p3,p4,p5,p6` 全部 bound。
- 6 个服务各自并发扫 5 块屏（Display 0/7/8/9/10），每轮 25–50ms，全部成功，无报错。
- 单个服务内部再开 6 个线程并发查询：每线程 `[93, 5, 3.5, 4.8, 5.1]ms`，**没有任何串行化**。
- 在 Display 9 启动设置页后，各服务同时读到 `d9:1w/90n/com.android.settings`，内容正确。

### 结论

1. AccessibilityService 路径（`getWindowsOnAllDisplays` + 按 displayId 过滤）**可以完全并行**，
   冷启动约 94ms、热查询约 5ms，比当前串行的 874–977ms 快 20–200 倍。
2. 只需要 **1 个** AccessibilityService 即可支持 6 个并发读写（每线程独立 binder 调用）；
   多开服务不是并行前提，只用于隔离事件回调与降级容错。
3. 点击/滑动/按键继续走 `input -d`，本就无锁；`set_value` 可改为节点 `ACTION_SET_TEXT`，
   不再需要 ToolMain 与剪贴板。
4. 启用 6 个 a11y 服务需要写 `enabled_accessibility_services`（root 或 adb 一次），普通 App 无法静默完成。
5. 验证用探针组件（A11yProbeService1..6 / A11yProbeReceiver / a11y_probe_config）已从代码与清单移除，
   设备上的临时服务也已清理，ui-config 未改动。

## 2026-11-09 并行控件树改造（无障碍直连）

按上面的结论落地：控件树不再走 UiAutomation，改成复用 AutoX 无障碍服务，单进程多线程并发。

### 实现

- 新增 `app/src/main/java/org/autojs/autojs/agent/AgentA11yTree.kt`
  - 取 `com.stardust.view.accessibility.AccessibilityService.instance`，`windowsOnAllDisplays` 按 displayId 过滤后建树；
    输出格式与 ToolMain `tree` 完全一致（`ok display=… windows=…` 头 + `# columns:` + 行），`vd-direct.js` 无需改解析。
  - `dump` / `resolve` / `setText` / `setClipboard` / `available` / `displays`；节点表按 displayId 缓存，供点击坐标与写值复用。
  - dump 超过 80ms 打 `D/AxgentA11yTree: dump dN xx.xms`，用于长期观测。
- `src/tools/vd-direct.js`：新增 `nativeReady()`；`observe` / `resolve` / `set_value` / 非 ASCII 剪贴板优先走 native，
  服务不可用或抛异常时逐项回退 `tool_cmd`（保留改造前行为）。
- `src/main.js`：虚拟屏就绪后打印后端，实测输出 `控件树后端：无障碍直连（并行，无锁）`。
- `AgentStore.startTask`：虚拟屏准备线程里先 `runCatching { ensureAccessibilityByRoot() }` 再启动 VD；
  root 开启失败不影响任务（脚本会回退串行）。已开启时 `hasAccessibility()` 直接返回，不产生额外等待。

### 实测：两个子 Agent 真并行（run `20261109-040803`）

任务：并行派两个 `spawn_task`（A 读设置里的 Android 版本，B 读时钟界面时间）。父任务 Display 16，子任务 Display 17 / 18。

- 父日志：`控件树后端：无障碍直连（并行，无锁）`；两个子任务 04:08:05 / 04:08:08 创建。
- 两子 Agent 的 `mobile -> ok` 在同一时间窗内交错：s1 在 +3/+7/+9/+13/+15s，s2 在 +2/+4/+11/+14s；无任何排队/等待日志。
- logcat 里 d17 / d18 的 dump 在同一 `:script` 进程内多线程重叠：
  d17 `04:08:20.805` 完成（479.1ms，起 20.326），d18 `04:08:21.262` 完成（500.2ms，起 20.762）；
  另 d17 `25.696`（96.5ms）与 d18 `25.739`（284.0ms）同样重叠。全程无 `tool_cmd.lock` 争用。
- dump 单次 96.5–544.9ms（冷/热混合，且多在点击后立即取树，需要等界面稳定），对比改造前嵌套 ToolMain 的 874–977ms。
- 结果：A 读到 Android 16 / PLC110_16.0.10.500，B 读到 04:08，父任务 40s 汇总完成。

### 回退与注意

- 无障碍未开或被系统回收时 `nativeReady()` 为 false，自动回退 ToolMain 串行后端，功能不变，只是不再并行。
- `getWindowsOnAllDisplays` 需要 **Android 11 / API 30**；更低版本 `available()` 直接返回 false，同样走串行后端（虚拟屏本身也只在较新系统上验证过）。
- 首次通过 root 开启无障碍会有 1.2s 等待（`ensureAccessibilityByRoot`），仅发生在服务未开启时；`su` 8s 未返回会直接放弃并回退，不会把任务卡在准备阶段。
