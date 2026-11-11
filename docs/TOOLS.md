# 工具目录（M3）

风险：🟢 只读 / 🟡 可写可回滚 / 🔴 不可逆或敏感

> 数量说明：共 58 个候选工具，按运行模式与能力开关动态裁剪（`src/tools/index.js` 的 `schemas()`）。
> 实测：后台虚拟屏 + root 关闭 = **42 个**；前台模式 + root 关闭 = 52 个。
> 虚拟屏模式移除 `gui_*`、`shot`、`look`、`ocr`、`wait_text`、`app_list`、`notify_read`（统一走 `mobile`）；
> `root_shell` 只在「Agent Root权限」开启时出现，`mobile` 只在虚拟屏模式出现，`spawn_*` 只在并行可用时出现。

## 虚拟屏（mobile）

`mobile` 是虚拟屏/物理屏统一入口。单屏模式走 vd_server；并行模式带 `display_id`（或 Agent 自动注入 `state.displayId`）时直接走 root shell：`input -d`、控件树、`am start --display`。

| 参数 | 说明 |
| --- | --- |
| `action` | observe / click / swipe / set_value / type / key / wait / launch_app / list_apps / switch_mode |
| `display_id` | 并行副屏 Display；不填则用当前 Agent 绑定的槽位，再退回单屏 |

并行模式不支持 `switch_mode`；`list_apps` 走 `pm list packages`，不依赖 vd_server。

后台虚拟屏模式下，父任务使用内部槽位 7，子 Agent 使用槽位 1-6（用户可设上限 1-5，系统硬上限 6）。控件树有两个后端：**无障碍直连（默认）**——复用 AutoX 无障碍服务，按 displayId 过滤窗口建树，单进程多线程并发、无锁，实测两个子 Agent 的 dump 时间戳重叠，单次 96–545ms；**ToolMain 串行（回退）**——无障碍未开启或被系统回收时使用，UiAutomation 系统单实例，靠 `/data/local/tmp/tool_cmd.lock` 串行，单次 874–977ms。两后端输出格式一致，上层解析不变，详见 [VD.md](./VD.md)。

## 并行子任务（spawn_*）

仅当 `run.virtualDisplay=true` 且父任务成功拿到内部虚拟屏时，工具才会出现在 schema 中；`execute` 还会检查 `ctx.parallel`，子 Agent 的 `ctx.parallel` 恒为 null，因此不能继续创建子 Agent。

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `spawn_task` | `task`, `slot?`, `wait?`, `timeout_seconds?` | 🟡 | 创建子 Agent，独占一块副屏；`wait=true` 会阻塞到子任务结束并返回结果 |
| `spawn_status` | `id?` | 🟢 | 不传 id 列出全部；传 id 查看单个子任务状态/结果片段 |
| `spawn_wait` | `ids?`, `timeout_seconds?` | 🟢 | 等待指定（或全部）子任务结束并收集结果，默认 300s、最大 900s |
| `spawn_stop` | `id` | 🟡 | 停止指定子任务，不影响父任务 |

硬限制（代码执行，不依赖提示词）：

- `config.run.virtualDisplay=false` → `spawn_task` 不在 schema 中，`execute` 也会拒绝；
- 并发上限 `min(6, max(1, config.run.maxParallelDisplays))`；用户设置被 Kotlin 限制为 1-5，配置文件最高只接受 6；
- 槽位必须在 1..上限且未被运行中的子任务占用；子任务的 `parallel` 恒为 null，不能再 spawn；
- 父任务结束时 `parallelMgr.shutdown()` 停止所有子 Agent 并回收槽位 1-7。

## GUI（9，仅前台模式）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `gui_tap` | `i` | 🟡 | 点节点；失败自动退回坐标点击 |
| `gui_tapXY` | `x,y` | 🟡 | 纯坐标点击，视觉定位时用 |
| `gui_setText` | `i,text` | 🟡 | 写入输入框；失败会尝试 focus 后重写 |
| `gui_swipe` | `dir(up/down/left/right),ms` | 🟡 | 全屏滑动 |
| `gui_longPress` | `i` 或 `x,y` | 🟡 | 长按（默认 600ms） |
| `gui_key` | `key(enter/delete/space/tab/escape)` | 🟡 | 特殊按键，提交搜索/删字用 |
| `gui_back` / `gui_home` | – | 🟡 | 返回 / 回桌面 |
| `gui_launchApp` | `name` | 🟡 | 应用名或包名，内部走 launch→getPackageName→launchPackage |

> 权限分级（2026-11-11）：无障碍不可用时，`gui_*`、`shot`、`ocr`、`look` 自动降级到 Shizuku/root 后端——
> 控件树走 `/data/local/tmp/tool_cmd.sh`（ToolMain），点击/滑动/按键走 `input`，截图走 `screencap`。
> 正常路径仍优先无障碍；返回结果的 `via` 会标明 `shell input tap` / `shell set_value` 等。

## 无障碍自动恢复（a11y_ensure）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `a11y_ensure` | – | 🟡 | 任务途中无障碍掉线时，用 Shizuku 或 Root 重新写入 `enabled_accessibility_services` 并等待服务重连 |

硬限制：

- 仅当全局/Agent 无障碍开启，且 Agent Shizuku 或 Root 至少一项开启时进入 schema；
- 执行时实时校验，Agent Root 关闭时即使全局 Root 开启也不会用 `su` 回退；
- Agent 每步观察前检测到掉线会自动调用一次（12s 节流），无需模型主动调用；`a11y_ensure` 也可由模型显式调用。

## 文件（13，全部限制在沙箱内）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `file_read` | `path` | 🟢 | 最多返回 8000 字 |
| `file_write` | `path,content` | 🟡 | 覆盖写，自动建父目录 |
| `file_append` | `path,content` | 🟡 | 追加 |
| `file_edit` | `path,old,new,all?` | 🟡 | 精确替换；`old` 必须唯一命中 |
| `file_list` | `dir` | 🟢 | 列目录 |
| `file_search` | `dir,keyword,contains?` | 🟢 | 文件名搜索；可选内容搜索（<100KB） |
| `file_stat` | `path` | 🟢 | 存在性/类型/大小 |
| `file_mkdir` | `path` | 🟡 | 建多级目录 |
| `file_move` | `from,to` | 🟡 | 移动/重命名 |
| `file_copy` | `from,to` | 🟡 | 复制 |
| `file_remove` | `path` | 🔴 | 不真删，移入 `runs/.trash/<ts>/`；多个用 `file_batch` |
| `file_batch` | `ops[]` | 🟡 | 一次执行多个 write/append/remove/mkdir/move/copy（最多 30，建议 10-20）；系统路径受保护 |
| `doc_read` | `path,max_chars?` | 🟢 | 文档读取：txt/md/json 直读；csv/tsv → Markdown 表格；docx → 纯文本；xlsx → Markdown 表格。旧版 `.doc/.xls` 与 PDF 返回不支持提示 |

## 感知（10）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `shot` | – | 🟢 | 截图存 `runs/<ts>/shots/` |
| `ocr` | `max?` | 🟢 | PaddleOCR，本地 ~260ms/屏，返回文本+置信度+坐标 |
| `look` | `question` | 🟢 | 看图：视觉模型优先，视觉模型未配置/失败/限流时回退主模型；受 `capabilities.mainVision` 与 `capabilities.visionModelVision` 控制；调用次数不设上限，仅受 4s 最小间隔与 429 冷却约束。两项都关时 `look`/`shot` 与自动看屏一并关闭。`vision.mode='every-step'` 时由框架自动调用，不再下发给模型。问题含“坐标/位置/在哪/center”等词时走结构化契约：视觉只回 `{found,label,screen,center}`，`center` 为 0-1000 归一化，框架统一换算成设备像素与一位小数百分比；`found=false` 时不产出坐标。视觉默认开 thinking（`vision.thinking=true`，`vision.maxTokens=2000` 留足 reasoning 预算），空响应且 `finish=length` 时自动翻倍预算重试一次；工具结果只回传 `answer`（与 `points`），不回传 reasoning 或原始响应 |
| `clip_get` / `clip_set` | `text?` | 🟢/🟡 | 剪贴板读写 |
| `device_info` | – | 🟢 | 机型/分辨率/电量/当前页 |
| `app_list` | `keyword?` | 🟢 | 已安装包名 |
| `wait_text` | `text,timeout?` | 🟢 | 等界面出现文字 |
| `notify_read` | – | 🟢 | 拉开通知栏读文本，读完自动收起 |
| `image_describe` | `path,question?` | 🟢 | 读本地图片（用户附件/截图）：缩放+JPEG 后交给视觉模型，返回文字/表格/画面描述；视觉模型优先、主模型兜底 |

## 定时任务（3）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `schedule_add` | `task,interval_minutes` | 🟡 | 创建固定间隔周期任务；写 `schedules.json` + `schedule.sync`，由 AlarmManager 到点自动新建任务 |
| `schedule_list` | – | 🟢 | 列出周期任务（含暂停状态与下次触发时间） |
| `schedule_remove` | `id` | 🟡 | 删除周期任务；已产生的对话记录不受影响 |

## 浏览器（5）

内置 WebView（`AgentBrowserActivity`）+ DOM 工具，命令走 `browser/cmd.json` → `browser/result.json`。

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `browser_open` | `url` | 🟡 | 打开网页并等待加载完成（需 http/https） |
| `browser_read` | – | 🟢 | 返回标题、URL、可见文本（≤12000 字）、链接、输入框列表 |
| `browser_click` | `selector? 或 text?` | 🟡 | 按 CSS 选择器或可见文字点击，自动 scrollIntoView |
| `browser_input` | `selector? 或 text? ,value` | 🟡 | 填值并触发 input/change 事件 |
| `browser_back` | – | 🟡 | 网页后退 |

> 限制：Android 后台启动 Activity 受限，浏览器工具仅在前台任务可用（后台定时任务不可用）。

## 交互（1）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `ask_user` | `question,options?,timeout_seconds?` | 🟡 | 向用户提问并阻塞等待；UI 在输入框上方显示提问卡（选项/文本框）；默认 300s，最长 600s；用户停止任务立即返回 |

## 任务（4）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `plan_set` | `steps[],note?` | 🟢 | 复杂任务先列计划；自动用步骤初始化 todo |
| `todo_write` | `todos[{content,status}]` | 🟢 | 维护清单；只允许一个 in_progress |
| `task_note` | `text` | 🟢 | 记一条关键事实，后续步骤带上 |
| `task_done` | `summary` | 🟢 | 收尾并给出结论 |

## 系统 / Root（1）

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `root_shell` | `command` | 🔴 | 以 root 执行任意 shell 命令；必须开启「Agent Root权限」（开启时二次确认）。每次执行重新读取 `ui-config.json` 硬拦截，30s 超时（`code=124`），并写入 `runs/<id>/root-audit.jsonl`（命令/耗时/退出码/输出前 500 字） |

## 元参数（所有工具共有）

> 系统路径保护：`runs/`、`conversations/`、`src/`、`task.txt`、`control.txt`、`conversation.txt`、`resume.txt`、`ui-config.json`、`approval.json`、`approval.txt`、`ask.json`、`answer.txt` 不能通过文件工具删除、移动或覆盖（后四个是人工确认/提问通道，Agent 自己写入就等于绕过审批）。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `thought` | ✅ | 一句话说明这一步做什么，同时写日志 |
| `expect` | 否 | `{ any_text?, not_text?, pkg?, changed? }`，动作后本地核对 |

## 技能层（skills）

技能库在应用私有目录 `files/agent/skills/<folder>/SKILL.md`（外部不可直接修改）：frontmatter 含 `name`/`description`，正文是执行说明；旧版外部技能首次启动会一次性迁移。
Agent 每轮只把已启用技能的名称/描述放进系统提示；任务匹配时调用 `skill_load(name=技能名)` 读取正文，正文按需进入上下文。
启用状态存 `ui-config.json.skills.disabled`；App「技能」页支持新建、编辑、删除、从目录导入、ZIP 导入、单个技能 ZIP 导出（`/sdcard/Download/Axgent/skills`）、启停。
任务成功后由 `src/skills/distill.js` 自动判断是否沉淀（`skills.autoDistill=true`），写入同名 `SKILL.md`。

> `src/skills/*.js` 里 `bilibili.js` / `files.js` / `notifications.js` / `settings.js` 是早期模块化技能，已不被引用（技能改为 SKILL.md）；`src/skills/index.js`（加载/目录）与 `distill.js`（自动沉淀）仍是现役代码。

## 长期记忆（memory）

- 目录 `/sdcard/Download/phone-agent/memory/`：`FACT.md`（偏好/事实，一行一条，启动时注入系统提示）+ `journal.jsonl`（任务事件）。
- 工具：`memory_search(query, limit)`、`memory_append(text, kind=fact|event)`、`memory_update(old_text, new_text)`。
- 任务结束自动追加一条 journal（task/done/steps/result/run）；`memory.maxEvents` 默认 200，超出裁旧。
- 配置：`memory.enabled`（默认 true）、`maxChars`（注入上限 1200）、`maxEvents`（200）。

## AutoX 能力映射（节选）

| AutoX 模块 | 对 Agent 暴露 | 状态 |
| --- | --- | --- |
| `accessibility` / `automator` | `gui.*` | ✅ |
| `files` | `file.*` | ✅ |
| `images`（截图） | `shot` / `look` | ✅（resize 不可用，发原图） |
| `paddle` | `ocr` | ✅ ~260ms/屏 |
| `app` | `app_list` / `gui_launchApp` | ✅（`getInstalledPackages` 不可调，改走受控 shell：优先 Shizuku，其次 root 的 `pm list packages`）|
| `device` | `device_info` | ✅ |
| `clipboard` | `clip_get` / `clip_set` | ✅ |
| `events`（通知栏） | `notify_read` | ✅（`automator.notifications` 不可调，改走受控 shell：优先 Shizuku，其次 root 的 `cmd statusbar`）|
| `shell` | `root_shell` | ✅ 已开放，受「Agent Root权限」实时硬闸门控制 |
| `java` | `system.java` | ⏸ T3，未开放 |
| `ui` / `floaty` | 不暴露 | 有意为之 |

## 未开放（有意为之）

`ui` / `floaty` / `threads` / `engines` / `java` —— 前三个防止 Agent 改自己的运行环境，
`java` 属于 T3 逃生舱，仍未开放；`shell` 已通过受控的 `root_shell` 开放。
