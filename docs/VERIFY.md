# 待验证清单（M0）

实测环境：OnePlus PLC110 · Android 16 / SDK 36 · 1272×2800 @560dpi
内置 AutoX v7.2.4 定制分支（`com.axgent.agent`）· Rhino 引擎 · 最后更新 2026-11-11

> 2026-11-11 说明：下表的 M0 结论是当时用 release 版 AutoX（`org.autojs.autoxjs.v7`）跑的探针结论；
> 现在脚本由 App 内的 `EngineController` 直接运行，旧的 `RunIntentActivity` 已在 agent 变体中被移除，
> 表中涉及它的验证方式仅作为历史记录保留。新增的并行 / 控件树 / 页面栈验证见文末「2026-11 回归」。

跑完 `src/probe.js` 后逐条填写；结论 ✅ 成立 / ❌ 不成立 / 🟡 部分成立 / ⏳ 未测。

| # | 假设 | 验证方式 | 实测结果 | 结论 |
| --- | --- | --- | --- | --- |
| V1 | 能 `require('./x')` 与内置包 | 探针 `modules.require` | `app`/`axios`/`lodash`/`dialogs` 可用；`accessibility`/`vue-ui`/`java`/`engines`/`toast`/`media` 不可用。相对 `require` 已在 `main.js`/`core` 中大量使用 | ✅ |
| V2 | `auto`/`files`/`images` 等全局存在 | 探针 `globals.scan` | 全部存在；缺 `clipboard`（用 `setClip/getClip`）、`crypto`、`zip` | ✅ |
| V3 | `auto.takeScreenshot()` 在 Android 16 可用 | 探针 `screenshot.probe` | 首次 1539ms，第二次 19ms，输出 1272×2800 | ✅ |
| V4 | `floaty.rawWindow(XML)` 能建面板 | `panel-test.js` | 建窗成功，`setPosition/setSize/setTouchable/close` 均可用 | ✅ |
| V5 | `vue-ui` 能渲染进悬浮窗 | 探针 + 源码 | `ui` 只有 `bindingContext`，无 `layout/inflate`；`vue-ui` 模块不可 require | ❌ |
| V6 | 面板不出现在树与截图 | `panel-test.js` + 裁剪对照 | **树：不出现**（AutoX 自身过滤）；**截图：会出现** | 🟡 |
| V7 | `select().packageNameMatches()` 能绕开 topmost 自绘窗 | `panel-test.js` | 面板压顶时仍找到 launcher 节点 | ✅ |
| V8 | `input -d <displayId>` 可用 | root shell | 等价实现：`vd_server` 的 `/api/action click/swipe/key` 可作用于指定副屏 Display，`runs/20261031-*` 多次 `mobile -> ok`；见 VD.md | ✅（等价实现） |
| V9 | `am start --display` 能把 App 拉进虚拟屏 | root shell | 等价实现：`vd_server /api/action launch_app` 成功拉起应用，VD 任务内可见 | ✅（等价实现） |
| V10 | MediaProjection 建 `VirtualDisplay` + ImageReader | 原生 spike（M3） | 未采用该路线；改用 root `vd_server` 创建真实 VirtualDisplay，预览走 `/api/screenshot` | ❌（已被 vd_server 替代） |
| V11 | PaddleOCR 速度/准确率 | 100 张截图基准 | API 存在（`ocr/ocrText/initOcr`），未跑基准 | ⏳ |
| V12 | 自身包名白名单可配且生效 | 配置后点面板 | 未配；但默认已不进树 | 🟡 |

## 待补充测试（P1，2026-11-02）

以下两项因当前没有对应平台的有效 API Key，暂未实测，状态标记为「待补充测试」：

| 项目 | 状态 | 原因 | 补测方式 | 完成判据 |
| --- | --- | --- | --- | --- |
| Anthropic 代理模型发现回退 | 待补充测试 | 无有效 Anthropic 兼容代理 Key | 配置 `baseUrl` 指向仅提供 `/v1/messages`、不提供 `/v1/models` 的 Anthropic 代理，在模型服务页输入 Key 触发自动发现 | 页面先提示主端点失败，随后通过同主机 OpenAI `/v1/models` 回退展示模型列表 |
| Gemini 真实 Key 验证 | 待补充测试 | 无 Google AI Studio / Gemini 有效 Key | 用真实 Key 添加 Gemini 服务商并检测 `gemini-*` 模型 | `testModelConnection` 返回 200 且模型列表/添加/调用均成功 |

## P1/P2 实测（2026-11-02）

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| root_shell 开启 | run `20261102-054535`：`uid=0(root) gid=0(root) context=u:r:ksu:s0`；`root-audit.jsonl` 记录 `code=0`、耗时 47ms | ✅ |
| root_shell 超时 | run `20261102-054407`：`sleep 40` 在 30.0s 被终止，`code=124`、`timedOut=true`，审计记录「命令超时（30s）」 | ✅ |
| root_shell 关闭硬拦截 | run `20261102-054615`：`root_shell -> fail | Agent Root权限未开启，root 命令已被硬拦截`，审计记录 `code=-1` 且无执行输出 | ✅ |
| 开启前二次确认 | 能力弹窗开关勾选后出现「开启 Agent Root权限？」确认框，取消不修改配置，确认后 `capabilities.root/root.enabled=true` | ✅ |
| 服务商级思考强度映射 | DeepSeek 选择「中」后 `llm.reasoningEffort=high`（Cherry reasoningContracts: medium→high）；恢复默认后为空 | ✅ |
| 服务商启停 | 关闭 deepseek 后 `providers.enabled=false`，模型行显示「服务商已停用」；重新开启并选择模型后恢复 `activeModelId` | ✅ |
| 模型状态定期刷新 | 进入模型管理页自动检测活动对话/视觉模型（437ms / 995ms），`lastCheckedAt` 与状态文案更新 | ✅ |
| 独立产品 APK | `assembleAgentProd` 产物 label=Phone Agent，仅 `AgentChatActivity` 为 LAUNCHABLE；无 `assets/template.apk`；同包同签名安装后脚本、root、无障碍均正常 | ✅ |

测试日志与审计已归档到 `runs/probe/protocol-tests/`（054407/054535/054615 的 log/steps/audit）。

> P2 交付形态：`agent` flavor + `prod` buildType 的单一 APK，仅 `AgentChatActivity` 为 LAUNCHABLE；正式 release 签名与包名变更按用户要求暂不执行。2026-11-11 收敛后 APK 不再包含 `assets/template.apk`、`assets/codeeditor/dist.zip` 与 `lib_bin_aapt2.so`。

## UI 性能实测（2026-11-02）

设备峰值 144Hz，`gfxinfo` 的 Janky 口径基于 144Hz；同时按 60fps（16.7ms）阈值统计逐帧数据。

| 场景 | 优化前 | 优化后 | 结论 |
| --- | --- | --- | --- |
| 抽屉 + 能力弹窗 + 模型菜单 | 622 帧，7.72% Janky，p99 250ms | 616 帧，3.73% Janky，p95 13ms，p99 34ms，Missed Vsync 4 | ✅ 周期性卡顿消除；菜单已改内联，残余 1–2 帧约 33ms |
| 服务商列表滚动 | 397 帧，6.55% Janky，p99 200ms | 589 帧，3.23% Janky，p99 18ms；最近 120 帧 0% >16.7ms | ✅ 60fps |
| 历史长对话滚动 | — | 721 帧，p95 18ms，p99 23ms；最近 120 帧 p95 14.9ms | ✅ 60fps |
| 受控 9s 流式输出 | — | 252 帧，p95 23ms，p99 65ms；最近 120 帧 p95 12.4ms、3.33% >16.7ms | ✅ 主体 60fps；切最终卡有一次性尖峰 |
| 静置 12s | — | 0 帧重绘，0 Janky | ✅ 无周期性重绘 |
| 模型/思考菜单开合 | 483 帧，7.66% Janky，p95 48ms，p99 101ms | 333 帧，4.80% Janky，p95 15ms，p99 40ms，Missed Vsync 6→1 | ✅ 已移除 Popup Window，首帧尖峰明显降低 |

详见 [PERFORMANCE.md](./PERFORMANCE.md)。测试结束后已恢复真实模型配置与无障碍服务。

## 上下文窗口与压缩实测（2026-11-03）

| 项 | 结果 |
| --- | --- |
| 服务商窗口 | `deepseek-flash` 从 `/models` 取到 **1,048,576 tokens**（`contextSource=provider`）；`glm-4.6v-flash` 无元数据，按模型族估算 **128K** |
| 配置落盘 | `llm.contextWindow=1048576`、`llm.contextCompressRatio=0.8`、`conversation.maxTurns=8/maxChars=12000` |
| 压缩触发实测 | 临时把窗口设为 2000（阈值 1600），run `20261103-004011`：日志出现“上下文预算 window=2000 trigger=1600 (80%)”，step3/step4 分别压缩 epoch1/2，`beforeTokens 10534→afterTokens 960`、`10627→1042`，任务正常 `task_done` |
| 压缩落盘 | `steps.jsonl` 写入 `kind:"compaction"`，含 `contextWindow/triggerPercent/beforeTokens/afterTokens`，UI 显示压缩卡片 |
| 超限兜底 | 服务商仍返回 context 错误时，压缩历史并重试当前步骤；无法再折叠才结束并提示检查窗口配置 |

测试证据：`runs/probe/context-tests/20261103-004011-main.log` / `20261103-004011-steps.jsonl`。测试后已将窗口恢复为服务商真实值 `1048576`。

## 对话 UI 与流体云实测（2026-11-03）

设备：一加 PLC110 / ColorOS V16.1.0（Android 16 / API 36）；APK `7.2.4 (724)`。

| 项 | 结果 |
| --- | --- |
| 上下文徽标 | 最终回答卡片右下角显示圆环 + `1%`；`steps.jsonl` 实测 `promptTokens=11114/11825/12507`、`contextWindow=1048576`，数据链路与轮次更新正常；旧 run 无字段时隐藏 |
| 输入卡布局 | 模型 `deepseek-flash` 与 `思考 默认` 同一行，模型在前、思考在后；输入框最小高度 64→48dp，空态与多行状态均无换行/溢出 |
| 流体云提升 | `dumpsys notification` 出现 `PROMOTED_ONGOING`；应用退到桌面后 ColorOS 顶部胶囊显示应用图标 + 当前动作（实测「执行命令」，不再显示步数） |
| 流体云关闭 | 完成/失败统一 10s：`02:20:48.674 任务已结束` → `02:20:59.665 notificationRemoved` = **10.99s**（1.5s 轮询粒度）；暂停 10s 取消提升但保留普通通知 |
| 设置入口 | 设置页新增「流体云实时活动」，实测显示「已开启 · 退到后台时在流体云显示当前动作」；未开启时显示「未开启 · 点击前往系统通知设置开启」，并在首次启动 Toast 提示一次 |
| 权限前置 | 系统 `POST_PROMOTED_NOTIFICATIONS` 已授予；ColorOS「流体云显示实时活动」默认关闭，开启后 appop 变为 allow；未开启时退回普通通知 |

证据截图：`runs/probe/ui-context-badge.png`、`runs/probe/ui-composer-row.png`、`runs/probe/cloud-capsule-action.png`、`runs/probe/settings-fluid-cloud.png`；日志：`runs/probe/fluid-cloud-verify.txt`。

## 视觉权限策略实测（2026-11-03）

设备同前；主模型 `deepseek-flash`、视觉模型 `glm-4.6v-flash`。探针 `src/exp/vision-policy-test.js` 通过 v7Debug 的 `RunIntentActivity` 运行，日志 `runs/probe/vision-policy-verify.log`。

| 场景 | 配置 | 结果 |
| --- | --- | --- |
| 两项都开 | mainVision=true / visionModelVision=true | 日志 `视觉尝试：visionModel 模型=glm-4.6v-flash` → `视觉成功`，证明优先视觉模型 |
| 视觉模型不可用 | 两项都开，视觉模型名改为无效；主模型临时换成 GLM 多模态 | 视觉模型返回 400「模型不存在」后，日志出现 `视觉尝试：mainModel 模型=glm-4.6v-flash` → `视觉成功`，回退闭环 |
| 只开视觉模型 | mainVision=false | 只出现 `visionModel` 尝试并成功 |
| 只开主模型 | visionModelVision=false，主模型为文本模型 | 只出现 `mainModel 模型=deepseek-flash`，返回 `Unsupported image_url`（符合预期：主模型非多模态） |
| 两项都关 | mainVision=false / visionModelVision=false | 工具集从 37→35（移除 `look`/`shot`），任务仍正常完成；OCR 归屏幕读取不受影响 |
| 旧配置迁移 | 仅 `capabilities.vision:true` | 解析为两项均为 true；UI 切换后写入 `mainVision/visionModelVision` 并保留 `vision` 兼容字段 |

截图：`runs/probe/ui-vision-permissions.png`；汇总：`runs/probe/vision-policy-verify.txt`。测试后已重装产品 APK 并把 `mainVision/visionModelVision` 恢复为 true；当前主模型为文本模型，若需要主模型兜底成功，需把主模型换成多模态模型。

## 视觉调用稳定性修复实测（2026-11-03）

症状：`look` 调 `glm-4.6v-flash` 时 reasoning 吃满 max_tokens 返回空内容（单次 15–20s）；返回坐标单位混用，主模型推理里出现 `954‰`、`→`（界面显示近似 `954%o`），并自行猜算出 `(1119,2671)`。

根因与修复：

| 问题 | 证据 | 修复 |
| --- | --- | --- |
| thinking 占满输出预算 | run `20261103-033355`：`finish=length reasoning=1355 content=0 completion=800`，耗时 15s | `vision.thinking=false`（初始修复默认关闭；2026-11-03 起默认改为 `true`，预算同步提到 2000，空响应时仍自动翻倍重试）；`llm.chat` 支持 `thinking:{type:"disabled"/"enabled"}`（不支持时自动移除重试） |
| 坐标单位混用 | run `20261103-030436` step3 返回 `[88, 954]`；另测出现 `[[80,926,930,991]]`、`[0.9,0.976]` | 问坐标时强制单行 JSON `{found,label,screen,center}`；`parseVisionPoint` 按值域归一化百分比 / 0-1000 / 0-1 小数 / 设备像素，输出 `center`（设备像素）与一位小数百分比 |
| 主模型拿到原始 JSON 后二次猜测 | 同 run step4 reasoning 逐位拆 bbox、写 `954‰` | `look` 成功后不再把 raw 回传；系统提示改为 center 直接用、`found=false` 不许猜坐标 |

修复后实测：

| run | 场景 | 结果 |
| --- | --- | --- |
| `20261103-034516` | 短信会话页，纯视觉定位发送按钮 | 2 次视觉均 `finish=stop reasoning=0 completion=31/48`；输出 `视觉定位：… center=(1155,2713) 百分比=(90.8%,96.9%)`；`gui_tapXY(1155,2713)` 落在 `send_btn` 实际 bounds `[1076,2632][1216,2772]` 内，无越界 |
| `20261103-034714` | 应用自身对话页（运行中右下角为 × 停止键） | 3 次视觉 `reasoning=0`，均返回 `found=false` 并说明“右下角是关闭按钮 ×，没有发送按钮”；未编造坐标、未误点 × |

结论：reasoning 不再占用输出预算，视觉调用不再返回空内容；坐标输出格式统一、数值可校验。另确认历史“乱码”不是编码损坏——磁盘字节为 `‰`(U+2030) 与 `→`(U+2192)，属于主模型对混用单位的猜测文本。

## 技能自动沉淀（2026-11-03，原待办需求已实现）

| 需求 | 参考实现 | 状态 |
| --- | --- | --- |
| 任务执行结束后自动总结并沉淀为技能（可复用 SKILL.md） | Cherry Studio `resources/skills/skill-creator/SKILL.md`（支持从对话历史提取工作流、“turn this into a skill”）与 `resources/builtin-agents/cherry-assistant/.claude/skills/faq-collector/SKILL.md`（问题解决后自动判断是否收录，做通用化沉淀）；技能目录由 `SkillService` 自动同步注册 | ✅ 已实现（2026-11-03）：`src/skills/distill.js` + `main.js` 任务成功后自动判断并写入 `skills/<name>/SKILL.md`，`steps.jsonl` 记 `kind=skill`，UI 在最终回答后显示「已沉淀技能」通知 |

### 自动技能沉淀实测（2026-11-03）

| 项 | 结果 |
| --- | --- |
| 触发 | 任务成功（`done`）且步骤 ≥2 时，`main.js` 用主模型判断是否沉淀；`skills.autoDistill=true` 默认开启 |
| 落盘 | run `20261103-043025`：`技能沉淀：md-files-backup → /sdcard/Download/phone-agent/skills/md-files-backup/SKILL.md`；frontmatter 为 `name/description`，正文含目标、前置条件、步骤、成功判据 |
| UI | `steps.jsonl` 追加 `{"kind":"skill","text":"已沉淀技能：…"}`，对话末尾（最终回答之后）显示通知卡片 |
| 去重/保护 | 任务未完成、中止/暂停、步骤 <2、模型判断不需要、同名技能已存在、内容含密钥时跳过并只记日志；单次最多 15 步摘要、正文 ≤800 字 |
| 可复用性 | 提示词要求正文用 `{源目录}`、`{目标目录}` 一类占位符，不写死一次性数据 |






## 决定落地进度（2026-11-04）

| 项 | 决定 | 状态 | 证据 |
| --- | --- | --- | --- |
| 历史会话 | 不做检索/分支；删除 + 批量删除 | ✅ 已实现 | run 目录 162→161→159；全选 159/160（排除当前/最新）；长按/「管理」进入多选；`AgentStore.deleteRuns` 清理无引用的 `conversations/<id>.jsonl` 与 `summary.txt` |
| 长期记忆 FACT + journal | 参考 Cherry | ✅ 已实现 | run `20261104-013206`：`memory_search` 无命中 → `memory_append` 写入 FACT；run `20261104-013256`：`memory_search` 命中并回答“深色模式”；`journal.jsonl` 2 条任务事件 |
| MCP / 知识库 / 语音 / 图像生成 / 多服务商容灾 | 不做 | 跳过 | 从补全清单移除 |
| 技能市场 | 只留接口，不实现 | ⏳ | 待设计外部仓库读取接口 |
| 运行中插话 | 对话内补充输入；消息落盘并按步注入；前台通知快捷回复 | ✅ 已实现（对话内） | run `20261104-015944`：控制通道 `followup:` 在下一步注入，最终回答补上 build number；run `20261104-020327`：UI 发送写入 `followups.jsonl` 并注入，重启后对话回显。通知/流体云快捷回复未做 |
| 定时任务 | TimedTask 暴露 + Agent 自调度 + 侧边菜单 | ✅ 已实现 | 见下方「定时任务」章节；run `20261104-024531`/`024711`/`025730` 由闹钟自动拉起任务并完成任务 |
| 附件输入 | 图片接视觉、文件接文本 | ✅ 已实现 | 系统文件选择器 → `attachments/` → `attachment.json` → 系统提示注入；run `20261104-030952` 未给路径也自动 `doc_read` 读附件；run `031101` `image_describe` 视觉读图成功 |
| 文档转换 | docx/xlsx/csv → 文本/Markdown | ✅ 已实现 | run `20261104-030616` CSV→Markdown 合计 8；run `030733` docx 纯文本 + xlsx 表格合计 8；PDF/旧版 doc/xls 返回明确不支持 |
| 浏览器自动化 | 内置 WebView + DOM 工具 | ✅ 已实现（前台） | run `20261104-032155`：`browser_open` + `browser_read` 返回 example.com 标题/正文/链接；`browser_click/input/back` 同通道 |
| 多虚拟屏并行 | Agent 自主创建并行子任务 | ✅ 已实现 | `DaemonMain` 支持 slot 1..6（父任务内部 slot 7）；Agent 通过 `spawn_task/spawn_wait` 自主分配子 Agent；用户入口已移除；设置页新增「后台最大虚拟屏数量（1-5，系统硬上限 6）」；实测 run `20261104-052723` 父 Display 4 + 子 Display 5/6 并行完成并汇总 |
| durable resume | 崩溃/被杀后自动续跑 | ✅ 已实现 | run `20261104-033845`：force-stop 后重开 App，`auto-resume.json` 记录 run id，`main.log` 显示「从断点恢复：已完成 1 步」，随后继续提问并完成 |
| ask_user | Agent 主动澄清 | ✅ 已实现 | run `20261104-033209`/`033845`：UI 提问卡出现、点选项“apple/Beijing”后 `answer.txt` 回传，工具返回并继续任务；`ask.json`/`answer.txt` 用完即删 |

> 驳回项：会话持久化不重做 SQLite（保留 JSONL + checkpoint，已满足单机需求）；plan/todo 不照搬 Cherry（Claude Code runtime 原生工具不可移植，现有 `plan_set`/`todo_write` 等价）；多代理按“不做 Cherry 跨会话委派、做多虚拟屏并行”理解。

## 本轮新增能力（2026-11-04）

### 定时任务

| 项 | 实现 |
| --- | --- |
| 存储 | `schedules.json`（`id/task/intervalMinutes/enabled/nextAt`），App 与 JS 工具共用 |
| 调度 | `AgentScheduleStore`：`AlarmManager.setExactAndAllowWhileIdle`（无精确闹钟权限时退回 `setAndAllowWhileIdle`）；同 id 先 cancel 再 set |
| 触发 | `AgentScheduleReceiver`：唤起 `AgentTaskService` → `AgentStore.startTask(task)` → `markFired` 排下一次；已有任务时跳过本次；已删除任务忽略残留闹钟 |
| 重启恢复 | `BOOT_COMPLETED` 与 `AgentChatActivity.onCreate` 调 `scheduleAll`；provider 静默期由 `AgentTaskService` 消费 `schedule.sync` |
| Agent 工具 | `schedule_add(task, interval_minutes)` / `schedule_list` / `schedule_remove(id)`；JS 写 `schedules.json` + `schedule.sync` 标记 |
| UI | 侧边菜单「定时任务」：列表、新建（任务+间隔）、启停 Switch、删除 |
| 实测 | UI 创建 1 分钟任务 → 02:45:31、02:47:11（后台）、02:57:30 自动生成 run 并完成；Agent 用 `schedule_add` 创建 3 分钟任务（run `025153`），到点自动执行 |
| 限制 | ColorOS 实测触发窗口约 +45s；应用被 force-stop / 长期不用进入 RESTRICTED bucket 时可能延后数天，需在系统设置允许自启动/后台活动 |

### 附件与文档转换

| 项 | 实现 |
| --- | --- |
| UI | 输入框左侧「+」→ 系统文件选择器；复制到 `attachments/<ts>_<name>`，输入框上方显示附件 chip（可移除） |
| 传递 | `startTask` 写 `attachment.json`（`path/name/mime`）；`core/attachment.js` 在系统提示注入路径与建议工具 |
| 图片 | `image_describe(path, question)`：本地图片 → 缩放/JPEG → 视觉模型（视觉模型优先、主模型兜底） |
| 文档 | `doc_read(path)`：txt/md/json 直读，csv/tsv → Markdown 表格，docx（`word/document.xml`）→ 纯文本，xlsx（`sharedStrings.xml` + `sheet1.xml`）→ Markdown 表格；旧版 `.doc/.xls`/PDF 返回明确不支持 |
| 实测 | CSV（run `030616`）、docx+xlsx（run `030733`）、附件自动读取（run `030952`）、图片视觉（run `031101`）全部通过 |

### 浏览器自动化

| 项 | 实现 |
| --- | --- |
| 窗口 | `AgentBrowserActivity`：原生 WebView + 顶部标题/关闭 |
| 通道 | JS 写 `browser/cmd.json`（`id/op/...`），Activity 每 250ms 轮询执行并写 `browser/result.json`；工具阻塞等待结果 |
| 工具 | `browser_open(url)` / `browser_read`（标题、URL、可见文本 ≤12000 字、链接、输入框）/ `browser_click(selector|text)` / `browser_input(selector|text,value)` / `browser_back` |
| 实测 | run `032155`：打开 `https://example.com`，返回 `Example Domain`、正文与唯一链接 |
| 限制 | Android 后台启动 Activity 受限：`:script` 进程直接 `startActivity` 返回 `-92`；改由前台 UI 进程 `AgentStore.maybeOpenBrowser` 代劳。因此**后台定时任务中浏览器工具不可用**，仅前台任务可用 |

### durable resume 与 ask_user

| 项 | 实现 |
| --- | --- |
| ask_user | `src/tools/ask.js` 写 `ask.json`；UI 在输入框上方显示提问卡（选项 chip + 文本输入）；回答写 `answer.txt {id,answer}`；工具轮询读取、校验 id、超时 30-600s、用户 stop 立即退出；用完删除两个文件 |
| 自动续跑 | 「运行与安全」可开关（默认开）；App 启动后若当前会话最新 run 未完成、无 `.stopped`、未暂停、6 小时内，且存在 checkpoint，则写 `auto-resume.json` 并 `resumeRun(id)`（保留 `resume.txt`，每个 run 只尝试一次） |
| 实测 | run `033845`：`device_info` 完成第 1 步后 force-stop；重开 App 自动 `RESUME` 并继续 `ask_user`；回答 Beijing 后正常结束并清理 `ask/answer` |

### 多虚拟屏并行（Agent 自主子任务）

**目标**：父 Agent 只能操作一个窗口；需要并行时由 Agent 自己通过 `spawn_task` 分配子 Agent，每个子任务独占一块后台虚拟屏。用户侧入口（侧边菜单「并行任务」、任务页、`AgentStore.startParallel`）已删除。

**设置**

- 「设置 → 运行与安全 → 后台虚拟屏」下方新增「后台最大虚拟屏数量（1-5）」。
- 后台虚拟屏关闭时输入框 `enabled=false`（灰色不可编辑）；只有开启后才能设置并生效。
- UI 输入只接受 1-5；保存时 `AgentStore.updateRunSettings` 再 `coerceIn(1,5)`。
- 配置加载 `AgentStore.loadConfig` 允许 1-6（系统硬上限），仅用于内部/手写配置；JS `Manager.maxDisplays()` 最终 `min(6, max(1, n))`。

**后端**

| 层 | 改动 |
| --- | --- |
| `DaemonMain.java` | slot>0 使用 `/data/local/tmp/vd_status_<slot>.json`、`vd_stop_<slot>`、显示名 `AgentVirtualDisplay-<slot>`；跳过 3071/3072 串流控制端口 |
| `run_daemon.sh` | 第 4 个参数透传 slot |
| `AgentVdRuntime.kt` | `MAX_PARALLEL_SLOTS=6`（用户可设 1-5）；`PARENT_VIRTUAL_SLOT=7` 给父任务；`stopAllSlots` 回收 1..7；`ASSET_VERSION=4` |
| ToolMain / `tool_cmd.sh` | 所有父子 Agent 的 ToolMain 调用通过 `/data/local/tmp/tool_cmd.lock` 串行；`disconnect` 后等待 400ms 再 halt，避免 “UiAutomationService ... already registered!” 僵尸注册 |
| 截图 | `screencap -d <SurfaceFlinger id>`，按显示名 `AgentVirtualDisplay-<slot>` 反查 SF id |

**控制层（每个 Display 独立）**

- `src/tools/vd-direct.js`：`mobile` 带 `display_id`（或 Agent `state.displayId`）时直接走 root shell：
  - 观察：`tool_cmd.sh "tree <displayId>"`（ToolMain 单次执行，按 display 过滤窗口）
  - 点击：`resolve node:<id>` → `input -d <displayId> tap x y`；也支持直接坐标
  - 滑动/按键：`input -d <displayId> swipe/keyevent`
  - 输入：ASCII 用 `input text`；非 ASCII 用 `clip_b64` + `KEYCODE_PASTE`
  - 启动应用：`am start --display <displayId>`
  - 应用列表：`pm list packages`
- `agent.js`：`displayId>0` 时观察/动作后核对/清理都走 `vd-direct`，不切 background、不停止共享 vd_server。

**编排层**

- `src/core/parallel.js`：Manager 持 config/Logger/LLM/createAgent；`spawn` 硬校验 VD 开关、并发上限、槽位占用；子 Agent `parallel=null`；`shutdown` 停止子 Agent + 回收槽位。
- `main.js`：父任务先 `vdDirect.ensureSlot(7)` 拿到父 Display，再创建 Agent（`displayId=parentDisplayId`），不使用 vd_server 的 UiAutomation；结束时停止父槽位。
- `tools/index.js`：新增 `spawn_task/spawn_status/spawn_wait/spawn_stop`；schema 仅在 `parallelEnabled` 时暴露；`execute` 再检查 `ctx.parallel`。
- Agent 系统提示只在 VD 开启且父 Display 就绪时追加并行说明。
- 自动续跑会创建/复用父槽位 7，不再依赖已删除的 `parallel.lock`。

**实测**

| 项 | 结果 |
| --- | --- |
| run `20261104-052723` | 父 slot 7 / Display 4；子 slot 1 / Display 5、slot 2 / Display 6 同时运行；子 1 读到 Android 16，子 2 读到 `com.android.documentsui`；父 `spawn_wait` 汇总；结束后 `vd_status_*.json` 全部消失、`vd-slots.json=[]`、DaemonMain 进程 0 |
| 设置灰显 | VD 关闭时「后台最大虚拟屏数量」节点 `enabled=false`；开启后 `enabled=true` |
| 用户上限 | 输入 6 被 UI 过滤（字段保持空/原值），输入 5 接受；保存后 `ui-config.json.run.maxParallelDisplays` 为 1-5 |
| 旧入口 | run `20261104-041804-p1/p2` 为旧用户入口版本（Display 11/12），已被本方案取代 |

**限制**

- 用户最多 5 个并行子任务；系统硬上限 6；父任务额外使用内部槽位 7，不计入用户设置。
- ToolMain 的 UiAutomation 仍是系统单实例：所有子 Agent 的观察/点击必须串行（锁等待 15s）；单次观察约 0.4-2s，并发很高时会排队。
- 并行期间浏览器工具仍受 Android 后台 Activity 限制。
- UI 运行态按最新 run 展示，子 run 需在历史记录分别查看。

### 设置项代码级硬控制审计

> 原则：`schemas()` 只决定「是否暴露给模型」，`execute()` 与各工具 run 才是最终硬拦截；系统提示不承担安全约束。

| 设置项 | 代码级硬控制 | 结论 |
| --- | --- | --- |
| `capabilities.*` | `tools/index.js` 的 `CAP_TOOL` + `capAllowed`；`execute` 再检查一次；`schemas` 只负责隐藏 | 已有；本轮补 `image_describe→vision`、`doc_read→fileRead`、vision 双开关分支、`root_shell` schema 需 `capabilities.root` |
| `capabilities.screenRead` | 物理/虚拟模式观察都受 `capScreen`；`mobile.observe` 在 `execute` 拒绝；其它 mobile 动作结果剥掉 `data` 控件树 | 本轮补齐（此前虚拟屏模式会绕过屏幕读取开关） |
| `capabilities.device` | `device_info`/`app_list`/`notify_read` 由 `CAP_TOOL` 拦截；本轮补 `mobile` 的 `list_apps` 动作 | 本轮补齐（此前 `mobile/list_apps` 可绕过） |
| `capabilities.mainVision` / `visionModelVision` | `CAP_TOOL` vision + `requires='vision'` + `ctx.visionEnabled` | 已有 |
| `run.virtualDisplay` | `schemas` 隐藏 mobile/spawn/gui 等；`execute` 拒绝 `mobile` 和 `spawn_*` | 已有；本轮 spawn 也纳入 |
| `run.maxParallelDisplays` | Kotlin UI `coerceIn(1,5)`、`loadConfig` `coerceIn(1,6)`、JS `MAX_HARD=6` + runningCount/slot 检查 | 本轮新增，三层硬限制 |
| `run.maxSteps` / `maxMinutes` | `Agent.run` 循环条件 + 每轮时间检查 | 已有 |
| `run.approvalTools` | `needsApproval` + `_waitApproval` 在每次工具执行前阻塞 | 已有 |
| `run.autoResume` | Kotlin `maybeAutoResume` 首行 `if (!config.autoResume) return` | 已有 |
| `web.*` | `web.js` 校验 `allowPrivateUrls`、`excludeDomains`、`maxResults`（clamp 10）、provider 合法性 | 已有 |
| `sandbox.roots` / `outsideSandbox` / `sandboxHighRisk` | `sandbox.need` 逐路径校验；每个文件写/删/移/批量操作都调用 | 已有 |
| `skills.disabled` | `skills.all/enabled/load` 过滤；`renderCatalog` 不列禁用技能 | 已有 |
| `memory.enabled` | memory 工具每次执行检查；`render` 关闭注入；本轮 `schemas` 也隐藏 `memory_*` | 本轮补齐 schema 隐藏 |
| `root.enabled` + `capabilities.root` | `root_shell.run` 每次重读 `ui-config.json` 双开关；本轮补 `root.denyPatterns` 硬拦截 | 本轮补齐 denyPatterns |
| `thinking` / `theme` / 模型配置 | 写入 LLM 请求或 UI 状态 | 非安全能力，无需硬拦截 |

**审计结论**：App 中与 Agent 能力/安全相关的可设置项均已在代码层生效；`schemas` 与 `execute` 可能有意不一致（隐藏失败时 execute 仍兜底），但不存在仅靠提示词的约束。`ask_user`、`schedule_*`、`task_done` 等无对应安全开关，保持常驻。
## 其余小项

| 项 | 状态 |
| --- | --- |
| 技能市场外部仓库接口 | ⏳ 未做（用户后续单独项目） |
| 通知/流体云快捷回复 | ⏳ 未做（对话内插话已可用） |
| 运行中消息实时刷新 | 🟡 已知限制：UI 轮询下基本实时，个别人工停止后需重开页面才回显（未彻底修复） |

## 关键发现（历史）

| 发现 | 证据 | 影响 |
| --- | --- | --- |
| `.js` 走 Rhino，不是 Node | `process` 有、`module` 无、`Autox=undefined`、`Packages` 有 | 全项目按 ES5 写 |
| 注入点击可穿透自有悬浮窗 | `flow-test.js` 用例 4：面板覆盖目标，`click()` 仍进入显示设置页 | 同 UID 注入被视为可信；但仍保留「出手前收起」策略 |
| 系统设置会强制隐藏悬浮窗 | `dumpsys window`：`mForceHideNonSystemOverlayWindow=true`、`mPolicyVisibility=false`；截图无红块 | 敏感页无法用面板急停 → 必须有音量键/通知通道 |
| 面板收起可从截图消失 | `hit-hidden.png` / `crop-hidden.png` | 截图前统一收起即可，不必改 alpha |
| LLM 原生 tool calling 可用 | `scripts/test-llm.ps1` | 866ms 返回标准 `tool_calls`，可直接用 |
| 无障碍可远程开启 | `settings put secure enabled_accessibility_services ...` | PC 侧可一键完成初始化 |
| 脚本可远程拉起 | `am start -n .../RunIntentActivity -d file:///...` | 开发循环无需手点 |

## 判定规则

- V2/V3/V4 为 ❌ 时 M0-2 方式要改；当前全部 ✅
- V6 截图项为 ❌ → 采用「截图前收起面板」，已实测有效
- V8/V9 已由 root `vd_server` 等价实现，不再依赖 `adb shell su`；VD 回归仍需在 LSPosed 模块重新启用并重启后复测
- V10 未走 MediaProjection；`vd_server` 创建真实 VirtualDisplay 并提供 `/api/screenshot`

## Agent 能力对标与差距分析（2026-11-03）

基准：Cherry Studio（外部参考仓库 https://github.com/CherryHQ/cherry-studio）当前已落地的 Agent 能力——四组 MCP 工具、Agent Session Runtime、技能库与市场、知识库、定时/渠道、浏览器/电脑自动化。
对象：Axgent（本仓库 `src/` + `android/app/src/main/java/org/autojs/autojs/agent/`），单机 Android GUI Agent。
标记：✅ 已有且可用 / 🟡 部分具备 / ❌ 缺失。
核验口径：**Phone 侧逐项以代码为准**，文档只作索引；下方“代码核验修正”列出文档与实现不一致处。

### 0. 代码核验修正（文档 vs 代码）

| 文档说法 | 代码事实 | 结论 |
| --- | --- | --- |
| docs/TOOLS.md §技能层：`src/skills/*.js` 模块技能 | 实际已是 `skills/<folder>/SKILL.md`（`src/skills/index.js`）；UI 走 `SkillManagerScreen`（`AgentSkillScreens.kt`，支持新建/导入/编辑/删除/启停） | 文档过期，报告按 SKILL.md 实际能力写 |
| 工具“37 个” | 初始 39 个；本轮新增 schedule 3、doc 1、browser 5、ask 1、image_describe 1 → 共 50；运行日志「工具集冻结：51 个」含 `mobile` | 报告写“定义 51，当前按能力裁剪” |
| AgentScreens `SkillsScreen()` | 是遗留只读页，只列 `/sdcard/phone-agent/src/skills/*.js`；实际路由在 `AgentChatActivity.kt:322` → `SkillManagerScreen` | 不以 `SkillsScreen()` 判断技能能力 |
| 审批“无 UI 配置” | `AgentSettingsScreens.kt:404-470` 的“运行与安全”页可编辑 `approvalTools`（逗号分隔工具名） | 审批有基础 UI，缺细粒度策略 |
| `paths.memoryDir` | 旧结论已过期：现为 `src/core/memory.js`（FACT.md + journal.jsonl）+ `src/tools/memory.js`，启动时注入系统提示 | ✅ 有长期记忆 |
| 文件读取能力 | 旧结论已过期：新增 `doc_read`（csv/tsv/docx/xlsx→文本/Markdown）；PDF 仍不支持 | ✅ 有文档转换（PDF 除外） |
| 聊天附件 | 旧结论已过期：输入区「+」接系统选择器，复制到 `attachments/` 并经 `attachment.json` 注入提示 | ✅ 有附件入口 |
| 运行中追加指令 | 旧结论已过期：`AgentUi.kt` 运行中显示「暂停/补充指令/停止」，写入 `followups.jsonl` 并按步注入 | ✅ 有 live steer（通知快捷回复未做） |
| 历史记录 | 旧结论已过期：有删除 + 批量删除（排除当前/最新）；仍无搜索/重命名/分叉 | 🟡 部分具备 |
| 主模型容灾 | `AgentStore`/`llm.js` 只有一个活动 `model`；多 provider 仅用于切换配置，无自动降级 | 无自动容灾 |

### 0.1 工具清单（按 `src/tools/*` 代码枚举，共 58 个候选）

- **GUI 10**：`gui_tap`、`gui_tapXY`、`gui_longPress`、`gui_setText`、`gui_key`、`gui_swipe`、`gui_back`、`gui_home`、`gui_launchApp`、`mobile`
- **文件 13**：`file_read`、`file_write`、`file_append`、`file_edit`、`file_list`、`file_search`、`file_stat`、`file_mkdir`、`file_move`、`file_copy`、`file_remove`、`file_batch`、`doc_read`
- **感知 10**：`shot`、`ocr`、`look`、`image_describe`、`clip_get`、`clip_set`、`device_info`、`app_list`、`wait_text`、`notify_read`
- **Web 7**：`web_search`、`web_fetch`、`browser_open`、`browser_read`、`browser_click`、`browser_input`、`browser_back`
- **记忆 3**：`memory_search`、`memory_append`、`memory_update`
- **定时 3**：`schedule_add`、`schedule_list`、`schedule_remove`
- **交互 1**：`ask_user`
- **任务/系统 7**：`skill_load`、`plan_set`、`todo_write`、`task_note`、`task_done`、`a11y_ensure`、`root_shell`
- **并行 4**：`spawn_task`、`spawn_status`、`spawn_wait`、`spawn_stop`

当前实跑：前台默认配置（Root 关闭、Shizuku 开启）为 **52 个**；未开虚拟屏时剔除 `mobile`/`spawn_*`，root 关闭时剔除 `root_shell`（`index.js` 能力裁剪）。

### 1. 能力对标矩阵（9 类）

| 能力域 | 能力点 | Cherry Studio（证据） | Phone Agent 现状（证据） | 差距 |
| --- | --- | --- | --- | --- |
| 工具调用 | 内置工具 | SDK 原生 Read/Edit/Bash + `bun`/`uv`/`rg`（cherry-tool-guide/references/cli.md） | 58 个候选工具，按能力裁剪（前台默认 52 个；清单见 §0.1；`src/tools/index.js`） | ✅ 覆盖移动端核心动作 |
| 工具调用 | MCP 客户端与服务 | 四组 MCP（`cherry-tools`/`agent-memory`/`skills`/`mcp-manager`）+ `install_mcp_server`（references/mcp.md） | 无 MCP 客户端，工具集编译期固定（src/tools/index.js） | ❌ |
| 工具调用 | 浏览器/电脑自动化 | Automation REPL：`cherry-browser`/`cherry-computer`，可点击、填表、截图（references/automation.md、web.md） | 内置 WebView + DOM 工具 `browser_open/read/click/input/back`（`AgentBrowserActivity`），另有 a11y+root 手机控制 | ✅ 前台可用（后台定时任务受 Activity 启动限制） |
| 工具调用 | 文档转换 | `to_markdown` 支持 Office/PDF/CSV 等（references/documents.md） | `doc_read`：txt/md/json/csv/tsv/docx/xlsx → 文本/Markdown（`src/core/doc.js`） | 🟡 覆盖常用格式，PDF 未做 |
| 工具调用 | 图像生成/交付物 | `generate_image`、`report_artifacts`（references/outputs.md） | 无；最终产物仅落在沙箱并给路径 | ❌ |
| 工具调用 | 跨会话委派/子代理 | `session_create`/`session_send`（references/sessions.md）+ runtime 原生编排（Claude Code / Pi） | 已实现并行子 Agent（`spawn_task/status/wait/stop`，每个子任务独占虚拟屏）；无跨会话委派 | 🟡 并行已实现，无 session 委派 |
| 多轮上下文 | 会话持久化/恢复 | SQLite Session + resume token（agent-session-runtime.md §Fresh turn/Resume token） | `conversations/*.jsonl` + `checkpoint.json`；崩溃/被杀后自动续跑（`auto-resume.json` + `resumeRun`） | ✅ 自动恢复 |
| 多轮上下文 | 上下文压缩 | Pi compaction（§Pi conversation compaction）；普通对话默认 **80%**（可配 20–100%） | 80% 阈值压缩、epoch、`kind:compaction`（`src/core/agent.js` L515，基数为原始 window） | ✅ |
| 多轮上下文 | 运行中插话（steer） | 队列式 live follow-up，不打断当前 turn（§Live follow-up） | 运行中「补充指令」写 `followups.jsonl`，下一步注入；`task_done` 前有 followup 会拒绝结束 | ✅ |
| 多轮上下文 | 后台会话/心跳 | background session + heartbeat + 最大执行时限（§Background sessions/Heartbeat deadline） | 前台 `AgentTaskService` + 流体云；定时任务用 AlarmManager 周期拉起 | 🟡 无持续心跳自治循环 |
| 多轮上下文 | 会话检索/分支 | `session_list/search/rename`、历史检索 | 历史页支持删除/批量删除；无搜索/分支/回滚 | 🟡 部分具备 |
| 规划拆解 | 计划/待办 | runtime 原生：Claude Code 的 todo/plan 工具；Pi 由 SDK 提供执行循环（非 Cherry MCP 工具） | `plan_set` + `todo_write` + 系统提示约束（src/core/agent.js L67-96） | ✅ |
| 规划拆解 | 步数/时间/熔断 | heartbeat 最大执行时限 + 状态恢复 | `maxSteps 25`/`maxMinutes 10`/loop+stuck 阈值/expect 自检（config/default.js、agent.js） | ✅ 移动端更细 |
| 规划拆解 | 定时/周期任务 | `cron` + `reuse_session` + 渠道投递（references/autonomy.md） | `schedules.json` + AlarmManager + 侧边菜单 UI + `schedule_add/list/remove` | ✅ 固定间隔（无 cron 表达式/渠道投递） |
| 记忆 | 长期事实/偏好 | `agent-memory` search/append/update + `memory/FACT.md`/`USER.md`/`SOUL.md`（references/memory.md、agent-session-runtime.md §System prompt） | `memory/FACT.md` 注入系统提示 + `memory_search/append/update` | ✅ |
| 记忆 | 事件日志 | `append` 追加 journal | `memory/journal.jsonl` 记录任务事件，可被 `memory_search` 检索 | ✅ |
| 记忆 | 知识库 | `kb_list/search/read/manage`（references/knowledge.md） | 无本地知识库/索引 | ❌ |
| 记忆 | 技能沉淀 | 技能市场 + `skill-creator` + `faq-collector` 自动收录 | SKILL.md 本地库 + 任务成功后自动沉淀（src/skills/index.js、distill.js） | ✅（无市场） |
| 多模态 | 屏幕视觉 | 浏览器/电脑截图 + 图像输入 | `look`/`ocr`/`shot`，视觉模型优先+主模型兜底（src/tools/perceive.js） | ✅ 移动端更强 |
| 多模态 | 用户图片/文件附件 | AI-SDK `read_file` 附件读取 + to_markdown | 输入区「+」选择文件 → `attachments/`；图片走 `image_describe`，文档走 `doc_read` | ✅ |
| 多模态 | 语音 STT/TTS | 未确认（工具指南未列） | 无 | ❌（基准也未确认） |
| 多模态 | 图像生成 | `generate_image` | 无 | ❌ |
| 工作流 | 技能库 | 全局技能库 + 市场检索/安装（references/skills.md） | 本地 SKILL.md：新建/编辑/删除/导入/启停（AgentSkillStore） | 🟡 无市场 |
| 工作流 | 自动沉淀 | skill-creator / faq-collector | 已实现自动总结沉淀（docs/VERIFY.md §技能自动沉淀） | ✅ |
| 工作流 | 定时/自动化编排 | cron + heartbeat + channel delivery | 定时任务（固定间隔）+ 虚拟屏 + 流体云 + 自动续跑 | 🟡 有定时，无渠道投递/表达式 |
| 工作流 | 多代理编排 | session 委派 + runtime 原生编排 | 多虚拟屏并行：父 Agent + 1-6 个子 Agent 各自绑定独立 Display（`spawn_*` + `vd-direct`）；无跨会话委派 | ✅ 并行（无委派） |
| 工作流 | 任务模板 | 定时任务编辑器/模板 | 空态 4 个 chips，无模板管理 | 🟡 |
| 第三方 | Provider/协议 | OpenAI/Anthropic/Gemini + 统一网关 | OpenAI Chat/Responses、Anthropic、Gemini、custom（src/core/protocols.js） | ✅ |
| 第三方 | Web 搜索/抓取 | `web_search`/`web_fetch` | DeepSeek/Zhipu/SearXNG/Bing + fetch（src/tools/web.js） | ✅ |
| 第三方 | IM 渠道 | Telegram/Feishu/Discord/Slack/WeChat/QQ（references/autonomy.md） | 无；只有系统通知/流体云 | ❌ |
| 第三方 | 通知推送 | `notify` 主动推送消息/文件 | 任务通知 + ColorOS 流体云（AgentTaskService/AgentPreviewOverlay） | 🟡 仅本机 |
| 异常处理 | LLM 重试/超时 | runtime 原生 retries/thinking 级别 | 4 次重试、thinking 参数回退、空响应翻倍、限流冷却（src/core/llm.js、perceive.js） | ✅ |
| 异常处理 | 卡死/恢复 | durable delivery、resume token、心跳强杀 | loop/stuck 熔断、expect 回读、checkpoint 续跑（手动） | 🟡 无自动续跑 |
| 异常处理 | 多服务商容灾 | 多 provider 配置 + 网关（自动降级未确认） | 可配置多 provider，但只有一个活动 `model`；仅同模型重试，无自动切换 | ❌ |
| 交互引导 | 权限开关 | Agent 配置/工具审批模式 | 13 项能力开关 + root 二次确认（AgentUi 能力弹窗） | ✅ |
| 交互引导 | 审批 | MCP 变更审批门 | `approvalTools` 可在设置页编辑（`AgentSettingsScreens.kt:404-470`）+ 确认弹窗 + 超时；无按风险/来源与 MCP 信任 | 🟡 |
| 交互引导 | 暂停/停止 | 用户停止/中断 | 音量键双击/长按 + UI 停止 + 流体云 | ✅ |
| 交互引导 | 进度/后台展示 | Session 列表、通知、artifacts | 流式卡片、步骤卡片、流体云、上下文徽标 | ✅ |
| 交互引导 | 澄清提问 | 对话内自然追问 | 无结构化 `ask_user`，只能结束或等人工接管 | ❌ |
| 交互引导 | 会话命名/搜索 | `session_rename/search/list` | 历史列表，无命名/搜索 | 🟡 |

> 压缩阈值代码核验（Cherry）：普通对话 `DEFAULT_CONTEXT_SETTINGS.compress.thresholdPercent = 80`（可配 20–100，`src/shared/data/types/contextSettings.ts:85-93`），触发点是 **inputRoom 的 80%**，`inputRoom = contextWindow − 输出预留`（`PersistentChatContextProvider.ts:984`、`inLoopCompaction.ts:174`），并保留 `0.375 × 触发点` 的原文轮次（`CONTEXT_COMPACT_KEEP_BUDGET_OF_TRIGGER`）。Claude Code Agent 用 `CLAUDE_AUTOCOMPACT_PCT_OVERRIDE = 80`（`runtime/claudeCode/environment.ts:61`）；Pi Agent 用原生 `contextWindow − reserveTokens`（生产未覆盖，仅测试显式设置）。Phone Agent 当前按**原始 window 的 80%**（`contextCompressRatio=0.8`，未扣输出预留），语义比 Cherry 更激进；1M 窗口下影响可忽略，小窗口模型建议改为 inputRoom。

### 2. 缺失能力与优先级

| 优先级 | 缺失能力 | Cherry 基准 | Phone Agent 现状 | 业务价值 | 移动端适配要点 | 影响 |
| --- | --- | --- | --- | --- | --- | --- |
| 高 | 跨会话长期记忆 | `agent-memory` + FACT/USER/SOUL | `paths.memoryDir` 仅在 `default.js:149` 定义、全仓库无读写，记忆只在本轮 | 免重复告知偏好/纠正，越用越懂用户 | `/sdcard/.../memory/FACT.md` + `journal.jsonl`，按能力开关读写，写入去敏 | 高：跨任务连续性缺失 |
| 高 | MCP 客户端 | 4 组 MCP + 服务注册/信任/审批 | 无 MCP 客户端；工具定义 39、按能力裁剪 37（`tools/index.js`） | 接日历、邮件、云盘、企业系统，能力可扩展 | 仅远程 SSE/HTTP（stdio 不适合 Android）；逐服务信任开关 + 审批 + 超时 | 高：能力边界受限 |
| 高 | 定时/周期自治 | cron + heartbeat + 渠道投递 | 引擎有 TimedTask 但未暴露，Agent 不能自调度 | 定时巡检、日报、定时清理 | AlarmManager/WorkManager + 前台服务 + 电池白名单 + 流体云；失败保活 | 高：从“被动执行”到“主动执行” |
| 高 | 运行中追加指令 | 队列式 live follow-up | 运行时输入条变停止键 | 纠偏、补条件，避免重跑整任务 | 用现有 `control.txt` 通道加 `followup` 队列，工具执行间隙注入 | 高：交互效率与成功率 |
| 高 | 用户附件/多模态输入 | 附件读取 + to_markdown + 截图 | 聊天仅文本 | 看图办事、读文档、语音下达 | 系统文件选择器/分享 Intent/相机；图片走视觉模型，文档先转 Markdown | 高：任务入口受限 |
| 中 | 多服务商容灾 | 多 provider + 网关（自动降级未确认） | 多 provider 可配置，但只有一个活动 `model`；仅同模型重试 | 限流/宕机时任务不中断 | 备用模型列表 + 切换策略（429/5xx/超时），保留用量记录 | 中高：稳定性 |
| 中 | 文档转换 | `to_markdown`（Office/PDF/CSV） | `file_read` 只读文本 | 处理下载的合同/表格/PPT | 内置转换库或调用转换 API；临时文件放沙箱 | 中：办公场景 |
| 中 | 本地知识库/RAG | `kb_*` 检索/管理 | 无 | 私有资料问答、按文档办事 | SQLite FTS/本地向量索引，后台索引，问答带引用 | 中：专业场景 |
| 中 | 会话检索/分支 | session search/fork/rename | 仅 run 列表 | 复用历史、回溯失败、分叉尝试 | 对 `conversations/*.jsonl` 建索引，支持搜索/复制为新会话 | 中：长期使用体验 |
| 中 | 子代理/并行委派 | `session_create/send` + 原生编排 | 已实现 `spawn_*`（父 + 1-6 子，用户上限 1-5） | 长任务拆分、并行采集 | 前台父 Agent + 后台子 Agent；代码硬限制并发与槽位，`spawn_wait` 回收结果 | ✅ 已实现（无跨会话委派） |
| 中 | 技能市场/远程安装 | `search_skills`/`install_skill` | 本地新建/导入/自动沉淀 | 复用社区技能，降低编写成本 | 下载 + 校验 + 审批 + 版本更新；离线可用 | 中：生态 |
| 中 | 分级审批与工具策略 | 审批门 + 信任模型 | `approvalTools` 有设置页（逗号分隔），无按风险/来源与 MCP 信任 | 安全与自动化的平衡 | 按工具/风险/来源配置，记住选择，MCP 逐服务信任 | 中：安全体验 |
| 中 | 结构化交付物/外发 | `report_artifacts` + `notify` | 产物仅沙箱路径 | 用户拿到成品文件 | 交付物清单 + 系统分享/通知/流体云 | 中：结果可用性 |
| 中 | IM 渠道接入 | Telegram/Feishu/Discord/Slack/WeChat/QQ | 无 | 远程下达任务、结果推送 | 移动端可接飞书/微信机器人；需连接状态与二维码 | 中：远程协作 |
| 中 | 语音输入/播报 | 未确认（基准未列） | 无 | 驾驶/双手占用场景 | Android STT/TTS，权限与隐私提示 | 中：入口多样性 |
| 中 | 结构化澄清工具 | 对话内自然追问 | 无 `ask_user` | 关键歧义时暂停确认，减少误操作 | 弹窗/通知式提问 + 超时 + 默认不执行 | 中：安全与成功率 |
| 低 | 图像生成 | `generate_image` | 无 | 海报/示意图 | 调用绘画模型 API，存沙箱并预览 | 低：非核心 |
| 低 | 托管 CLI/包管理 | bun/uv/rg + cli_install | `root_shell` 可执行任意命令 | 脚本化数据处理 | 移动端无完整运行时，维持 root_shell 即可 | 低：已有替代 |
| 低 | 浏览器 DOM/JS 自动化 | Automation REPL | GUI 点击可覆盖 | 网页表单更稳 | WebView/Chrome 调试协议受限，优先 GUI | 低：边际收益 |
| 低 | 成本/预算报表 | usage 记录 + 网关 | 有 token/用量与上下文徽标 | 控成本、按任务核算 | 汇总 `steps.jsonl` 用量，按日/任务统计 + 预算告警 | 低：管理需求 |
| 低 | 任务模板管理 | 定时任务编辑器 | 空态 4 个 chips | 常用任务一键复用 | 模板 CRUD + 与技能联动 | 低：可与技能合并 |
| 低 | Agent 人格/系统提示自定义 | SOUL/USER/FACT + system.md | 固定系统提示 | 个性化与角色化 | 设置页编辑，注入前做变量替换 | 低：差异小 |
| 低 | 多语言回复约束 | `agent.language` | 中文固定提示 | 出海/英文任务 | 配置回复语言，注入系统提示 | 低：可按需 |

### 3. 可落地补全需求清单

**P0（高，先做）**

| 编号 | 需求 | 落地方案 | 验收标准 |
| --- | --- | --- | --- |
| A1 | 跨会话长期记忆 | 新增 `src/core/memory.js`：`FACT.md` + `journal.jsonl`，提供 `memory_search/append/update` 工具；启动注入 FACT，任务结束可自动 append（复用 distill 收尾钩子） | 连续两次任务能召回上次偏好；记忆写入有开关与去敏；重启后仍生效 |
| A2 | MCP 客户端 | 新增 `src/tools/mcp.js`：远程 SSE/HTTP 连接、工具 schema 合并、逐服务启停/信任/审批；设置页增加 MCP 管理入口 | 接入 1 个远程 MCP 后工具出现在工具集；断连不影响本地工具；审批拒绝不执行 |
| A3 | 定时/周期任务 | 暴露 AutoX `TimedTask`/`TaskReceiver` 到 Agent UI；新增 `cron_set/list/remove` 工具与任务编辑器；复用前台服务与流体云 | 设定「每天 9:00 巡检」后能自动触发并产出 run；失败有通知；可暂停/删除 |
| A4 | 运行中追加指令 | `control.txt` 增加 `followup:<text>`；Agent 循环在工具间隙读取并注入用户消息（队列，不打断当前工具） | 任务运行中发送补充条件，下一步生效；不导致重复执行；UI 输入条在运行时可用（保留停止按钮） |
| A5 | 用户附件/多模态输入 | 聊天输入增加附件/相机入口；图片走 `look` 同源视觉调用；文档先转 Markdown 再入上下文；音频可选 STT | 发一张截图能基于它回答/操作；发 PDF 能摘要；附件大小受限且有类型校验 |

**P1（中）**

| 编号 | 需求 | 落地方案 | 验收标准 |
| --- | --- | --- | --- |
| B1 | 备用模型/容灾 | providers 增加“备用模型”与切换策略（429/5xx/超时）；失败自动切换并记录 | 主模型 429 时备用模型完成同一步；日志标注切换原因 |
| B2 | 文档转换 | 内置轻量转换或调用转换 API，产物落沙箱临时目录；`file_read` 支持读取转换结果 | docx/xlsx/pdf 能转 Markdown 并被摘要 |
| B3 | 本地知识库 | SQLite FTS 索引沙箱文档；`kb_add/search/read` 工具；回答带引用路径 | 指定目录建库后可检索；删除/重建索引可用 |
| B4 | 会话检索/分支 | 对 `conversations/*.jsonl` 建索引；历史页搜索、重命名、复制为新会话 | 能按关键词搜到历史任务；分叉后原会话不变 |
| B5 | 子任务/并行 | 新增 `task_spawn/status/result`，后台 run 目录隔离；限制并发数 | 两个子任务并行执行互不干扰；主任务可汇总结果 |
| B6 | 技能市场 | 远程技能索引 + 下载校验 + 审批安装 + 版本更新 | 能搜索/安装一个技能并在任务中加载；校验失败拒绝 |
| B7 | 审批策略细化 | 在现有 `approvalTools` 设置页基础上，增加按风险/来源的默认策略、记住选择、MCP 逐服务信任 | 关闭审批的工具不再弹窗；高风险仍强制确认；MCP 首次调用需信任 |
| B8 | 交付物/外发 | 任务结束登记产物（路径/类型/大小），对话卡片可打开/分享；通知/流体云携带产物 | 用户能从卡片直接分享文件；无产物时不显示 |
| B9 | IM 渠道 | 接入飞书/微信机器人（webhook/长连接）；连接状态、二维码、发送/接收 | 能从 IM 下达任务并收到结果；断连有提示 |
| B10 | 语音输入/播报 | 系统 STT 作为输入入口；TTS 播报最终回答（可关） | 语音下达任务可执行；播报可开关 |
| B11 | 澄清工具 | `ask_user(question,options,timeout)`，UI 弹窗/通知；超时默认不执行危险动作 | 歧义时弹窗并能继续任务；拒绝后按安全路径收尾 |

**P2（低）**

| 编号 | 需求 | 落地方案 | 验收标准 |
| --- | --- | --- | --- |
| C1 | 图像生成 | 接入绘画模型 API，产物入沙箱并在对话预览 | 生成图片可在卡片查看/分享 |
| C2 | 浏览器 DOM 自动化 | 评估 WebView/Chrome 调试协议可行性，优先保留 GUI | 不劣化现有 GUI 成功率 |
| C3 | 成本报表 | 聚合 `steps.jsonl` 用量，按日/任务展示与预算告警 | 能查看单任务 token/费用；超预算提示 |
| C4 | 任务模板 | 模板 CRUD，空态与技能联动 | 一键发起模板任务 |
| C5 | Agent 人格/系统提示 | 设置页编辑 system prompt，变量替换后注入 | 修改后新任务生效；可恢复默认 |
| C6 | 多语言回复 | `agent.language` 配置，注入系统提示 | 英文任务默认英文回复 |
| C7 | 定时任务可视化 | 后台会话/定时任务列表与状态页（可并入 A3） | 能看到下次触发时间与上次结果 |

> 结论：Phone Agent 在“设备操作、感知、任务闭环、异常自检”上已达到或超过 Cherry Studio 的桌面 Agent；主要差距集中在**跨会话记忆、MCP 生态、定时自治、运行中协作、多模态输入与多服务商容灾**。建议按 A1→A5 顺序推进，A2/A3 需要系统权限与后台保活配合。

---

## 2026-11 回归（README/UI/VD 同步）

### 并行控件树与子 Agent（run 20261109-040803）

| 项 | 结果 |
| --- | --- |
| 启动日志 | `控件树后端：无障碍直连（并行，无锁）` |
| 并行度 | 子任务 A（Display 17）`+3/+7/+9/+13/+15s`，子任务 B（Display 18）`+2/+4/+11/+14s`，动作时间窗交错 |
| 无锁验证 | logcat `AxgentA11yTree` 中 d17/d18 的 dump 时间区间重叠（d17 20.326–20.805、d18 20.762–21.262），无 `tool_cmd.lock` 争用 |
| dump 耗时 | 96.5–544.9ms（改造前 ToolMain 串行 874–977ms） |
| 结果 | A 读到 Android 16 / PLC110_16.0.10.500，B 读到 04:08，父任务 40s 汇总完成 |

### 页面栈与返回（2026-11-10）

| 场景 | 结果 |
| --- | --- |
| 对话页抽屉打开 → 返回 | 关抽屉，仍在对话页 |
| 对话页弹窗/下拉菜单 → 返回 | 关浮层，仍在对话页 |
| 设置 → 关于 → 返回 ×2 | 关于 → 设置 → 对话 |
| 设置 → 模型管理 → 新建服务商 → 返回 ×3 | 新建 → 模型管理 → 设置 → 对话 |
| 对话 → 模型菜单 → 新建连接 → 返回 | 模型管理 → 对话（旧逻辑会错回设置） |
| 抽屉 → 会话/定时任务 → 返回 | 回对话，不退出应用 |
| 会话列表打开会话 → 对话 → 返回 | 退出到桌面（栈重置为 `[对话]`） |
| 旋转屏幕（Activity 重建）后返回 | 仍停留原页面，正确回上一级 |
| 横屏侧栏 → 设置 → 返回 | 回对话，不退出 |

### UI 变更（2026-11-10 / 11）

| 变更 | 验证 |
| --- | --- |
| 标题栏只保留对话标题，移除“本机/运行中”副标题与箭头 | 真机截图确认单行标题 |
| 输入卡移除模型按钮，模型统一从标题栏选择 | 顶部菜单在按钮下方展开，再点收起；思考菜单仍贴输入框 |
| 应用图标替换为盾牌+星芒 | 桌面/关于页显示正常 |

### 模块收敛（2026-11-11）

| 项 | 结果 |
| --- | --- |
| 移除内容（第一轮） | `:inrt`、`:apkbuilder`（AutoX 打包产品线）、`codeeditor/dist.zip`、`assets/template.apk` |
| 移除内容（第二轮） | Javet Node 原生库（`.mjs` 引擎）、ONNX 检测、Google ML Kit OCR、Tesseract、Quickie 扫码；`__globals__.js` 改为按需挂载 `gmlkit` |
| 仓库体积 | 4239 文件 / 98.2MB → 4086 文件 / 70.4MB |
| APK 体积 | 137.04MB → 61.18MB（删除旧产物后重新打包） |
| 构建 | `BUILD SUCCESSFUL`；`app-agent-arm64-v8a-prod.apk` |
| 真机冒烟 | run `20261111-015733`：`父任务虚拟屏就绪：slot 7 · Display 25`，工具集 41 个，`file_list` 正常，10s 完成；logcat 无 `ClassNotFound`/`FATAL` |

### 权限分级与 Shizuku（2026-11-11，真机完成）

| 项 | 结果 |
| --- | --- |
| 新增 | `PrivilegeManager`（Shizuku→root shell）、`ShizukuUserService`、设置页「权限分级」、JS `privilege.js` / `shell-ui.js`；前台 GUI 在无障碍不可用时降级到 ToolMain/input/screencap |
| 构建 | `BUILD SUCCESSFUL`，APK 61.91MB；APK 内 `assets/agent-src/tools/{privilege,shell-ui}.js` 存在 |
| Shizuku 状态 | 授权前 `Shizuku 不可用`，授权后设置页显示 `Shizuku 已连接 · Root 可用`；`com.axgent.agent:service` 以 shell UID 运行 |
| Shizuku 执行 | `PrivilegeManager: exec level=shizuku ok=true cmd=settings get secure accessibility_turn_off_skip_package`；开启无障碍的 `enabled_accessibility_services` 写入同样走 Shizuku |
| Root 执行 | 授权 Shizuku 前，白名单读取日志为 `exec level=root ok=true` |
| 权限弹窗 | 「请求 Shizuku 授权」弹出 Shizuku 确认框，点「始终允许」后服务连接；无需额外 ADB 授权 |
| GUI 降级 | 前台模式 + 诊断开关 `.force-shell-ui`：run `20261111-234429` 共 10 步，`gui_tap` 返回 `via=shell input tap`，目标「关于本机」「版本信息」均命中，最终读到 Android 16 |
| 焦点修复 | shell 后端改用 `dumpsys window` 解析 `mCurrentFocus`；run `20261111-234623` 的 `pkgBefore` 正确为 `com.axgent.agent` / `com.android.settings`，verify=true |
| 恢复验证 | 删除诊断开关、恢复 `virtualDisplay=true` 后 run `20261111-234720`：Display 27，`file_list` 正常，13s 完成 |
| ColorOS 提示 | 白名单已含 `com.axgent.agent`，但**首次启用/重装后仍会弹一次** `AccessibilityGuideCloseActivity`；点「保持开启」后 90s 内未复发。白名单目前看只覆盖周期性提醒，不阻止首次启用提示 |
| 待办 | 若需连首次提示也自动处理，可加「检测到 safecenter 提示时自动点保持开启」的可选开关（需产品确认） |

### 全局权限、自身界面防护与技能私有化（2026-11-12，真机完成）

| 项 | 结果 |
| --- | --- |
| 构建 | `BUILD SUCCESSFUL`；`app-agent-arm64-v8a-prod.apk`；安装 `Success` |
| 全局开关 | 设置 → 运行与安全 → 权限分级 显示全局无障碍/Shizuku/Root 与自动处理弹窗四项开关 |
| 联动（无障碍） | 关闭全局无障碍后 `privilege.accessibility=false`、`capabilities.a11y=false`；重新开启后两者均为 true |
| 联动（Shizuku） | 关闭/开启全局 Shizuku，`capabilities.shizuku` 同步 false/true |
| 联动（Root） | 开启全局 Root 后 `privilege.root=true` 但 `capabilities.root=false`；手动开启 Agent Root 后两者才同时为 true；全局 Root 关闭时 Agent Root 保持 false（代码路径同 Shizuku） |
| Agent 能力面板 | 新增「Agent 无障碍权限」「Agent Shizuku 权限」；全局关闭时置灰；全局 Root 关闭时 Agent Root 行禁用并显示原因 |
| Shizuku 能力 | 无障碍服务未启动时 run `20261112-005509` 走 shell 后端，读取到 `com.android.settings` 的「无障碍」页标题；`app_list`/`notify_read` 已改走受控 shell |
| Root 硬限制 | Agent Root 关闭时工具集不含 `root_shell`，虚拟屏不启动（首页横幅提示原因），任务自动按物理屏运行 |
| 全部权限关闭 | 无障碍/Shizuku/Root 全关时任务在启动前退出，日志写「无障碍服务未开启，且 Shizuku/Root 不可用，无法运行」，未执行任何界面工具 |
| 自身界面 | run `20261112-005734`：Axgent 前台时 Agent 看到「Axgent 自身界面：禁止观察/点击/输入」，拒绝点击 (600,1200)，仅建议返回/启动其他应用 |
| 脚本绕过 | 文件工具拒绝 `/sdcard/phone-agent/src`、私有目录、`/data/local/tmp`；root_shell 固定黑名单覆盖 `settings put secure enabled_accessibility_services`、`pm grant`、`app_process`、写 `ui-config.json` |
| 技能私有化 | 首次启动迁移旧外部技能到 `files/agent/skills`，生成 `.migrated-from-external`；每个技能自动建 `work/`；`skill_load` 返回 `dir=/data/user/0/com.axgent.agent/files/agent/skills/test-import` |
| 技能导出 | 技能页「导出」生成 `/sdcard/Download/Axgent/skills/coloros-settings.zip`，解压内容为 `coloros-settings/SKILL.md` |
| 技能导入 | 系统文件选择器导入 `test-skill.zip` 成功，`test-import/SKILL.md` 与 `work/note.txt` 均落盘 |
| 恶意 zip | 含 `../evil-skill.txt` 的 zip 被拒绝，技能列表不新增、未产生 `evil-skill.txt` |
| 自动处理弹窗 | 开关已持久化到 `ui-config.json.autoHandlePopup`；白名单只含 ColorOS 引导、普通运行时权限、ANR/更新提示，明确排除 Shizuku/Root/无障碍授权弹窗；未在本次真机中触发 ColorOS 首次提示做开/关对比 |
| 待补测 | 1) 真实 ColorOS `AccessibilityGuideCloseActivity` 在开关开/关下的行为对比；2) 有 root 设备上开启全局+Agent Root 后 `root_shell`/VD 的可用性与审计；3) 全局 Root 关闭时通过 UI 关闭 Agent Root 的联动（代码同 Shizuku 已验证分支） |

### 无障碍自动恢复（2026-11-12，真机完成）

| 项 | 结果 |
| --- | --- |
| 新增工具 | `a11y_ensure`：调用 `PrivilegeManager.ensureAccessibilityForAgent()`，要求全局/Agent 无障碍开启且 Agent Shizuku/Root 至少一项开启 |
| 原生闸门 | Agent 恢复时 `allowShizuku=policy.agentShizuku`、`allowRoot=policy.agentRoot`；Agent Root 关闭时不会借全局 Root 走 `su` |
| 看门狗 | Agent 每步观察前检测 `auto.service`；任务开始时无障碍为开、途中掉线才恢复，12s 节流 |
| 弹窗白名单 | ColorOS 安全中心 `AccessibilityGuideCloseActivity` 及 activity/文本含「无障碍」的提示，只点「保持开启/继续开启/保留/确定/知道了」，绝不点「关闭无障碍」 |
| 真机复现 | run `20261112-013912`：任务开始后手动移除 Axgent 无障碍组件，8s 时日志出现「检测到无障碍掉线：尝试用 Shizuku/Root 自动恢复」→「无障碍恢复结果：ok level=shizuku」→「无障碍已恢复连接」；`settings get secure enabled_accessibility_services` 已重新包含 Axgent 组件 |
| 弹窗处理 | 同 run 中 ColorOS 弹出「检测到 Axgent 获取无障碍权限 / 关闭无障碍 / 保持开启」，日志记录「自动处理弹窗：coloros-guide」，任务继续执行 GUI 操作 |
| 工具集 | 前台工具数由 51 增至 52（新增 `a11y_ensure`），Shizuku 开启时可见，Root 关闭时仍不含 `root_shell` |
| 构建 | `BUILD SUCCESSFUL`，安装启动正常 |
