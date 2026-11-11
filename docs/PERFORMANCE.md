# Phone Agent UI 性能分析与验证报告（2026-11-02）

## 1. 测试环境

| 项 | 值 |
| --- | --- |
| 设备 | OnePlus PLC110（OP60EDL1） |
| 系统 | Android 16 / SDK 36 |
| 屏幕 | 1272×2800 @560dpi，系统峰值 144Hz |
| 应用 | Phone Agent 7.2.4 / 724，`com.axgent.agent` |
| 构建 | `agent` flavor + `prod` buildType（`assembleAgentProd`） |
| 采集工具 | `adb shell dumpsys gfxinfo <pkg>`（汇总）与 `framestats`（逐帧） |

> 设备峰值 144Hz，系统 `Janky frames` 以 144Hz 的 6.9ms 帧预算判定，比 60fps 的 16.7ms 严格得多。
> 本报告同时给出 60fps 阈值（帧耗时 >16.7ms）的逐帧超标比例。

## 2. 问题根因

### 2.1 主线程轮询与 Compose 状态抖动（周期性卡顿主因）

- `AgentChatActivity.AgentApp` 的轮询协程每 120ms（运行中）/800ms（空闲）调用 `AgentStore.refresh()`，原先直接跑在主线程。
- `AgentStore.refresh()` 每轮会：
  - `listFiles()` 扫描 `runs/` 全部目录；
  - 逐个读取 `checkpoint.json`（单个文件包含完整 `messages`，可达数十 KB）；
  - 读取并解析不断增长的 `stream.jsonl`；
  - 无条件 `messages.clear()` + `messages.addAll(...)`。
- 800ms 的空闲轮询与整表清空/重加，正好对应“约一秒一卡”；运行中 120ms 轮询进一步放大。
- 空闲时权限检查还会在主线程读取 `Settings.Secure`、`PackageManager`、`NotificationManager` 等 binder 状态。

### 2.2 流式输出路径

- `readLive()` 每 120ms 重读整个 `stream.jsonl` 并逐行 `JSON.parse`，文件越长越慢。
- `ChatScreen` 有 4 个独立 `LaunchedEffect`，分别监听 `liveText/liveReasoning/liveRetry` 长度并调用 `scrollToItem`；一次轮询可能触发多次 LazyColumn 重排。
- `LiveCard` 在 LazyColumn 内部，每次文本增长都让列表项重新测量，并触发跟随滚动。
- 流式阶段每 120ms 重建整棵 Markdown 组件树，长回答时单帧可达数十毫秒。

### 2.3 其他主线程 I/O 与重复测量

- `AgentTaskService` 的 1.5s 通知刷新：`listFiles`、读 `checkpoint.json`、读 `steps.jsonl` 全在主线程。
- `AgentPreviewOverlay` 每 120ms 在主线程 `exists/readText/lastModified`，帧变化时还在主线程 `BitmapFactory.decodeFile`。
- `FinalSources` 在组合阶段调用 `File.exists()`；`ToolStepCard` 使用 `Row(Modifier.height(IntrinsicSize.Min))`，每张步骤卡双倍测量。
- `MarkdownText` / `mdInline` 在每次重组时重新分词与构建 `AnnotatedString`。
- `agent-mobile-use` 侧预览与主 UI 无直接关系，但预览打开时 120ms 主线程轮询会抢占 UI 线程。

### 2.4 线程安全回归（测试中发现并修复）

把 `refresh()` 直接移到 IO 线程后，出现：

```
java.lang.IllegalStateException: Reading a state that was created after the snapshot was taken
```

Compose 的 SnapshotState 读写必须在一致快照内完成。最终方案是把后台 refresh 包进
`Snapshot.withMutableSnapshot { refreshState() }`，让整轮状态更新原子应用；主线程的
`newConversation/openRun` 等交互仍走全局快照，冲突时本轮 refresh 跳过、下一轮重试。

## 3. 优化方案

| # | 优化 | 文件 |
| --- | --- | --- |
| 1 | 轮询降到运行中 200ms / 空闲 1000ms；后台 state 读写包进 `Snapshot.withMutableSnapshot` | `AgentChatActivity.kt`、`AgentStore.kt` |
| 2 | `messages` 改为增量同步：内容不变直接返回，变化时只替换/追加差异项 | `AgentStore.kt` |
| 3 | `checkpoint.json` 解析改为轻量 `RunMeta` 投影，按 `(mtime, length)` 缓存；run 目录扫描 1s 节流；`conversationId` 永久缓存 | `AgentStore.kt` |
| 4 | `stream.jsonl` 改为 `RandomAccessFile` 增量读取，只解析新增完整行 | `AgentStore.kt` |
| 5 | 流式文本发布限制为最多 5fps；流式卡片只渲染最近 500 字，最终回答仍完整 Markdown 渲染 | `AgentStore.kt`、`AgentUi.kt` |
| 6 | 流式卡片移出 LazyColumn，固定在列表下方；删除多个 live 滚动 effect，只在消息数量变化时跟随一次 | `AgentUi.kt` |
| 7 | 流式阶段/思考阶段不再重建 Markdown 树；`mdInline`、表格解析加 `remember` | `AgentUi.kt`、`Markdown.kt` |
| 8 | `ToolStepCard` 左侧状态条改用 `drawBehind`，移除 `IntrinsicSize.Min` 双倍测量 | `ToolCards.kt` |
| 9 | 通知进度与预览悬浮窗的读文件/解码全部移到后台线程；预览帧只在主线程贴图 | `AgentTaskService.kt`、`AgentPreviewOverlay.kt` |
| 10 | 权限状态检查移到 IO，最多 2s 一次；VD 结果统一 post 到主线程 | `AgentChatActivity.kt` |
| 11 | 模型服务列表/模型选择列表懒加载；`provider-models.json` 解析移到 IO | `AgentModelScreens.kt` |
| 12 | `FinalSources` 的 `File.exists` 加 `remember` 缓存 | `AgentUi.kt` |
| 13 | 模型/思考菜单改为同窗口覆盖式内联菜单（Scrim + 卡片），彻底移除 `DropdownMenu`/Popup Window；点击聊天区或返回键关闭。2026-11-10 起模型菜单锚定标题栏按钮下方，思考菜单仍贴输入框 | `AgentUi.kt`、`AgentShell.kt` |

## 4. 真机验证结果

### 4.1 优化前（旧 APK，仅采集交互基线）

| 场景 | 帧数 | Janky（144Hz） | p95 | p99 | Missed Vsync | Slow UI |
| --- | --- | --- | --- | --- | --- | --- |
| 抽屉 + 能力弹窗 + 模型菜单 | 622 | 48（7.72%） | 29ms | 250ms | 19 | 47 |
| 服务商列表连续滚动 | 397 | 26（6.55%） | 20ms | 200ms | 7 | 26 |

### 4.2 优化后（最终 APK）

| 场景 | 帧数 | Janky（144Hz） | p95 | p99 | 60fps 超标（>16.7ms） |
| --- | --- | --- | --- | --- | --- |
| 静置 12s | 0 | 0 | — | — | 0 帧重绘 |
| 抽屉 + 能力弹窗 + 内联模型菜单 | 616 | 23（3.73%） | 13ms | 34ms | 最近 120 帧 5.00%（p95 14.5ms，菜单组合 1–2 帧约 33ms） |
| 服务商列表连续滚动 | 589 | 19（3.23%） | 15ms | 18ms | 最近 120 帧 0% |
| 历史长对话连续滚动 | 721 | 62（8.60%） | 18ms | 23ms | 最近 120 帧 2.5%（p95 14.9ms，p99 17.1ms） |
| 受控 9s 长文本流式输出 | 252 | 13（5.16%） | 23ms | 65ms | 最近 120 帧 3.33%（p95 12.4ms） |
| 模型/思考菜单开合（专项） | 333 | 16（4.80%） | 15ms | 40ms | 最近 120 帧 4.17%（p95 9.4ms） |

### 4.3 结论

- **周期性“一秒一卡”已消除**：最终版本静置 12 秒 `Total frames rendered = 0`，没有轮询触发的重绘或掉帧。
- **列表滚动达到 60fps**：服务商列表最近 120 帧 0% 超过 16.7ms，p99 9.5ms；历史长对话 p95 14.9ms。
- **流式输出达到 60fps 主体**：最近 120 帧 p95 12.4ms，96.7% 帧在 16.7ms 内；残余 2 帧约 60ms 为“流式卡 -> 最终 Markdown 卡”一次性切换。
- **抽屉、能力弹窗、列表滚动无明显滞涩**；`Janky frames` 数值偏高是 144Hz 判定口径所致。
- **模型/思考菜单专项**：原 `DropdownMenu` 为 483 帧 / 7.66% Janky / p95 48ms / p99 101ms；改为同窗口覆盖式内联菜单后为 333 帧 / 4.80% Janky / p95 15ms / p99 40ms，Missed Vsync 6→1。Popup Window 已彻底移除，首帧尖峰由约 100ms 降到约 40ms；剩余 1–2 帧为菜单卡片首次组合/销毁成本，不再是窗口创建尖峰。

## 5. 复现与证据

```powershell
adb shell dumpsys gfxinfo com.axgent.agent reset
# 执行操作 ...
adb shell dumpsys gfxinfo com.axgent.agent
adb shell dumpsys gfxinfo com.axgent.agent framestats
```

逐帧数据（`framestats` 只保留最近 120 帧）位于：

- `runs/probe/perf-baseline-gfxinfo.txt`
- `runs/probe/perf-baseline-providerlist-gfxinfo.txt`
- `runs/probe/perf-final9-interaction.txt` / `perf-final9-interaction-framestats.txt`
- `runs/probe/perf-final9-providerlist.txt` / `perf-final9-providerlist-framestats.txt`
- `runs/probe/perf-final4-chathistory.txt` / `perf-final4-chathistory-framestats.txt`
- `runs/probe/perf-final8-mockstream.txt` / `perf-final8-mockstream-framestats.txt`
- `runs/probe/perf-final8-idle.txt`
- `runs/probe/perf-inline-final-menu.txt` / `perf-inline-final-menu-framestats.txt`（内联菜单专项）
- `runs/probe/perf-inline-interaction.txt` / `perf-inline-interaction-framestats.txt`（最终综合交互）

> 流式压测使用本地 mock SSE 服务（`runs/probe/mock_perf_llm.py`，仅测试期间启用，已停止并移除 adb reverse）。
> 测试结束后已恢复真实 DeepSeek/GLM 配置与无障碍服务。
