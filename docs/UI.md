# Axgent UI 设计

> 宿主：`android` 的 app 模块新增 Activity，原生 Compose + Material3。
> 形态：**对话式主界面**，视觉体系在 2026-11 按 DSH Go「雾蓝玻璃 / Mist Glass」重构。
> Agent 内核仍是 `src/` 下的 JS 脚本，UI 只通过文件与它通信。

---

## 0. v2 设计体系（2026-11）

参考 `dsh-Go/app/lib/ui/theme`，在 Compose 中重建，不引入 Flutter。

### 0.1 色彩

- 浅色：晨雾蓝纸面渐变 `#EBF1FE → #F6F7FC`；深色：深海石板 `#1D222C → #1B1C23`。
- 六套强调色：雾蓝 `#4B67B4`、黛青 `#236185`、暮紫 `#726291`、松石 `#34766F`、琥珀 `#8D6636`、石墨 `#2C2E32`；另支持自定义色与跟随壁纸取色。
- 中性色按强调色的 OKLCH 色相/彩度整体重染，明度不变，因此六套配色的文字对比度一致。
- 比例：70% 中性面、25% 文字与细线、≤5% 强调色；强调色只出现在主按钮、发送键、选中态、链接与进度。

### 0.2 字体与形状

- 系统无衬线；字阶 display 28/1.25 w300、标题 22/18 w600、正文 16/1.65、说明 12/1.5、标签 11–14。
- 聊天正文字号可调 0.85–1.3×（设置 → 外观）。
- 圆角五档 6/10/14/20/28 + 胶囊；间距 2/4/8/12/16/20/24/32；页面留白 16，阅读列最大 720dp。

### 0.3 表面与动效

- 背景：竖向渐变 + 右上主色光晕 + 左下邻近色光晕。
- 玻璃：半透明填充 + 顶边高光 + 0.5dp 发丝描边 + 柔和外阴影；分 chrome / sheet / card / pill 四级。
- 动效：按压 90ms、淡入 160ms、结构 240ms、折叠 280ms、弹层 380/260ms；展开用 `animateContentSize`，切页用 240ms 淡入 + 28dp 上滑。

### 0.4 布局与自适应

- 窄竖屏（<720dp）：抽屉 + 单列聊天，内容最大 720dp。
- 横屏 / 平板（≥720dp）：侧栏常驻，会话按日期分组；高度 <600dp 时底部导航收成一行图标。
- 输入条与会话内容居中于 720dp 阅读列；键盘弹起时输入条贴键盘上沿。
- 顶栏：菜单、会话标题（单行，仅标题）、模型胶囊（点开下拉选择模型）、新对话、能力入口。

### 0.5 v2 页面

- **聊天**：已完成轮次折叠成一行摘要（可展开）；运行中工具行逐条显示；消息下方显示步数 / token / 上下文圆环；写文件后显示「交付了 N 个文件」卡。
- **排队与插话**：运行中发送进入排队横幅，可「立即发送」转为插话或「撤回」；本轮结束后自动作为新任务发送。
- **会话中心**：搜索、按今天/昨天/本周/更早分组、切换会话、长按或管理进入批量删除。
- **设置**：AI / 能力 / 外观 / 通用四组，保留模型管理、联网搜索、运行与安全、沙箱、技能、流体云、诊断、关于；新增外观与主题页。
- **外观**：浅色/深色/跟随系统三档预览、六套配色 + 自定义、壁纸取色、聊天字号、折叠已完成轮次。
- **模型 / 技能 / 定时 / 首启**：统一使用玻璃分组卡、色块图标、胶囊按钮与同档圆角。

> 下文 v1 章节保留交互细节（工具类型、确认/提问、压缩、上下文等），这些逻辑在 v2 中继续生效。

---

## 1. 设计原则

1. **对话即任务**：用户发一条消息就是下发一个任务；Agent 的每一步以"卡片"流式回填到对话里。
2. **一眼看清状态**：顶部一个状态点 + 一行当前动作，不展开也能知道在干嘛。
3. **不挡目标 App**：运行期间本 App 退到后台，进度靠回到 App 时回放；不造悬浮窗。
4. **危险动作有刹车，运行中可插话**：运行中发送键用于补充指令，右侧独立停止键；支持暂停/继续。补充指令在下一步注入，不打断当前工具。
5. **深色优先**：跟随系统，默认深色；主色沿用 AutoX 的 M3 主题。

---

## 2. 信息架构

```
AgentChatActivity
├─ 对话（默认）
│  ├─ 空态：问候 + 4 个模板
│  ├─ 消息流：用户气泡 / 步骤卡片 / 最终回答
│  └─ 底部输入条
├─ 抽屉
│  ├─ 新建任务
│  ├─ 历史记录（runs 列表，选中后抽屉自动收起）
│  ├─ 技能（占位，后续 CRUD）
│  └─ 设置（模型/视觉/预算/关于）
└─ 历史详情（复用对话页，载入某次 run 的 steps）
```

### 2.1 对话模型

- **同一对话可包含多轮指令**，上下文默认延续；只有点右上角「新对话」才重置。
- 实现：`conversation.txt` 存当前对话 id；`conversations/<id>.jsonl` 只存用户指令与最终回答（最多 8 轮 / 按窗口预算，当前 12000 字），每次运行时作为历史消息拼到 system 后面。
- 每个 run 的 `checkpoint.json` 带 `conversationId`；UI 按 id 聚合同一对话的所有 run 展示。
- 升级迁移：没有 `conversationId` 的旧 run 归入 `c-legacy`，聊天不会突然变空。
- 历史记录入口在抽屉；右上角位置留给「新对话」。

### 2.2 上下文与计划（对标 Cherry Studio 思路）

- **计划/清单工具**：`plan_set` 设定 3-12 步计划；`todo_write` 维护清单（pending / in_progress / completed，只允许一个 in_progress）。`plan_set` 会自动用计划步骤初始化 todo，模型只列计划也不会空。
- **每步注入**：观察里始终带【计划】【任务清单】，checkpoint 保存，断点续跑恢复。
- **上下文压缩**：跨任务历史 `conversations/<id>.jsonl` 超过按窗口计算的 `maxChars`（当前 12000）时，把最老的 user+assistant 轮次折叠成 `summary.txt`（上限 4000 字）；任务内则按服务商窗口 80% 折叠最老步骤回合（保留最近 2 回合）。
- **UI**：输入框上方实时显示「计划：…」与清单（✓ 完成 / ◐ 进行中 / ○ 待办，最多 5 条 + 剩余数量）。
- **todo 强制提醒**：连续 2 步未更新清单时，下一步观察里会自动出现提醒。

---

## 3. 对话页布局

```
┌──────────────────────────────────────┐
│ ☰  Phone Agent        ● 运行中   ⋮   │  TopAppBar
├──────────────────────────────────────┤
│                                      │
│              ┌────────────────────┐  │
│              │ 打开设置，读出 Android │  │  用户气泡（右）
│              │ 版本写进 notes.md    │  │
│              └────────────────────┘  │
│                                      │
│  ● 第 1 步 · gui_tap                  │
│  ┌────────────────────────────────┐  │
│  │ 点击「设置」图标                 │  │  步骤卡片（左，可折叠）
│  │ ✓ 预测命中 · 界面已变化          │  │
│  └────────────────────────────────┘  │
│                                      │
│  ● 第 2 步 · gui_swipe                │
│  ┌────────────────────────────────┐  │
│  │ 下滑寻找「关于本机」             │  │
│  │ ✓ 预测命中                       │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ 已完成：Android 版本 16 …        │  │  最终回答（左，无气泡）
│  │ 产出 /sdcard/.../notes.md        │  │
│  └────────────────────────────────┘  │
│                                      │
├──────────────────────────────────────┤
│ 第 6 步 · gui_tap · 12s   ⏸ 暂停  ■ 停止 │  状态行
│ ┌──────────────────────────────┐  ➤  │
│ │ 描述任务，例如：整理通知…        │     │  输入条
│ └──────────────────────────────┘     │
└──────────────────────────────────────┘
```

### 3.1 组件规格

| 组件 | 规格 |
| --- | --- |
| 用户气泡 | 右对齐，`primaryContainer`，圆角 20dp，最大宽 82%，内边距 12×10 |
| 步骤卡片 | 左对齐，`surfaceContainer`，圆角 16dp，左侧 3dp 状态色条（绿=成功/红=失败/灰=跳过） |
| 步骤标题 | `● 第 N 步 · 工具名`，12sp，`onSurfaceVariant` |
| 步骤正文 | 模型为该步写的文字（`thought`）不再放进卡片，作为普通对话文本渲染在卡片上方（Markdown），卡片只保留工具动作与返回 |
| 工具返回 | 卡片展开后在底部显示「工具返回」代码块：`steps.jsonl` 的 `result` 字段（缩进 JSON，最多 1200 字，可滚动/复制） |
| 最终回答 | 无气泡，`surface` + 细边框，16sp，**Markdown 渲染**：标题/粗体/斜体/行内代码/代码块/列表/引用/表格/链接；流式阶段仅纯文本，完成后完整渲染；卡片右下角显示上下文使用率（14dp 圆环 + 百分比，低对比度右对齐），取本轮最后一次请求的 `prompt_tokens / contextWindow`，随轮次自动更新 |
| 工具卡片 | 按 tool 类型渲染，参考 Cherry Studio：调用/读取/写入/编辑/删除/移动/复制/批量/搜索/运行/计划/清单/视觉/笔记/任务/网页搜索/读取网页/截屏；图标+左侧色条，点击展开详情；失败自动展开并红框 |
| 状态行 | `第 N 步 · 工具 · 12s`（无按钮，停止用右下角 X）；空闲时隐藏 |
| 流式 | **SSE 真流式**：模型 token 增量 → `runs/<id>/stream.jsonl` → UI 增量读取并实时追加；流式卡片固定在列表下方、只展示最近 500 字，完成后由 FinalCard 完整 Markdown 渲染；不再做本地逐字动画；历史消息直出 |
| 自动跟随 | 列表新消息/最终回答出现时自动贴底；流式卡片固定在列表下方，不再随 token 增长滚动 |
| 输入条 | 圆角 28dp 的 `surfaceVariant`，多行 1~5 行，右侧圆形发送键；底部 inset 取 `ime ∪ navigationBars`（不相加），键盘弹起时紧贴键盘上沿；发送后自动收键盘，空输入 Toast 提示且不发送 |
| 发送/停止 | 空闲=纸飞机；运行=红色方块（停止） |
| 顶栏右侧 | 「新对话」：结束当前对话（运行中先 stop），生成新 id、清空聊天与上下文 |
| 抽屉 | 点击任一项后立即关闭，避免遮住内容、看不清是否点中 |
| 空态 | "今天要做什么？" + 模板 chips：信息查询 / 通知整理 / 文件整理 / 打开某 App |

### 3.2 工具卡片类型

| 类型 | 工具 | 展示 |
| --- | --- | --- |
| 调用 | `gui_*` | 节点/坐标/输入文本/按键 + 包名 + 界面变化 |
| 读取 | `file_read/list/stat` | 文件名 + 路径 + 代码块（语言/复制） |
| 写入 | `file_write/append` | 文件名 + bytes + 代码块 |
| 编辑 | `file_edit` | 路径 + diff（`-` 红 / `+` 绿） |
| 删除 | `file_remove` | 红色卡片 + 路径 + 回收站位置 |
| 移动/复制 | `file_move/copy` | from → to |
| 批量 | `file_batch` | 项数 + 逐项图标/路径 + 成功/失败统计 |
| 搜索 | `file_search` | 目录/关键词 + 结果路径 |
| 运行 | `gui_launchApp` | 包名（启动应用） |
| 计划/清单 | `plan_set/todo_write` | 步骤 / 清单 |
| 视觉 | `look/shot` | 识别结果 |
| 笔记/任务 | `task_note/done` | 摘要 |
| 通知/其他 | `notify_*` / 其余 | 参数表 |

交互：点击卡片展开/收起；失败卡片自动展开；路径和代码块都有复制按钮。

### 3.3 思考与引用

- **Thinking**：捕获模型的 `reasoning_content` → `stream.jsonl` 的 `r` 事件 → 实时卡片「思考中…」可展开；步骤日志写 `reasoning`，步骤卡片展开后显示「思考过程 · N 字」；上限 2000 字，不回灌到模型上下文。
- **Citations**：最终卡片底部显示「N 个引用文件 / N 个引用来源」；来源包括：
  - 本次 run 的 `file_*` 参数（read/list/stat/search/write/edit/remove/move/copy/batch）与最终文本中的 `/sdcard/` 路径；
  - `web_search`/`web_fetch` 结果里的 URL、最终文本中的 URL；
  - 最终回答里的来源优先进列表，其次才是工具步骤里的来源；最多 8 个。
- 文件来源：存在=蓝色 Info，缺失=红色 Delete；网页来源=蓝色搜索图标并显示域名；点击展开逐项复制路径/URL。
- 新增 `web_search`（Bing RSS）与 `web_fetch`（网页正文），目标机网络实测见 `docs/WEB.md`。

### 3.4 过程组与状态块

- **处理过程组**：连续 STEP 消息自动折叠为「处理过程 · N 步」，显示失败步数；≤3 步默认展开，>3 步默认折叠，可手动展开/收起。
- **压缩卡**：run 内回合边界压缩时写 `kind=compaction`，显示摘要字数/保留回合/epoch。
- **重试卡**：`stream.jsonl` 的 `retry` 事件在实时卡片显示「第 N 次重试：错误」；持久化事件可显示为 RetryCard。
- **截图块**：`shot` 步骤展开后用 Coil 显示 220dp 高截图，并附路径；否则回退为文本。
- **确认卡**：`run.approvalTools` 配置的工具执行前暂停，UI 显示工具名/参数与「拒绝/允许」，选择写入 `approval.txt`；默认空=全自动，示例 `['file_remove']`。
- **提问卡**：`ask_user` 工具写 `ask.json`，输入框上方显示问题 + 2-4 个选项 chip + 文本框；回答写 `answer.txt`，工具校验 id 后继续；任务停止时立即退出等待。
- **附件入口**：输入框左侧「+」打开系统文件选择器，选择后复制到 `attachments/`，输入框上方显示「图片：/附件：<名称>」chip（可移除）；发送时写 `attachment.json`，Agent 系统提示自动注入路径，图片用 `image_describe`、文档用 `doc_read`。

### 3.5 状态

| 状态 | 顶部点 | 状态行 | 发送键 |
| --- | --- | --- | --- |
| 空闲 | 灰 | 隐藏 | 发送 |
| 运行中 | 绿（呼吸） | 第 N 步 | 补充指令（发送）+ X（停止） |
| 等待确认 | 橙 | `等待确认：工具名` | X（停止） |
| 等待回答 | 蓝 | 输入框上方「Agent 需要你的回答」卡 | 选项 chip / 文本框 + X（停止） |
| 完成 | 蓝 | 隐藏，最后一条为最终回答 | 发送 |
| 失败/停止 | 红/蓝 | 显示原因（无按钮） | 发送 |

> 暂停已恢复为后台任务的显式状态（2026-10-31）：暂停只停止模型响应并保留当前界面/进程，继续时复用；终止才关闭预览并结束 Agent。  
> 停止/失败后 checkpoint 写 `ended:true`，UI 立即回到发送态，不再因 `done:false` 卡在“运行中”。

### 3.6 输入卡与模型管理

- 输入卡改成圆角卡：上方 BasicTextField（最小 48dp），下方一行左侧附件/暂停、思考强度 chip，右侧发送/停止按钮；窄屏/大字体下不换行。模型选择已移到顶部标题栏右侧（2026-11-10），输入卡不再显示模型。
- **上下文使用率**：`steps.jsonl` 的每步 `promptTokens/contextWindow` 由 AgentStore 解析，写入本轮 FINAL `ChatMessage`；`FinalCard` 右对齐渲染圆环 + 百分比（`<1%`/`100%` 边界文案），历史 run 无字段时不显示。
- **流体云**：Android 16 Live Updates（`POST_PROMOTED_NOTIFICATIONS` + ongoing 通知 + `android.requestPromotedOngoing`）在 ColorOS 16 被渲染为顶部流体云胶囊；应用退到后台后胶囊显示当前动作（如「执行命令」「点击关于本机」），任务完成后保留 10 秒再关闭。设置页新增「流体云实时活动」入口（显示已开启/未开启，点击跳系统通知页）；未开启时首次启动 Toast 提示一次。首次使用仍需在系统「Phone Agent 通知设置 → 流体云显示实时活动」开启（默认关闭，App 无法自行授权）。
- **模型选择**：显示 `chatModels`；选择后写 `activeModelId` + `llm.baseUrl/apiKey/model/protocol`；菜单底部“新模型连接”跳到模型管理。
- **思考强度**：9 挡位 `default/none/auto/minimal/low/medium/high/xhigh/max`；只写 `llm.reasoningEffort`（`auto` 归一为 `medium`），不再写 `thinkingPrompt`；主模型不再发送 `max_tokens`。
- **上下文**：窗口大小由服务商决定，不再手填。模型检测/发现时从 `/models` 读取 `context_length`、`context_window`、`max_input_tokens`、`inputTokenLimit` 等字段，写入 `ModelProfile.contextWindow` 与 `llm.contextWindow`；服务商不提供时按模型族估算并标注「估算」。Agent 默认到窗口 80% 触发压缩（Cherry Studio 默认），折叠最老回合并保留最近 2 回合。
- **模型管理**：服务商层 `ProviderProfile` 由对话/视觉模型共用；内置 67 个 Cherry 服务商预设与 2301 个模型 ID。点击「添加」进入独立「模型服务」页：服务商搜索 → 动态表单 → 输入 API Key 后自动拉取模型列表 → 勾选检测 → 自动返回模型管理并显示可用性/延迟/上下文窗口。前端不提供模型 ID 输入框。
- **协议**：支持 OpenAI Chat、OpenAI Responses、Anthropic Messages、Gemini generateContent；请求转换、SSE、工具调用/结果与图片编码统一在 `src/core/protocols.js`；模型列表拉取失败时回退内置预设目录。
- **设置**：只保留菜单入口；模型管理、联网搜索、运行与安全、沙箱目录、技能、诊断与日志、关于与外观。主题支持跟随系统/浅色/深色，立即应用并持久化。
- **运行与安全**：最大步数/最长分钟/审批工具/后台虚拟屏/后台最大虚拟屏数量，以及「崩溃/被杀后自动续跑」（默认开；每个 run 只自动尝试一次）。「后台最大虚拟屏数量」在后台虚拟屏关闭时置灰不可编辑；开启后可设 1-5，保存时 `updateRunSettings` 仍会 clamp 到 1-5；系统硬上限 6 仅允许配置文件/内部使用。
- **权限分级（2026-11-12）**：同页提供全局「无障碍 / Shizuku / Root」三个独立开关与「自动处理弹窗」开关；关闭某一级后应用与 Agent 都不得使用该层。全局无障碍/Shizuku 开启时 Agent 对应权限自动跟随开启，Root 保持默认关闭；任一全局关闭会立即把 Agent 对应权限写为 false。能力面板同步显示 Agent 三项权限，全局关闭时置灰。
- **权限分级状态**：显示无障碍 / Shizuku / Root 三项可用状态；「加入白名单」把包名写入 ColorOS `accessibility_turn_off_skip_package`（写入顺序 Shizuku→root），「请求 Shizuku 授权」调用系统授权弹窗。前台 GUI 操作在无障碍不可用时自动降级到 Shizuku/root 的 ToolMain+input+screencap 后端；后台虚拟屏仍为 root 专属。白名单不阻止首次启用/重装后的那一次 `AccessibilityGuideCloseActivity` 提示（可由自动处理弹窗开关点击「保持开启」）。
- **无障碍自动恢复**：任务开始时无障碍为开、途中掉线时，Agent 每步观察前自动调用 `a11y_ensure`，用 Shizuku（无 root 也可）重新写入无障碍服务并等待重连；12s 节流。Agent Root 关闭时不会借全局 Root 走 `su`。
- **定时任务页**：侧边菜单进入；列表显示任务、间隔、下次触发时间/已暂停；「新建」输入任务文本与间隔分钟；Switch 启停、删除；未授予精确闹钟权限时提示并在新建时跳转授权页。
- **并行任务（仅 Agent 使用）**：不再提供用户入口（侧边菜单、任务页、`startParallel` 均删除）。Agent 在后台虚拟屏开启时通过 `spawn_task/spawn_status/spawn_wait/spawn_stop` 自主创建子 Agent，每个子 Agent 独占一块副屏；上限由 `run.maxParallelDisplays` 与代码硬限制共同决定。
- **沙箱目录**：设置 → 沙箱目录可增删 roots，保存到 `ui-config.json.sandbox.roots`；下方小字提醒不要把相册、Android/data、系统目录设为沙箱，并提示移除默认运行目录的影响。路径格式校验：绝对路径、禁止 `..`/换行、至少一条；不强制阻止高风险路径。
- **技能**：设置 → 技能、侧边栏“技能”都进入同一管理页；技能库位于应用私有目录 `files/agent/skills/<folder>/SKILL.md`（外部不可直接修改），frontmatter 含 name/description；每个技能自动创建 `work/` 工作目录；可新建、编辑、删除、从目录导入、导入 ZIP、导出 ZIP、启停。启用状态存 `ui-config.json.skills.disabled`。
- **技能调用**：Agent 启动时只把已启用技能的 name/description 放进系统提示；任务匹配时调用 `skill_load(name)` 读取完整 SKILL.md，正文随后进入上下文，支持同目录资源文件。首次启动会种入 ColorOS 设置、通知、哔哩哔哩、文件约定四个技能；旧版外部技能会一次性迁移到私有目录。
- **自动沉淀**：任务成功后 `skills/distill.js` 用主模型判断流程是否可复用；值得则写入私有 `skills/<name>/SKILL.md`，对话末尾（最终回答之后）显示「已沉淀技能：name」通知。一次性查询、失败/中止、与现有技能重复时跳过；可用 `skills.autoDistill=false` 关闭。
- **首启引导**：`onboarded` 未完成时显示引导页，检查无障碍、所有文件访问、电池优化、通知；完成后写 `onboarded:true`，以后启动只在权限缺失时显示顶部横幅。权限检查在 `ON_RESUME` 刷新。
- **引擎兜底**：`launchScript` 只用本包 `EngineController`，不再回退旧 release；未创建 run 超时显示明确失败原因。
- **一键运行**：`AgentStore.launchScript` 优先用本包 `EngineController` 直接运行 `main.js`，不再依赖旧 release 的 `RunIntentActivity`；发送前检查本包无障碍服务，未开启时跳系统设置。
- **能力开关**：右上角「能力」打开 15 个开关：Agent 无障碍权限、Agent Shizuku 权限、Agent Root权限（默认关闭；全局关闭时置灰；开启时弹二次确认；关闭时 `root_shell` 在工具执行层实时硬拦截并写审计）、屏幕读取、文件读取、**主模型视觉**、**视觉模型视觉**、Web、GUI 操作、文件编辑、沙箱内高风险操作（默认开）、沙箱外文件操作（默认关）、设备与通知、剪贴板、规划与待办。
- **视觉权限细化**：`capabilities.mainVision` 控制主模型能否直接读图，`capabilities.visionModelVision` 控制专用视觉模型；两项都开时 `look`/自动看屏优先走视觉模型，视觉模型未配置、读图失败或限流时自动回退主模型；只开一项时只走对应来源；两项都关时 `look`/`shot` 与自动看屏一并关闭，OCR 仍归屏幕读取。旧配置的 `capabilities.vision` 会迁移为两项同值，保存时继续写 `vision` 供旧脚本兼容。
- **模型服务管理补充**：服务商列表支持启停开关（关闭后其模型显示「服务商已停用」且不可选中）、删除与配置编辑；编辑表单可改名/地址/Key，或「仅保存服务商配置」。进入模型管理页会自动刷新超过 10 分钟未检测的活动模型状态。
- **无障碍 root 开启**：权限缺失时先判断当前状态，未开启才通过 `su` 写入 Secure 设置；入口在权限横幅、首启引导和发送任务前，成功后会等待系统重新绑定服务。

### 3.7 后台任务与镜像预览

- **前台服务**：任务启动时 `AgentTaskService` 以前台服务常驻，通知标题显示当前动作（`打开应用/点击关于本机/执行命令/已暂停/任务已结束`），正文显示工具与任务摘要，并提供暂停/继续/终止；通知内容打开 App 后可再点顶栏「预览」。
- **流体云触发**：`AgentChatActivity.onStart/onStop` 维护前台标记；应用不在前台且系统 `canPostPromotedNotifications()` 为 true 时，每 1.5s 更新的通知请求提升为实时活动，ColorOS 流体云胶囊实时显示当前步骤动作（`steps.jsonl.actionLabel`）。所有结束状态（完成/失败）统一保留 **10 秒**再 `stopSelf` 并移除胶囊；暂停状态同样保留 10 秒后取消提升（普通通知继续保留，恢复后重新提升）。真机实测完成到移除 10.99s。
- **流体云边界**：仅 Android 16+ 且 ColorOS 侧开启「流体云显示实时活动」时生效；未开启时退回普通常驻通知，不影响任务与对话。设置页入口会随返回 App 自动刷新开关状态。手动「终止」立即收尾，不走 10 秒延迟。
- **暂停**：写 `control.txt=pause` 和 `paused.txt`；JS 端中止当前流式模型响应，在步边界等待；界面和进程保留。继续写 `resume`，下一轮重新观察后继续。
- **终止**：写 `control.txt=stop`，关闭预览悬浮窗、停止模型/工具循环并结束前台服务。
- **镜像预览**：顶栏「预览」或通知打开悬浮窗；JS 只在 `.preview.on` 存在时截图并写入 `.preview.png`/`.preview.json`。父任务虚拟屏（内部 slot 7）走 `screencap -d`，物理屏模式走本地截图；预览可随时关闭，任务继续。
- **防套娃**：截图前写 `.preview.suppress`，悬浮窗暂时隐藏，截完删除；Agent 的 `auto.takeScreenshot` 也包了同样的抑制逻辑，避免视觉模型看到预览窗口本身。
- **边界**：真 VirtualDisplay 已落地（父任务内部 slot 7，子 Agent slot 1-6，独立 Display 承载任务），不再是物理屏镜像。`vd_server` 仅保留 idle/兼容接口，父任务与子任务均走 DaemonMain + ToolMain，避免 UiAutomation 单实例冲突。LSPosed hook 模块 `0.8.11` 重装后需在 Vector 重新启用并重启；侧键 hook 已移除。
- **沙箱规则（代码写死）**：`sandbox.need(path, mode)` 逐路径校验；沙箱内读写默认允许，删除/移动/批量需 `sandboxHighRisk`；沙箱外读写/删除/列目录/搜索需 `outsideSandbox`，写/删同时需 `fileEdit`。关闭时工具对沙箱外路径直接返回错误，并在每步能力提示中告诉模型不能操作沙箱外。系统路径（runs/conversations/src 等）仍然禁止写删。
- **能力生效**：UI 写 `ui-config.json.capabilities`；JS `tools.schemas/execute` 双重过滤，屏幕读取关闭时跳过 a11y 观察，视觉/GUI/屏幕读取都关闭时不再强制无障碍；系统提示同步告知关闭项。
- **构建注意**：debug APK 需带 `autojs/src/main/assets/v6modules`、`v7modules`（`__timers__.js` 等），否则引擎报模块缺失。

### 3.8 性能（2026-11-02）

- **轮询**：运行中 200ms / 空闲 1000ms；磁盘与 `checkpoint.json` 解析在 IO 线程并包进 `Snapshot.withMutableSnapshot`，主线程只做状态应用。
- **数据**：`RunMeta` 按 `(mtime, length)` 缓存；run 目录扫描 1s 节流；`stream.jsonl` 增量读取；`messages` 增量同步，不再整表清空重加。
- **渲染**：流式卡片移出 LazyColumn、固定在列表下方；流式文本最多 5fps 发布、只显示最近 500 字；流式阶段不构建 Markdown 树；`ToolStepCard` 去掉 `IntrinsicSize.Min` 双倍测量。
- **后台**：通知进度与预览悬浮窗的文件读取/图片解码移到后台线程；权限检查移到 IO。
- **菜单**：模型/思考下拉菜单为同窗口覆盖式内联菜单（Scrim + 卡片），不再创建 Popup Window；支持点击聊天区或返回键关闭。模型菜单从顶部标题栏的模型按钮向下展开，思考菜单仍贴输入框向上弹出。开合 p95 15ms、p99 40ms，Missed Vsync 6→1。
- **实测**：静置 12 秒 0 帧重绘；服务商列表滚动最近 120 帧 0% 超过 16.7ms；历史长对话 p95 14.9ms；流式输出 p95 12.4ms。详见 [PERFORMANCE.md](./PERFORMANCE.md)。

---

## 4. 抽屉与二级页

### 4.0 页面栈与返回（2026-11-10）

- **根页面**：对话页恒为栈底；未完成引导时栈底是引导页，完成后重置为「对话」。
- **入栈规则**：页内下钻（设置 → 子页、模型管理 → 新建连接、设置 → 技能）入栈；目标与栈顶相同时不重复入栈。
- **顶部导航**：侧栏/抽屉里的「会话 / 技能 / 定时任务 / 设置」把栈重置为 `[对话, 目标]`；打开某个会话或新建对话重置为 `[对话]`，因此从会话列表进入对话后按返回直接退出，不会退回列表。
- **返回**：系统返回统一出栈（`BackHandler`），只有对话页不拦截、交给系统退出应用；抽屉打开时返回先关抽屉，弹窗/下拉菜单打开时返回先关浮层。
- **状态保持**：页面栈用 `rememberSaveable` 保存，旋转屏幕或 Activity 重建后仍停留在原页面，返回路径不变。

### 4.1 抽屉

- 头部：App 名 + 当前模型名（只读）
- 菜单：对话 / 历史记录 / 定时任务 / 技能 / 设置（并行任务入口已移除，仅 Agent 可通过工具使用）
- 底部：版本号 + "Agent 内核：main.js"

### 4.2 历史记录

- 列表项：任务标题（截断 24 字）· 状态徽标（完成/失败/卡死）· 步数 · 相对时间；最新一条标「当前」
- 点击：载入该 run 的 `steps.jsonl` 到对话页，顶部显示"历史记录 · 2026-10-30 06:36"
- 管理：右上角「管理」进入多选（点击/长按选中，支持全选、取消全选、批量删除）；确认后删除选中 run 目录，若该会话已无其他 run 引用，一并删除 `conversations/<id>.jsonl` 与 `summary.txt`
- 保护：当前 run（`currentRunId` 或最新 run）不可选、不会被删除

### 4.3 设置

| 分组 | 项 |
| --- | --- |
| 模型 | 服务商与模型管理（模型服务页：预设、添加服务商、自动拉取模型） |
| 运行 | 最大步数 / 最大时长 / 卡死策略 |
| 手势 | 双击停止窗口 / 长按暂停时长 |
| 存储 | 日志保留天数 / 清理 |

保存写到 `/sdcard/Download/phone-agent/ui-config.json`，JS 侧 `config.load()` 会合并它。
Key 与 `local.js` 同一安全级别（手机本地明文），UI 上默认打码。

### 4.4 技能

设置 → 技能或侧边栏“技能”进入 SKILL.md 管理页：列表、启停、新建/编辑/删除、从目录导入、ZIP 导入、单个技能 ZIP 导出；存储为应用私有目录 `files/agent/skills/<folder>/SKILL.md`，每个技能带 `work/` 工作目录。
后续：从成功任务生成草稿、在线市场导入。

---

## 5. 与 Agent 的通信契约

```
UI → Agent：
  task.txt      任务文本
  control.txt   stop | pause | resume（脚本每 800ms 读一次，处理后清空）
  paused.txt    暂停标记；预览/通知控制也复用
  preview.on    打开预览时创建；关闭时删除
  preview.suppress 截图时临时隐藏预览
  .preview.*     实际预览帧/元数据（隐藏文件）
  resume.txt    续跑 runId
  ui-config.json 设置覆盖

Agent → UI：
  runs/<id>/steps.jsonl     每步一行 JSON（kind=step / nudge）
  runs/<id>/main.log        人类可读日志
  runs/<id>/checkpoint.json 状态/步数/notes
  runs/<id>/final.md        最终结论
  .preview.png / .preview.json 当前镜像预览帧与步数/暂停状态
```

UI 只轮询文件，不依赖 Agent 内部实现；换引擎不改界面。

---

## 6. 实现映射（fork 内）

| 文件 | 职责 |
| --- | --- |
| `agent/AgentChatActivity.kt` | Activity + 主题 + 抽屉 + 页面切换 |
| `agent/AgentUi.kt` | 对话页、状态行、输入条、步骤卡片 |
| `agent/AgentHistory.kt` | 历史列表与详情加载 |
| `agent/AgentSettings.kt` | 设置页与 `ui-config.json` |
| `agent/AgentStore.kt` | 文件读写、轮询、状态流 |
| `agent/AgentModels.kt` | `ChatMessage / StepItem / ApprovalRequest / ModelProfile / RunSummary / UiConfig` |
| `agent/ToolCards.kt` | 类型化工具卡、ReasoningBlock、截图/批量/计划/清单、Diff |
| `agent/AgentModelScreens.kt` | 模型管理、服务商配置页、模型自动发现、协议连通测试 |
| `agent/AgentProviderPresets.kt` | 67 个内置服务商预设与协议信息 |
| `agent/AgentProviderModelCatalog.kt` | 内置模型目录加载（assets/agent/provider-models.json，2301 个模型 ID） |
| `agent/AgentSkillStore.kt` | SKILL.md 技能库、种子、解析、导入、启停存储 |
| `agent/AgentSkillScreens.kt` | 技能管理页、新建/编辑/导入/删除 |
| `agent/AgentSettingsScreens.kt` | 设置菜单、搜索/安全/沙箱/诊断/关于二级页 |
| `agent/AgentOnboarding.kt` | 首启引导、权限检查与对话页权限横幅 |
| `agent/AgentScriptInstaller.kt` | 把 APK 内置 `assets/agent-src` 部署到 `/sdcard/phone-agent/src`；`AgentTaskService.kt` 负责前台服务、通知与暂停/终止动作 |

`agent` 变体 Manifest overlay 仅保留 `AgentChatActivity` 为 LAUNCHABLE（label = Axgent），移除 AutoX 原对外 Activity 入口。

---

## 7. v1 不做

- 技能编辑与自动生成
- 聊天记录云同步 / 多设备
- 语音输入
- 后台虚拟屏预览（M5）
- 富文本 Markdown 完整渲染（先支持标题/列表/加粗/代码块）

---

## 8. 构建与已知问题

- 工程要求 **JDK 17**（`buildSrc/Version.kt` 硬编码）。本机把 JDK 放在 `tools/jdk-17.0.2`（仅本地、已 gitignore），用用户级 `~/.gradle/gradle.properties` 的 `org.gradle.java.home` 指向它；仓库内的 `gradle.properties` 保持可移植。
- **死锁根因：`org.gradle.parallel=true`**。并行 worker 在 Windows 上会卡在 `:autojs:compileReleaseAidl` 等任务（日志停住、CPU/IO 为 0）。改为 `org.gradle.parallel=false` 后构建正常：**BUILD SUCCESSFUL in 9m 8s**。（当时卡住的 `:inrt:mergeExtDexTemplateRelease` 已随 `:inrt` 模块移除。）
- 代理（可选）：`systemProp.http.proxyHost=127.0.0.1` / `7890` 已从仓库的 `gradle.properties` 移除，需要时写到用户级 `~/.gradle/gradle.properties`。
- **产品包（当前交付形态，P2）**：`agent` flavor + `prod` buildType，构建命令 `.\gradlew :app:assembleAgentProd`；产物 `app/build/outputs/apk/agent/prod/app-agent-arm64-v8a-prod.apk`（2026-11-11 收敛后约 61.9MB）。
- 包名保持 `com.axgent.agent`（沿用现有 KernelSU 授权）；签名沿用 debug keystore（SHA-256 `f572fe29…`）；**正式 release 签名按用户要求暂不执行**。
- `prod` 必须保持 `isDebuggable=true`：AutoX 运行时/引擎在非 debuggable 包内会静默退出（已实测）；同时关闭 minify/shrink。
- 产品变体仅保留 `AgentChatActivity` 一个 LAUNCHABLE，移除 Splash/Main/Edit/About/Settings/Build/Log/Shortcut/TimedTask/Tasker 等 AutoX 对外 Activity；`assets/template.apk`、`assets/codeeditor/dist.zip`、Node/ONNX/MLKit/Tesseract 原生库均已移除，APK 内不再嵌套其它 APK。
- 安装后需授权：`MANAGE_EXTERNAL_STORAGE`（用 release AutoX 的 root 执行 `appops set ... allow`）、电池白名单，否则 ColorOS 会冻结进程。
- 执行方式：UI 通过本包 `EngineController` 直接运行 `main.js`（同进程引擎），不再依赖旧 release 的 `RunIntentActivity`。
- 实测：UI 发任务 → `task.txt` → 本包引擎执行 → **3 步 / 7s** 写入 `ui-test.md` → 聊天里回放步骤与“完成”卡片。
- 流式：`llm.chatStream` 用 `HttpURLConnection` 读 SSE，增量写 `runs/<id>/stream.jsonl`；UI 的 `LiveCard` 每 120ms 追加重绘，模型出多快就显示多快。历史消息直出，重进页面/键盘收放不重播。
- 实测：`stream.jsonl` 逐条 `{"d":"..."}` 到达，截图 `live-real` 捕获到正文写到 “...modernity so▍” 的中间态。
- 历史/实时区分：首载某次运行时把已有消息标记为“已见”，`animate=false` 直出；之后新增消息才 `animate=true`；进度用 `rememberSaveable` 保存，键盘收放不重置。
- 自动跟随：底部时持续贴底；手动上滑后停止，滑回底部后恢复（截图 `follow2` / `nofollow2`）。
- 键盘适配：输入条用 `ime ∪ navigationBars` 并集 inset，Activity 设 `adjustNothing`；修复了弹起时上移过大、不贴键盘的问题（截图 `ime-fixed`）。
- 发送行为：非空发送后 `keyboard.hide()` + `clearFocus()`；空输入弹 Toast“内容为空，无法发送”，不发送、不收键盘（截图 `empty-send` / `send-hide`）。
- 多轮对话：已实测。同会话第二轮日志出现「对话历史 2 条（188 字）」，能引用第一轮创建的 `conv-a.txt` 并重命名；UI 同时展示两轮气泡/步骤/完成卡片。
- 新对话：右上角点击后聊天清空、生成新 id（`conversation.txt` 更新）；旧 run 归入 `c-legacy` 保留展示。
- 抽屉：点「历史记录」后切换到历史列表且抽屉自动收起（截图 `drawer3-history`）。
- 计划/清单：多步任务日志出现 `plan_set`，checkpoint 写入 7 步计划；底部面板显示计划与进度（截图 `plan1`）。
- 上下文压缩：`context.js` 负责历史加载、折叠与摘要注入；实测 run `203850` 触发 1 次（epoch=1，摘要 929 字，保留最近 2 回合）。
- KV cache：工具集冻结（当时 34 个，当前虚拟屏模式 42 个、前台 52 个，`toolsHash` 全程不变）、技能/计划/notes 去重、回合边界压缩；实测第 2 步起命中 **93.5%–98%**，压缩后 90.8% 再恢复。详见 [CACHE.md](./CACHE.md)。
- 压缩实测：20 轮 × 400 字 → **18160 → 2727** 字符，摘要 3638 字，`ok=true`。
- 流式卡死修复（2026-10-30）：SSE 只收到 `start`、200s 无增量，`readLine` 阻塞导致停止无效。现改为 **20s 读超时 + 60s 总时限 + 逐行检查 stop**；探针复测 213 chunks / 1.8s 正常。
- 停止/发送修复（2026-10-30）：`agent.js` 被 PowerShell 默认编码写坏（字符串未闭合）→ `main.js` 加载失败，UI 停在 RUNNING、X 无法停止。反编码恢复 + 重写 SYSTEM_PROMPT/关键标签后，mod-check 全 OK；实测 X 停止 `+8s` 生效，结果「用户手动停止」，下一条发送正常。
- 本机构建环境（非临时开关，已固化）：
  - `buildSrc/src/main/kotlin/Version.kt`：JDK 17
  - `gradle.properties`：关闭自动下载 JDK，指向 `tools/jdk-17.0.2`
  - `android/local.properties`：本机 SDK 路径（不入库，克隆后自建）
