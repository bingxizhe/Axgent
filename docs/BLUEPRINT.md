# Phone Agent 项目蓝图 v1.0

> **文档状态（2026-11-11 核对）**：本文件是 2026-10-30 的设计基线，用于保留当时的决策与取舍，
> **不是当前实现的规格**。当前行为以代码、[README](../README.md)、[TOOLS](TOOLS.md)、[UI](UI.md)、[VD](VD.md) 为准。
>
> 与基线的主要差异：
> - 交付形态：已从“AutoX 宿主内跑脚本”变为**单一产品 APK**（`com.axgent.agent`，Compose UI + 内置引擎 + 自动部署脚本），不再使用 release 版的 `RunIntentActivity`。
> - 执行引擎：`.js` 走 **Rhino（ES5）**；AutoX 源码里 Node/Javet 引擎只用于 `.mjs`，本项目未使用，2026-11-11 已移除引擎注册与原生库。
> - 设备基线：实测机为 **Android 16 / API 36**（文中 D4 写的 14/15 已过时）。
> - 视觉额度：D11 的“每任务 ≤ 6 次”已取消，改为 4s 最小间隔 + 429 冷却。
> - 并行：新增 `spawn_task` 多虚拟屏并行；控件树默认走无障碍直连（多线程无锁），ToolMain 串行降级为回退路径。
> - UI：2026-11 按 DSH「雾蓝玻璃」重构；页面改为显式返回栈；模型入口收敛到标题栏。
> - 工具面：清单已扩充到 58 个候选（虚拟屏模式裁剪为 42 个），以 TOOLS.md 为准。
>
> 执行引擎：**AutoX.js v7（Rhino ES5）**，源码已在 `android`（v7.2.4，commit `2d08665f`）。
> 本文件是设计基线；带 ⚠ 的条目是**尚未真机验证**的假设，M0 用探测脚本逐条证实或推翻。

---

## 0. 已确认决策

| # | 决策 | 结论 |
| --- | --- | --- |
| D0 | 引擎 | **Rhino（v6 API）**——实测这台机器上 `.js` 走 Rhino，Node / `Autox` 均不可用；“v7 包名”≠ Node 引擎 |
| D1 | 交付 | **单一产品 APK**（`agent` flavor + `prod` buildType，`assembleAgentProd`）；正式 release 签名按用户要求暂缓 |
| D2 | 模型 | **OpenAI 兼容云 API**，baseUrl + model 可配 |
| D3 | 感知 | **无障碍树优先 + 截图兜底**，视觉按需 |
| D4 | 设备 | **已 Root**，Android 14/15 |
| D5 | 交互 | **两套执行模式**：前台征用（无悬浮窗，双击音量下急停）/ 后台虚拟屏（不占用前台）|
| D6 | 能力面 | **AutoX 全能力开放**（分层 T1→T3 + 逃生舱），不做"只给几个按钮"的阉割版 |
| D7 | 验收 | 信息查询记录 / App 内填表 / 消息整理 / 文件批量整理 四类 |
| D8 | 后台模式 | **M5**；M3 插 1~2 天虚拟屏 spike 探路，期间用自由窗口过渡 |
| D9 | AutoX 源码 | **可以改**：本机 Android Studio + JDK17 + SDK34 已就绪 → 后台模式走 P1 |
| D10 | 模型 | **自建 / 其他 OpenAI 兼容端点**：baseUrl + model 由你填，框架不预设厂商 |
| D11 | 视觉 | **OCR + 找图优先**；`look` 视觉模型优先、主模型兜底；两个来源分别由 `mainVision`/`visionModelVision` 授权；独立视觉模型当前 `glm-4.6v-flash`（独立 baseUrl/key）；每任务 ≤ 6 次 |

---

## 1. 定位与边界

### 1.1 一句话

**让它像一个熟练的手机用户**：看得懂界面、点得准控件、写得进文件、卡住了会换招、要命的事会问人。

### 1.2 能力目标：AutoX 全能力映射

原则：**AutoX 能做的，Agent 最终都能做**，但按「风险 × 稳定性」分三层开放，不是一把全给。

| AutoX 模块 | 对 Agent 暴露为 | 层 | 说明 |
| --- | --- | --- | --- |
| `accessibility` / `automator` | `gui.*` | T1 | 点击、长按、滑动、多指手势、滚动、输入、返回/主页/多任务、通知栏/快捷设置 |
| 选择器 `UiSelector` | `gui.find` / 树查询 | T1 | 按 text/desc/id/class/package 定位，支持多窗口 |
| `files` / Node `fs` | `file.*` | T1 | 读、写、精确编辑、追加、新建、列目录、搜索、移动、复制、删除、建目录 |
| 截图（无障碍 / 媒体投影 / screencap） | `shot` | T1 | 三通道，见 §5.4 |
| `paddleocr` | `ocr` | T2 | 本地 OCR，返回文本 + 框 + 置信度 |
| `images`（模板/找色） | `vision.findImage` / `findColor` | T2 | 便宜稳定的兜底定位 |
| `http` / `axios` | `net.get/post` | T2 | 抓接口、下载文件、调模型 |
| `app` | `app.launch/list/current/stop/info` | T2 | 启动、枚举、查包信息、强停 |
| `device` / `system` | `device.*` | T2 | 机型、版本、电量、分辨率、亮度、音量、锁屏、亮屏 |
| `clipboard` | `clip.get/set` | T2 | 输入法失败时的粘贴回退 |
| 通知监听 | `notify.list/watch` | T2 | 读通知、按包过滤、等待指定通知出现 |
| `media` | `media.*` | T3 | 播放控制、扫描媒体库 |
| `sensors` | `sensor.*` | T3 | 加速度/光线/方向（默认关） |
| `shell` / `shizuku` | `system.shell` | T3🔴 | 白名单命令；Root 通道 |
| `java`（反射桥） | `system.java` | T3🔴 | 逃生舱：AutoX 没封装的系统 API，需显式开启 + 每次确认 |
| `ui` / `floaty` / `vue-ui` | **不暴露** | – | 只给框架自己用，防止 Agent 改自己的界面/把自己弄崩 |
| `threads` / `timers` / `engines` | **不暴露** | – | 内部调度；禁止 Agent 起并发动作（无障碍是全局单例） |

> 交付物里会有一份 `docs/TOOLS.md`：上表逐项落成 **JSON Schema + 调用示例 + 风险等级**，M1 完成初版。

### 1.3 明确不做

- 不做云端控制台 / 多设备集群（M5 之后再说）
- 不绕过验证码、不做支付、不批量注册账号
- 不逆向第三方 App 私有协议，一切通过"像人一样操作界面"
- 不把 Root 当唯一依赖：关键路径必须在无 Root 时也能跑通（Root 只做加速与降级）

---

## 2. 关键约束（源码核对结论）

1. **两套引擎并存**：`common` flavor = Rhino（ES5 风格）；`v7` flavor = Node.js（Javet V8）。本项目锚定 v7。
2. **v7 的 API 是"原生对象 + 内置包"**：`Autox` 全局（`accessibility`/`app`/`ui`/`dialogs`/`floaty`/`media`/`toast`/`engines`/`java`），内置包 `accessibility`、`axios`、`vue-ui`、`lodash`。⚠ v6 兼容层（`auto`/`files`/`images` 全局）在 v7 引擎里是否注入，M0 实测确认；不确定的一律走 `compat`。
3. **v7 的悬浮窗很底层**：`JsFloaty` 只有 `createFloaty(context, view, params)`，没有 v6 的 `floaty.rawWindow({...})`。面板要么用 `java` 桥手搭 Android View，要么用 `vue-ui` 渲染进 `ComposeView`。⚠ 需要先做 200 行原型验证。
4. **无障碍截图免授权**：`automator.takeScreenshot2Sync()`（API 30+，约 1 次/秒）→ 脚本层 `auto.takeScreenshot()`。媒体投影 `requestScreenCapture()` 仅作低版本/高频兜底。
5. **AutoX 自己有"防误伤"机制**：`SimpleActionAutomator` 在 `isRunningPackageSelf` 时会拒绝执行；`AccessibilityConfig` 有包名白名单。这两处正好可以用来保证"Agent 不操作自己的悬浮窗"。
6. **MediaProjection 基建已具备**：清单里有 `FOREGROUND_SERVICE_MEDIA_PROJECTION` 与 `CaptureForegroundService(mediaProjection)`。但**脚本层没有创建虚拟屏的 API** → 后台模式要么改 AutoX 源码，要么走 Root 自建服务（见 §4.2）。
7. **无障碍自动化是全局单例**：同一时刻只能有一个动作；Agent 循环必须串行，UI 线程与执行线程隔离。
8. **后台存活**：AutoX 被清理 = 任务中断。需要前台服务 + 电池白名单 + 断点续跑。

---

## 3. 总体架构

```
┌────────────────────────── 交互层 ──────────────────────────┐
│ 前台：悬浮球/面板（隔离窗口）   后台：状态页 + 常驻通知       │
│ 输入任务 · 看步骤 · 停止 · 失败后发“继续” · 危险确认 │
└───────────────────────────┬───────────────────────────────┘
                            │ 任务 / 控制信号（事件通道，非坐标）
┌───────────────────────────▼───────────────────────────────┐
│                      Agent 核心（与屏幕无关）                │
│  Context 组装 → LLM 决策 → Guard → Tool 执行 → 校验 → 记录  │
│  计划/重规划 · 熔断 · 预算 · 断点续跑                        │
└──────┬──────────────────┬────────────────────┬────────────┘
       │                  │                    │
┌──────▼─────┐    ┌───────▼────────┐    ┌──────▼──────────┐
│  Memory    │    │   Perception   │    │     Tools       │
│ 步骤日志    │    │ tree/shot/ocr  │    │ gui/file/app/…  │
│ 笔记/经验   │    │ idle/occlusion │    │ 注册表 + 风险分级 │
└────────────┘    └───────┬────────┘    └──────┬──────────┘
                          │                    │
                 ┌────────▼────────────────────▼─────────┐
                 │        Session（会话抽象层）            │
                 │  ForegroundSession / VirtualDisplaySession │
                 └────────┬──────────────────────┬─────────┘
                          │                      │
                 ┌────────▼────────┐   ┌─────────▼──────────┐
                 │ AutoX 运行时     │   │ Root shell 通道     │
                 │ a11y / 截图 / 文件│   │ input / am / screencap│
                 └─────────────────┘   └────────────────────┘
```

### 3.1 核心抽象：Session

Agent 不直接操作"屏幕"，只操作会话。这样前台/后台两套执行方式共用同一套循环、同一套工具定义。

```js
// 形状约定，字段以实现为准
Session = {
  id, displayId, kind: 'foreground' | 'virtual',

  observe():  { pkg, activity, tree, occluded, ime, focus, hash },
  shot():     { image, w, h, scale },        // 按需，不是每步
  inject(a):  boolean,                        // tap/swipe/setText/back/...
  launch(t):  boolean,                        // 包名 | intent
  idle(ms):   boolean,
  onControl(fn)                               // 暂停/急停/接管
}
```

| | ForegroundSession | VirtualDisplaySession |
| --- | --- | --- |
| displayId | 0（默认屏） | ≥ 1（虚拟屏） |
| 感知 | 无障碍树（主）+ 截图 | 截图（唯一）+ OCR / 视觉 |
| 注入 | `automator` 手势 | Root `input -d N` / 自建注入服务 |
| 用户可见 | 是（屏幕被"征用"，但有隔离面板） | 否（主屏照常使用） |
| 成本 | 低（3k tokens/步） | 高（图像 ~1k tokens/帧起，CPU 也高） |

### 3.2 一次步进

| 阶段 | 动作 | 要点 |
| --- | --- | --- |
| Observe | 抓包名/Activity、树快照、遮挡区、键盘态、焦点 | 节点编号 `[i]`，模型只引用编号，不吐坐标 |
| Think | `系统提示 + 任务 + 计划 + 最近 6 步 + 当前界面` | 严格 JSON：`thought / action / expect` |
| Guard | 参数校验、路径沙箱、风险分级、会话锁 | 高危先确认，超时默认拒绝 |
| Act | 调工具执行 | 串行；执行前后各记一次 hash |
| Verify | 等稳定（连续两次 hash 一致）→ 比对 `expect` | 不符则回注“预测未命中”，模型纠偏 |
| Record | steps.jsonl、笔记、变化/重复计数 | 同动作+同界面 3 次 → 警告；无变化 6 次 → 停 |

### 3.3 计划 + 反应

- **外层**：开局 3~7 步粗计划写入 `task.plan`，完成打勾；连续 2 次 `expect` 失败或界面大幅偏离 → 重规划。
- **内层**：每步仍是观察→决策→执行，允许临时插入子操作（弹窗、权限页）。

### 3.4 运行时基线（真机实测，2026-10-30）

| 项 | 实测值 | 影响 |
| --- | --- | --- |
| 设备 | OnePlus PLC110（OP60EDL1） | 单设备基线 |
| 系统 | **Android 16 / SDK 36**，安全补丁 2026-08 | 比蓝图假设的 14/15 高两个大版本 |
| 屏幕 | 1272×2800，560dpi（≈363×800dp） | 坐标/布局基准 |
| AutoX | 当时用 release 版 `org.autojs.autoxjs.v7` 7.2.3；现为内置 v7.2.4 定制分支（`com.axgent.agent`） | 已统一到同一份源码 |
| 引擎 | **Rhino**；`process` 有、`module` 无、`Packages` 有 | 全部代码按 ES5 写 |
| 可用全局 | `auto` `automator` `files` `images` `http` `device` `app` `floaty` `ui` `threads` `storages` `dialogs` `toast` `media` `sensors` `shell` `shizuku` `paddle` `selector` `colors` `keys` `events` `timers` `util` | v6 能力面基本完整 |
| 缺失全局 | `clipboard`（用 `setClip/getClip`）、`crypto`、`zip` | 用替代品 |
| 可 require | `app` `axios` `lodash` `dialogs` | 可直接用 axios 做 HTTP |
| 不可 require | `accessibility` `vue-ui` `engines` `java` `toast` `media` | v7 内置包不可用 |
| `ui` 模块 | 只有 `bindingContext`，**无 `ui.layout` / `ui.inflate`** | 面板必须用 `floaty.rawWindow(XML)` |
| `floaty` | `window` `rawWindow` `closeAll` `checkPermission` `requestPermission` | XML 字符串入口可用 |
| 截图 | `auto.takeScreenshot()` 可用；首次 1539ms，第二次 19ms，1272×2800 | 无需媒体投影授权 |
| 无障碍 | 已启用、服务已绑定；`windowRoots` 正常；**自身悬浮窗不进树** | Agent 默认“看不到”面板 |
| OCR | `paddle.ocr/ocrText/initOcr/...` 均在 | 本地 OCR 可用 |
| Root | KernelSU 已装，`/system/bin/su` 与 `/data/adb` 均存在；**`adb shell` 下无 su** | 需在 KernelSU 管理器给 AutoX 授权 |
| 电池 | 已加入白名单 | 降低后台被杀概率 |

---

## 4. 双执行模式（核心设计）

### 4.1 模式 A：前台征用（无悬浮窗）

用户把屏幕交给 Agent，Agent 在真实屏幕上操作。**运行期不创建任何悬浮球/面板/输入弹窗。**

#### 4.1.1 为什么放弃悬浮窗（2026-10-30 决策）

| 问题 | 实测证据 |
| --- | --- |
| 敏感页被系统强制隐藏 | 系统设置里 `mForceHideNonSystemOverlayWindow=true`，面板直接不可见 → 反而失去急停入口 |
| 面板会进截图 | 必须每次动作前收起、截图前隐藏，额外 ~150ms/步，还有竞态 |
| 无法自动化验证 | adb `input tap` 属不可信触摸，按钮只能用手指点，迭代慢 |

结论：投入产出不划算，改为**运行时零界面** + 系统按键急停。

#### 4.1.2 任务从哪来、进度去哪看

| 环节 | 方案 |
| --- | --- |
| 任务输入 | ① 启动 intent 的 extra：`am start ... -e task "..."`；② `/sdcard/Download/phone-agent/task.txt` |
| 进度 | AutoX 控制台、`runs/<ts>/main.log`、`steps.jsonl`；toast 仅在启动/结束/首次按键时出现 |
| 启动/结束 | 各一条 toast，不创建窗口 |

#### 4.1.3 急停：双击音量下

| 项 | 做法 |
| --- | --- |
| 监听 | `events.observeKey()` + `events.onKeyDown('volume_down', fn)` |
| 判定 | 800ms 内两次短按 → `agent.stop()`；一次短按给 toast 提示 |
| 暂停 | 已移除（2026-10-30）：只保留双击停止；恢复用对话框「继续」 |
| 为什么不用音量上 | 目标机 `mtk-kpd` 只注册了 KEY_VOLUMEDOWN，音量上在另一输入设备 |
| 拦截 | 运行期 `setKeyInterceptionEnabled('volume_down', true)`，避免误调音量；`disarm` 后恢复 |
| 生效点 | 在下一个步边界生效；LLM 请求进行中无法中断，最长等一次响应 |
| 前置 | 无障碍必须在线；不在线则启动时直接拒绝运行 |

状态机：`idle → planning → acting → verifying → … → done / failed / aborted`；
`aborted` 由音量下双击触发（AutoX 自带的“停止脚本”入口保留作兜底）。

实测（2026-10-30）：任务跑到第 3 步时双击音量下，日志 `[W +5s] 音量下键双击：用户中断任务` → `[I +6s] 任务结束：用户手动停止`，`final.md` 正常落盘。

⚠ 测试坑：`adb shell input keyevent 25` 属于 **injected event**，会被无障碍按键过滤丢弃，看起来像“急停失效”；真机手指或 root `sendevent /dev/input/event1` 才有效。

### 4.2 模式 B：后台虚拟屏

目标：Agent 在虚拟显示器里操作 App，**主屏完全不受影响**，App 里实时显示"它在干什么"。

#### 4.2.1 三条技术路径（M5 前必须选一条）

| 路径 | 做法 | 优点 | 代价/风险 |
| --- | --- | --- | --- |
| **P1 改 AutoX 源码**（推荐） | 复用已有 `CaptureForegroundService(mediaProjection)`，加一个原生模块：`DisplayManager.createVirtualDisplay(name,w,h,dpi, ImageReader.surface, PUBLIC|OWN_CONTENT_ONLY)`，暴露 `create/frame/inject/launch/dispose` | 最干净；无需 Root 也能截图；与现有截图基建共用授权 | 要改 Kotlin（约 300~500 行）并自行编译 APK；Android 14 对每会话授权/FGS 有硬要求 |
| **P2 Root 自建虚拟屏服务** | root 起 `app_process`，用 `DisplayManagerGlobal` 建虚拟屏 + socket 控制 | 不动 AutoX | 相当于自己写半个 scrcpy；常驻进程、开机自启、崩溃恢复都要自己扛 |
| **P3 降级：自由窗口** | `settings put global enable_freeform_support 1` + `am start --windowingMode 5` | 马上能用；无障碍树仍然有效，成功率最高 | 不是真后台：屏幕被分割，用户仍能看到操作过程 |

**决策建议**：M3 做 P1 的 1~2 天 spike（只验证三件事：能否建虚拟屏 → `am start --display N` 能否把目标 App 拉进去 → `input -d N` 能否注入成功）；通了走 P1，不通走 P2，其间用 P3 顶着。

> 实际落地：采用 **P2 root 自建虚拟屏服务**（`/data/local/tmp/vd_server`，3070 端口），未改 AutoX 原生代码；能力与验证见 [VD.md](./VD.md)。

#### 4.2.2 虚拟屏里的技术现实

| 环节 | 方案 | 现状 |
| --- | --- | --- |
| 建屏 | `VirtualDisplay` + `ImageReader` surface | ⚠ 需验证 Android 14 上是否要求一次性授权（每次会话） |
| 启动 App | `am start --display <id>`（root/shell） | ⚠ 验证目标 App 是否接受非默认显示器（部分 App 会拒绝或降级布局） |
| 输入注入 | `input -d <id> tap/swipe/text/keyevent` | ⚠ `input` 的 `-d` 参数在 Android 14 可用性需实测；不可用则退到 `InputManager.injectInputEvent`（需 root/系统签名） |
| 感知 | `ImageReader` 取帧 → OCR / 视觉模型 | 可用，但**没有无障碍树** → 成功率与成本都劣于前台模式 |
| 用户提示 | 主 App 内实时预览虚拟屏（帧 → ImageView）+ 当前步骤/耗时/token；通知栏常驻一行摘要 | 可行，且是调试利器 |
| 屏幕参数 | 默认 `1080x2400 / 420dpi`，可配 | 与真机一致可避免布局降级 |

#### 4.2.3 后台模式的定位（重要）

因为拿不到无障碍树，后台模式**不该承诺"什么任务都能跑"**。建议明确边界：

- ✅ 适合：信息查询、固定表单填写、消息读取与整理、批量点击（界面结构稳定、OCR 足够）
- 🟡 勉强：需要精确控件语义的操作（开关、滑杆、复杂列表）
- ❌ 不适合：Canvas/游戏、强依赖控件状态的流程

框架上支持"混合模式"：后台跑主流程，遇到 🟡/❌ 时自动切前台（切之前先问用户）。

---

## 5. 感知层

### 5.1 无障碍树序列化（决定成功率与成本）

下发给模型的不是 XML，而是压缩后的列表：

```
pkg=com.android.settings act=.Settings  screen=1080x2400  ime=off  focus=[7]
occluded=[958,1800,1080,2400]            # 面板占据的禁区
[2] TextView "设置" b=[0,120,1080,220]
[4] TextView "网络和互联网" b=[60,300,900,380] clickable
[7] EditText "" b=[60,420,960,520] editable focus
[9] RecyclerView b=[0,560,1080,2200] scrollable
```

规则：
- 只保留可见、非零尺寸、有语义的节点；纯装饰节点合并或丢弃
- 类名短化（`android.widget.TextView` → `TextView`）；长文本截 24 字（编辑框/标题不截）
- 父子同文本保留父；子节点继承可点击性
- 预算内（默认 120 节点 / ≈3k tokens）：先删不可交互静态文本，再按屏幕分块
- 每步算 `treeHash`：用于稳定检测、动作前后 diff、卡死判定
- **过滤自身包名**，并把面板 bounds 写成 `occluded`

### 5.2 视觉兜底（按需）

触发：节点数 < 5 / 页面主要是 `WebView`/`SurfaceView` / 模型显式 `look` / 连续 2 次 `expect` 失败。

**优先级：PaddleOCR + OpenCV 找图找色 → 视觉模型（若开启且配置）→ 主模型（若开启且多模态）。** `capabilities.mainVision` 与 `capabilities.visionModelVision` 分别控制两个来源；两项都开时优先专用视觉模型，未配置、读图失败或限流时自动回退主模型。回退只在主模型确实支持图片时成功；当前主模型 `deepseek-flash` 是文本模型，实测回退会返回 `Unsupported image_url`，因此兜底前需确认主模型多模态。

已配专用视觉模型 **GLM-4.6V-Flash**（独立 baseUrl/key，**免费额度**）：实测约 10s。默认两项视觉权限都开启，因此 `look` 先走 GLM；若希望只用主模型，关闭「视觉模型视觉」；若希望只用 GLM，关闭「主模型视觉」。`vision.simulateNoVision` 仍可用于模拟主模型无视觉，此时只走视觉模型、不启用主模型兜底。

`vision.mode = 'every-step'`：每步先自动调用一次视觉来源（视觉模型优先、主模型兜底），把描述以【视觉】段附在观察后面；该模式下不再下发 `look` 工具，避免重复调用。实测任务「从桌面打开哔哩哔哩」：右滑一页 → 找到图标 → 点击 → 确认前台 `tv.danmaku.bili`，**6 步完成**（其中因 DeepSeek 429 限流多花了约 1 分钟）。

- 长边缩到 720 或 1080 → JPEG q60 → base64
- 叠一层"百分比网格"图例，抑制坐标幻觉
- 坐标按 `截图尺寸 / 设备尺寸` 反算并钳制
- 每任务默认上限 6 次，超限强制回树模式

### 5.3 界面稳定检测

连续轮询树 hash：连续 2 次相同且无内容变化事件 → 稳定；超时 3s 带"仍在加载"标记继续，不死等。

### 5.4 三通道降级

| | A（默认） | B | C（最后） |
| --- | --- | --- | --- |
| 感知 | 无障碍树 | `uiautomator dump`（shell） | 截图 → OCR / 视觉 |
| 动作 | `automator` | `input tap/swipe/text`（shell） | `gesture` 多指 / 坐标点击 |

每次降级在步骤日志里打标，后续统计"哪类 App 该预置哪条通道"。

### 5.5 纯视觉模式（vision-only）

控件树完全不可用时（游戏 / Canvas / Flutter），`vision.mode='vision-only'` 会：

- **不下发** `gui_tap`（没有节点编号）、`gui_launchApp`、`shot`、`look`
- 每步只给 `pkg/act` + 视觉模型输出的元素列表（label + center 设备坐标）
- 只能 `gui_tapXY` 点坐标

**关键实测（2026-10-30）**：GLM-4.6V 的 bbox 是 **0–1000 归一化坐标**，不是像素。原始返回 `[46,219,197,299]` → 换算设备中心 `(155,725)`，真实图标中心 `(156,712)`，**误差 13px**。

两个坑：
- `images.resize` 在本版本报 `INTER_1080` 常量错误；`images.scale` 对硬件位图报 CvException。**缩放不可用**，只能发原图；归一化坐标不受影响。
- GLM 免费额度对速率敏感，连续调用会返回 `429 code=1302 账户已达到速率限制`。已加 `vision.minIntervalMs=4000` 与按来源 30s 冷却；某个来源限流时先尝试另一来源，两个来源都不可用时 `look/_locate` 返回“限流冷却中”，模型可改用 OCR。
- 视觉来源状态：`visionModelUnsupported` / `mainVisionUnsupported` 记录“读不了图”或模型不存在；`visionUnsupported` 仅在两个来源都永久不可用时置位。`main.log` 记录 `视觉尝试：<来源> 模型=<model>` 与成功/失败，便于排查。

---

## 6. 决策层

### 6.1 模型适配

- 统一 OpenAI 兼容协议：`POST {baseUrl}/chat/completions`（自建端点只要兼容此协议即可接入）
- 双通道输出：**原生 tool calling**（优先）／**文本 JSON**（小模型兜底）
- 参数：`temperature 0.2`、`max_tokens 512`、超时 60s、指数退避 3 次
- 角色分层（省钱）：**Planner**（强模型，开局/重规划）＋**Actor**（便宜模型，逐步执行）＋**Vision**（按需多模态）
- 记账：每步 `usage` 累计，面板实时显示

实测（2026-10-30，`deepseek-flash`）：普通对话 579ms / 51 tokens；带工具定义 866ms / 373 tokens，返回标准 `tool_calls`（`gui_tap {"i":4}`），响应含 `reasoning_tokens` 12~16。**原生 tool calling 可直接用，不必走文本 JSON 兜底。**

### 6.2 动作协议（已实现）

用原生 tool calling，`thought` / `expect` 直接作为工具的元参数注入（不占额外一轮对话）：

```json
{
  "name": "gui_tap",
  "arguments": {
    "thought": "设置页已打开，点进关于本机",
    "expect": { "any_text": ["关于本机", "版本"] },
    "i": 4
  }
}
```

- `thought` 必填（一句话，同时写日志）
- `expect` 可选，四种查法可组合：`any_text` / `not_text` / `pkg` / `changed`
- 定位二选一：**节点编号**（首选）或 **坐标**（仅视觉/后台模式）
- 禁止模型输出原始 JS；禁止越过沙箱路径

### 6.2.1 自检与熔断（已实现，实测有效）

动作后本地核对，不额外调用模型：

| 信号 | 做法 | 回注给模型的话 |
| --- | --- | --- |
| 预测 | 用新快照比对 `expect` | 「⚠ 上一步预测未命中：界面没有出现 X」 |
| 变化 | `hashBefore !== hashAfter` | 「⚠ 界面没有变化（连续 N 步）」 |
| 重复 | 同界面 + 同工具 + 同参数 | 「⚠ 你在同一界面重复了 N 次相同动作，必须换方法」 |

熔断：`noChange >= 6` 或 `repeat >= 12` → 直接停，`final.md` 标记“卡死”。
**只有 GUI 工具参与变化核对**（写文件/记笔记本来就不改界面，否则会误报——第一版就踩了这个坑）。

实测（同一任务）：加自检前 13 步 / 75s，加自检后 **6 步 / 13s / 40.7k tokens**；熔断用例在连续 6 步无变化后正确停住（`stuck: true`）。

跑批踩出来的三个实现坑（已修）：

| 坑 | 现象 | 修法 |
| --- | --- | --- |
| `tool_calls` 未对齐 | HTTP 400 `insufficient tool messages` | 只执行 1 个动作时，assistant 消息也只保留那 1 个 `tool_call` |
| 空输出当结束 | 任务在 11-12 步突然 `(无输出)` | `max_tokens` 512→1024；空 content + 无工具调用时提醒 2 次继续，不当结束 |
| 设置页不重置 | info/form 从上次子页继续，搜索框找不到 | 跑设置类任务前 `am force-stop com.android.settings`，保证从首页开始 |

### 6.3 上下文策略（2026-11-03 更新）

**一个任务 = 一个对话；前缀优先，接近服务商窗口时压缩最老回合。**

| 块 | 策略 |
| --- | --- |
| 系统提示 + 工具 schema | 固定不变（前缀稳定才能命中 KV cache） |
| 任务 | 只在首轮出现 |
| 历史 | 请求前按 `prompt_tokens + 新增字符估算`，达到窗口 80% 时折叠最老回合、保留最近 2 回合 |
| 当前观察 | 每步一份，追加在尾部 |
| 笔记 | 模型自写关键事实，随观察一起下发 |

- 上下文窗口来自服务商 `/models`（`context_length/context_window/max_input_tokens/inputTokenLimit`），写 `llm.contextWindow`；缺失时按模型族估算。
- 压缩会改写前缀，主动接受一次 cache epoch 重置；压缩事件写 `steps.jsonl`，UI 显示压缩卡片。
- 护栏：步数 / 时长硬上限；服务商仍报上下文超限时，先压缩再重试当前步骤，无法再折叠才结束并写 `final.md`。

---

## 7. 工具目录

风险：🟢 只读 / 🟡 可写可回滚 / 🔴 不可逆或敏感（需确认）

### T1 核心（M2）

| 工具 | 参数 | 风险 |
| --- | --- | --- |
| `gui.tap` / `longPress` | `i` 或 `x,y`，`ms` | 🟡 |
| `gui.swipe` / `scroll` | 坐标或 `dir` + `i` | 🟡 |
| `gui.gesture` | 多指路径 + 时长 | 🟡 |
| `gui.setText` | `i`, `text` | 🟡 |
| `gui.key` | `back/home/recents/notifications/quickSettings/enter/…` | 🟡 |
| `gui.launchApp` | `name` 或 `pkg` | 🟢 |
| `gui.waitIdle` / `waitText` | `ms` / `text, timeout` | 🟢 |
| `gui.find` | `text/desc/id/class/pkg`, `index` | 🟢 |
| `shot` | `save?` | 🟢 |
| `file.read/write/edit/create/append/mkdir/list/search/move/copy/remove` | 见 §8 | 🟡/🔴 |
| `task.plan/note/ask/done` | – | – |

### T2 扩展（M3）

`ocr`、`vision.findImage`、`vision.findColor`、`clip.get/set`、`app.list/current/info/stop`、`device.info`、`device.setBrightness/Volume`、`device.wake/lock`、`notify.list/watch`、`net.get/post`、`ui.dock`（挪面板）、`session.switch`

### T3 全量 / 逃生舱（M4）

| 工具 | 说明 | 风险 |
| --- | --- | --- |
| `system.shell` | 已实现为 `root_shell`：任意命令（不再白名单），默认关闭，每次执行实时读取 `ui-config.json` 做硬拦截；30s 超时，写 `root-audit.jsonl` | 🔴 |
| `system.install/uninstall` | 强制二次确认 | 🔴 |
| `system.setting` | 读写系统设置（关动画、免打扰） | 🔴 |
| `system.java` | 反射调用任意 Android API（AutoX 未封装的） | 🔴 |
| `media.*` / `sensor.*` | 播放控制、传感器（默认关） | 🟢 |

`system.shell` 与 `system.java` 默认关闭；开启需在配置里显式打开（`root_shell` 在 UI 开关处二次确认），且每次调用写审计日志（命令、耗时、退出码、输出前 500 字符）。`root_shell` 每次执行前实时重读 `ui-config.json`，关闭开关后立即硬拦截；单条命令 30s 超时（`code=124`）。

---

## 8. 文件能力

沙箱默认：`/sdcard/Download/phone-agent/**`、`/sdcard/Documents/**`、脚本自身目录。

| 操作 | 行为 |
| --- | --- |
| 读 | 单次 ≤200KB；二进制拒绝；返回行号便于模型定位 |
| 写 | 沙箱内直接写；沙箱外需确认；写前自动备份到 `runs/<id>/.backup/` |
| 编辑 | `old` 必须唯一命中（否则报错让模型重写），支持 `all` |
| 删除 | 先移到 `runs/.trash/<ts>/`，30 天后才真删；沙箱根目录直接拒绝 |
| 搜索 | 文件名 glob + 内容正则，返回 `path:line:text`，上限 200 条 |

---

## 9. 记忆与状态

| 层 | 存放 | 内容 |
| --- | --- | --- |
| 步内 | 内存 | 当前观察、最近动作、expect |
| 任务内 | `runs/<id>/steps.jsonl` | 每步全量 trace，可回放 |
| 任务笔记 | `runs/<id>/notes.md` | 模型自写关键事实（版本号、账号、路径） |
| 跨任务 | `memory/` 目录 | 应用别名（"微信"→`com.tencent.mm`）、常用路径、失败模式、用户偏好 |
| 检查点 | `runs/<id>/state.json` | 步号、计划、笔记；崩溃可续跑 |

---

## 10. UI 与交互

### 10.1 前台运行（无界面）

| 通道 | 内容 |
| --- | --- |
| AutoX 控制台 | 每步一行：工具、结果、预测命中/未命中 |
| `runs/<ts>/main.log` | 同上，可 `adb cat` 远程查看 |
| `runs/<ts>/steps.jsonl` | 结构化轨迹，回放与调 prompt 用 |
| toast | 仅启动、结束、第一次按音量下时各一条 |

任务输入：`task.txt` 或启动 intent 的 `task` extra。

### 10.2 后台状态

- App 内：虚拟屏实时预览 + 步骤流（"正在：打开设置 → 关于手机"）+ 耗时/token + 停止
- 通知栏：常驻一条，每步更新（节流 ≥1.5s），点开进状态页
- 粒度要求：不仅"运行中"，要能看出**正在哪个 App、做什么动作、第几步**
- 上下文使用率：`steps.jsonl` 每步写 `promptTokens/contextWindow/model`（无工具调用的收尾单写 `kind:"usage"` 记录）；AgentStore 取本轮最后一条写入 FINAL `ChatMessage`，最终回答卡片右下角渲染圆环 + 百分比。百分比 = `prompt_tokens / contextWindow`，按轮次重新计算。
- 流体云（ColorOS 16 / Android 16）：前台服务通知使用 Android 16 Live Updates 提升（`POST_PROMOTED_NOTIFICATIONS` + ongoing + `android.requestPromotedOngoing` + `setShortCriticalText`）。`AgentChatActivity.onStart/onStop` 维护 `appVisible`，仅后台时提升；胶囊标题使用通知 `contentTitle`，取 `steps.jsonl.actionLabel`（`打开应用/点击关于本机/执行命令…`，≤7 字）而非步数。所有结束状态保留 10 秒再停止服务并移除通知；暂停 10 秒后取消提升但保留普通通知，恢复后重新提升。设置页「流体云实时活动」入口跳系统通知页，未开启时首次启动提示一次；ColorOS 侧需开启每应用「流体云显示实时活动」，默认关闭时自动退回普通通知。

### 10.3 急停

**双击音量下键** → `agent.stop()`，在下一个步边界生效（LLM 请求进行中时最长等一次响应）。
AutoX 自带的「停止脚本」入口保留作兜底。

### 10.4 停止与继续（2026-10-30 调整）

| 能力 | 触发 | 行为 |
| --- | --- | --- |
| 停止 | 双击音量下 / UI「停止」 / 控制台 stop | `agent.stop()`，下一步边界生效 |
| 失败 | 连续 6 步无变化 / 同动作 12 次 | 直接停止（`stuckAction=stop`） |
| 继续 | 对话框发送「继续」 | 同一对话内接着做，历史上下文保留 |

暂停与续跑按钮已移除；不再维护 pause/resume 手势。检查点仍每步写 `runs/<id>/checkpoint.json`，供排查使用。

---

## 11. 安全与护栏

1. **路径沙箱** + 删除进回收站
2. **危险动作**：M0 无 UI 确认，策略为“默认拒绝 + 日志说明”；沙箱外写入/删除直接拒绝，需要时在 `config/local.js` 里显式放开
3. **循环熔断**：同动作+同界面 3 次 → 换策略；同界面 6 步无进展 → 请人接管
4. **凭据**：Key 只存 `config/local.js`（gitignore）或 `storages`；日志脱敏；不上传、不回显
5. **隐私**：截图/树快照默认只存本地；上传视觉模型前可对密码框、通知栏打码
6. **Root 最小化**：命令前缀白名单；禁止 `rm -rf`、`dd`、`mkfs`、系统分区写；`pm uninstall`/`settings put` 需确认
7. **费用**：tokens 记在 `runs/<ts>/final.md`；不设截断（依赖 KV cache），只留步数/时长上限
8. **自我操作防护**：序列化时过滤自身包名，避免 Agent 操作 AutoX 自己的界面

---

## 12. 目录结构

```
phone-agent/
├─ docs/
│  ├─ BLUEPRINT.md              # 本文件
│  ├─ TOOLS.md                  # 工具 schema（M1）
│  └─ VERIFY.md                 # ⚠ 待验证清单与实测结果（M0）
├─ src/
│  ├─ main.js                   # 入口：自检 → 配置 → 拉起 UI → Agent
│  ├─ config/{default.js, local.js}
│  ├─ core/
│  │  ├─ agent.js               # 主循环、步控、熔断、重规划、续跑
│  │  ├─ context.js             # 上下文拼装与压缩
│  │  ├─ llm.js                 # OpenAI 兼容 + tool calling / 文本 JSON
│  │  ├─ prompt.js              # 系统提示与规则
│  │  ├─ memory.js              # run 日志、笔记、跨任务经验
│  │  ├─ guard.js               # 校验、沙箱、风险分级
│  │  └─ logger.js              # 分级日志、trace、脱敏
│  ├─ session/
│  │  ├─ index.js               # 会话创建/切换/生命周期
│  │  ├─ foreground.js          # 默认屏 + a11y + 面板隔离
│  │  └─ virtual.js             # 虚拟屏：截图 + shell 注入（M5）
│  ├─ perception/{index,a11y,vision,ocr,idle}.js
│  ├─ tools/{index,gui,file,app,device,notify,net,task,shell,java}.js
│  ├─ ui/
│  │  ├─ floaty.js              # 悬浮球/面板 + 拖动 + 遮挡规避
│  │  ├─ panel.xml 或 vue 组件
│  │  ├─ confirm.js             # 危险确认
│  │  └─ wizard.js              # 首次权限向导
│  ├─ utils/{text,image,base64,retry,time,assert}.js
│  └─ compat/{index,v7,v6,caps}.js   # rt 统一接口 + 能力探测
├─ scripts/                     # PC 端：setup-adb / sync / run / pull-logs / test-local
├─ tests/                       # 手机内断言脚本
├─ android/                # AutoX 源码（只读参考，gitignore）
├─ project.json
└─ README.md
```

---

## 13. 运行产物

`/sdcard/Download/phone-agent/runs/<yyyyMMdd-HHmmss>/`

| 文件 | 内容 |
| --- | --- |
| `task.md` | 原始任务、起止时间、结论 |
| `steps.jsonl` | 每步：`{step, hash, thought, action, result, verify, channel, tokens, ms}` |
| `prompt.log` | 实际下发的上下文（开关控制，调 prompt 用） |
| `shots/` | 视觉模式截图（保留最近 20 张） |
| `notes.md` / `final.md` | 任务笔记 / 最终产出 |
| `state.json` | 检查点，崩溃续跑 |

---

## 14. 里程碑

| 阶段 | 目标 | 交付 | 验收 |
| --- | --- | --- | --- |
| **M0 骨架** | 跑起来、看得见、停得下 | 入口、权限自检、日志、`capability.probe()`、LLM 连通、急停 | ✅ 探针、tool calling、GUI 任务均实测通过；悬浮窗方案同日废弃，改音量键急停 |
| **M1 文件闭环** | 不碰 GUI 先验证循环 | `file.*`、Agent 循环、`task.done`、`TOOLS.md` 初版 | ✅ 2 步完成「读屏幕文本 → 写 notes.md」 |
| **M2 前台 GUI** | 核心价值 | 树序列化、`gui.*`、`ocr`、expect 校验、熔断、面板遮挡规避 | ✅ 2026-10-30：设置→关于本机→写文件 **6 步**完成；expect 自检与熔断已实测 |
| **M3 稳定性** | 真实场景不崩 | 视觉兜底、卡死恢复、人工接管、断点续跑、T2 工具、多任务并行排队 | 🟡 文件/OCR/通知/视觉模型已就绪；暂停/继续/终止已实测；UI 卡顿治理完成（见 [PERFORMANCE.md](./PERFORMANCE.md)）；人工接管、断点续跑、四类跑批仍待回归 |
| **M4 产品化** | 好用 | T3 工具、技能库（高频流程固化 + 任务成功后自动沉淀）、任务模板、打包 APK、成本报表 | 🟡 root_shell（实时硬拦截/30s 超时/审计）、技能库与自动沉淀、单一产品 APK 已完成；任务模板、成本报表待做；正式 release 签名按用户要求暂缓 |
| **M5 后台虚拟屏** | 不占前台 | P1 spike → 虚拟屏会话 + 状态页实时预览 + 通知进度 | 🟡 root `vd_server` 真 VD 已落地，暂停/预览/重启恢复已实测；LSPosed hook `0.8.11` 重装后待 Vector 启用+重启做最终回归 |

### 14.1 产品构建（P2，2026-11-02）

- 命令：`.\gradlew.bat assembleAgentProd`；产物 `android/app/build/outputs/apk/agent/prod/app-agent-arm64-v8a-prod.apk`。
- `agent` flavor 提供 Manifest overlay：label/图标为 Phone Agent，仅 `AgentChatActivity` 为 LAUNCHABLE；`template.apk` 生成链（`:inrt`）已移除。
- `prod` buildType 必须 `isDebuggable=true`：非 debuggable 时 AutoX 运行时进程会静默退出（实测）；同时关闭 minify/shrink。
- 包名保持 `com.axgent.agent`，签名沿用 debug keystore，保证 KernelSU root 授权与覆盖安装不丢失；正式 release 签名按用户要求暂缓。
- 输出为单一完整应用，不包含嵌套 APK；编辑器前端包（`codeeditor/dist.zip`）与打包产品线（`:apkbuilder`）已于 2026-11-11 移除。

---

## 15. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| v7 中 v6 兼容层缺失 ⚠ | 大量 v6 API 用不了 | M0 先探测；不足的部分用原生对象/`java` 桥自己封 |
| v7 悬浮窗太底层 ⚠ | 面板做不出来或体验差 | M0 做 200 行原型（java 桥 / vue-ui 两条路各试一次） |
| 面板遮挡与注入竞态 | 点错、漏点 | 禁区分注入 + 先挪面板 + NOT_TOUCHABLE 兜底 + 冲突日志 |
| 虚拟屏拿不到无障碍树 | 后台模式成功率低 | 后台限定任务类型；OCR 优先；必要时切前台并告知 |
| `input -d` 在 Android 14 不可用 ⚠ | 注入失败 | 退 `InputManager`（需 root/系统签名）；或改 AutoX 加原生注入 |
| MediaProjection 每次授权 | 后台模式频繁弹窗 | 复用 AutoX `CaptureForegroundService`；缓存 token；实在不行用 Root 建屏 |
| 后台被杀 | 任务中断 | 前台服务 + 电池白名单 + 自启 + 检查点续跑 |
| 模型坐标幻觉 | 乱点 | 强制节点编号；坐标仅视觉/后台模式，且必须 bounds 校验 |
| 输入法导致 setText 失败 | 填不进 | 剪贴板 + 粘贴回退 |
| token 爆炸 | 慢且贵 | 树压缩、历史摘要、按需视觉、分层模型、预算硬停 |

---

## 16. 待验证清单（M0 逐条打勾，写入 `docs/VERIFY.md`）

| # | 假设 | 验证方式 |
| --- | --- | --- |
| V1 | v7 引擎里能 `require('./x')` 与 `import 'accessibility'` | 最小脚本跑通 |
| V2 | v7 里 `auto` / `files` / `images` 全局是否存在 | `typeof` 打印 |
| V3 | `auto.takeScreenshot()` 在 Android 14 可用、频次限制 | 连续 5 次采样，计时 |
| V4 | `floaty.createFloaty` + java 桥能做出面板 | 原型 |
| V5 | `vue-ui` 能否渲染到悬浮窗 | 原型 |
| V6 | 面板不出现在 `windowRoots()` / 截图里 | 打开面板后 dump 树 + 截图对比 |
| V7 | `select().packageNameMatches()` 能绕开 topmost 自绘窗 | 面板展开时读目标 App 节点 |
| V8 | `input -d <displayId>` 在 Android 14 可用 | root shell |
| V9 | `am start --display` 可把目标 App 拉进虚拟屏 | root shell |
| V10 | MediaProjection 建 `VirtualDisplay` + ImageReader 取帧 | 原生 spike（M5） |
| V11 | PaddleOCR 在目标机型的速度/准确率 | 100 张截图基准 |
| V12 | AutoX 自身包名白名单可配置且生效 | 配置后点面板应无效 |

---

## 17. 待你补充（不阻塞 M0）

1. **模型端点**：`baseUrl`、`model` 名、视觉模型名；Key 自己填进 `config/local.js`，我不代填、不记录。
2. **机型 / 分辨率**：用于设定虚拟屏默认参数与坐标基准。
3. **面板形态**：小球 + 展开面板（推荐）／常驻细条／两者都要。

M0 第一步（能力探针）不依赖以上任何一项，可先跑。
