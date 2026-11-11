package org.autojs.autojs.agent

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * 轻量中英双语层。
 *
 * 界面仍以中文文案书写；英文模式通过 Text 包装器按词条翻译。
 * 未收录的长句会保留中文，后续可继续补充 EN 词条。
 */
object AxLang {
    fun resolved(): String {
        val cfg = AgentStore.config.language
        if (cfg == "zh" || cfg == "en") return cfg
        return if (Locale.getDefault().language.startsWith("zh")) "zh" else "en"
    }

    fun isEnglish(): Boolean = resolved() == "en"

    fun t(zh: String, en: String? = null): String {
        if (!isEnglish()) return zh
        if (en != null) return en
        EN[zh]?.let { return it }
        return translatePatterns(zh)
    }

    private fun translatePatterns(s: String): String {
        var out = s
        out = Regex("""权限未完成：(.+)""").replace(out) {
            "Permissions incomplete: " + (EN[it.groupValues[1]] ?: it.groupValues[1])
        }
        out = Regex("""^思考\s*(.+)$""").replace(out) { "Thinking " + (EN[it.groupValues[1]] ?: it.groupValues[1]) }
        out = Regex("""^上下文使用率\s*(.+)$""").replace(out, "Context usage $1")
        out = Regex("""^计时\s*(.+)$""").replace(out) { "Timer " + it.groupValues[1] }
        out = Regex("""第\s*(\d+)\s*步""").replace(out, "Step $1")
        out = Regex("""已选\s*(\d+)\s*段会话""").replace(out, "$1 conversation(s) selected")
        out = Regex("""(\d+)\s*段会话""").replace(out, "$1 conversation(s)")
        out = Regex("""已选\s*(\d+)\s*/\s*(\d+)""").replace(out, "$1/$2 selected")
        out = Regex("""^(\d+)\s*步计划$""").replace(out, "$1-step plan")
        out = Regex("""^(\d+)\s*项待办$""").replace(out, "$1 todo(s)")
        out = Regex("""^(\d+)\s*/\s*(\d+)\s*启用$""").replace(out, "$1 / $2 enabled")
        out = Regex("""^无障碍\s*(.+?)\s*·\s*Shizuku\s*(.+?)\s*·\s*Root\s*(.+)$""").replace(out) {
            "Accessibility ${EN[it.groupValues[1]] ?: it.groupValues[1]} · Shizuku ${EN[it.groupValues[2]] ?: it.groupValues[2]} · Root ${EN[it.groupValues[3]] ?: it.groupValues[3]}"
        }
        out = Regex("""^每\s*(\d+)\s*分钟\s*·\s*下次\s*(.+)$""").replace(out, "Every $1 min · next $2")
        out = Regex("""^每\s*(\d+)\s*分钟\s*·\s*已暂停$""").replace(out, "Every $1 min · paused")
        out = Regex("""^Agent 可自主创建最多\s*(\d+)\s*并行子任务；系统硬上限 6，用户可设不超过 5。$""").replace(
            out,
            "Agent may create up to $1 parallel subtasks; the hard limit is 6 and the user setting is capped at 5."
        )
        out = Regex("""^ColorOS 白名单：(.+)$""").replace(out) {
            "ColorOS allowlist: " + (EN[it.groupValues[1]] ?: it.groupValues[1])
        }
        out = Regex("""^(雾蓝|黛青|暮紫|松石|琥珀|石墨|跟随壁纸|自定义)\s*·\s*(light|dark)$""").replace(out) {
            (EN[it.groupValues[1]] ?: it.groupValues[1]) + " · " + it.groupValues[2]
        }
        out = Regex("""^服务商：\s*(\d+)\s*·\s*对话模型：\s*(\d+)\s*·\s*视觉模型：\s*(\d+)$""").replace(
            out,
            "$1 providers · $2 chat models · $3 vision models"
        )
        out = Regex("""^上下文\s*(.+?)\s*·\s*服务商提供$""").replace(out, "Context $1 · from provider")
        out = Regex("""^上下文\s*(.+?)\s*·\s*估算$""").replace(out, "Context $1 · estimated")
        out = Regex("""(\d+)\s*轮""").replace(out, "$1 turn(s)")
        out = Regex("""(\d+)\s*个网页""").replace(out, "$1 page(s)")
        out = Regex("""(\d+)\s*步失败""").replace(out, "$1 step(s) failed")
        out = Regex("""(\d+)\s*次工具调用""").replace(out, "$1 tool call(s)")
        out = Regex("""(\d+)\s*次""").replace(out, "$1 time(s)")
        out = Regex("""(\d+)\s*条消息""").replace(out, "$1 message(s)")
        out = Regex("""(\d+)\s*回合""").replace(out, "$1 turn(s)")
        out = Regex("""(\d+)\s*步""").replace(out, "$1 step(s)")
        out = Regex("""(\d+)\s*项""").replace(out, "$1 item(s)")
        out = Regex("""(\d+)\s*条""").replace(out, "$1 item(s)")
        out = Regex("""(\d+)\s*个""").replace(out, "$1")
        out = Regex("""(\d+)\s*字""").replace(out, "$1 chars")
        out = Regex("""(\d+)\s*分钟前""").replace(out, "$1 min ago")
        out = Regex("""(\d+)\s*小时前""").replace(out, "$1 h ago")
        out = Regex("""(\d+)\s*天前""").replace(out, "$1 d ago")
        out = Regex("""(.+?)\s*不能为空""").replace(out, "$1 is required")
        out = Regex("""(.+?)\s*失败""").replace(out, "$1 failed")
        out = out.split(" · ").joinToString(" · ") { EN[it] ?: it }
        return out
    }

    private val EN: Map<String, String> = mapOf(
        // 通用
        "保存" to "Save", "取消" to "Cancel", "关闭" to "Close", "完成" to "Done",
        "编辑" to "Edit", "删除" to "Delete", "刷新" to "Refresh", "重试" to "Retry",
        "导出" to "Export", "导入" to "Import", "新建" to "New", "添加" to "Add",
        "菜单" to "Menu", "设置" to "Settings", "技能" to "Skills", "会话" to "Chats",
        "模型" to "Model", "模型管理" to "Models", "外观" to "Appearance",
        "关于" to "About", "版本" to "Version", "包名" to "Package",
        "计时" to "Timer", "思考" to "Thinking", "默认" to "Default",
        "语言" to "Language", "中文" to "中文", "English" to "English",
        "跟随系统" to "System", "浅色" to "Light", "深色" to "Dark",
        "配色" to "Accent", "自定义" to "Custom", "跟随壁纸取色" to "Wallpaper color",
        "深色与浅色" to "Theme", "文字大小" to "Text size", "轮次折叠" to "Fold turns",
        "深浅" to "Theme", "阅读" to "Reading", "聊天字号" to "Chat text size",
        "小" to "Small", "标准" to "Standard", "大" to "Large", "特大" to "Extra large",
        "折叠已完成轮次" to "Fold completed turns", "当前生效" to "Active",
        "雾蓝" to "Mist", "黛青" to "Slate", "暮紫" to "Dusk", "松石" to "Turquoise",
        "琥珀" to "Amber", "石墨" to "Graphite",
        "配色只出现在主按钮、发送键、选中态与链接上，其余是中性纸面。" to
            "Accent appears on primary buttons, send, selection and links; everything else stays neutral.",
        "从系统壁纸取样，自动推出一套同明度的配色" to
            "Sample the wallpaper and derive a palette with matching luminance.",
        "把上一轮的工具过程收成一行摘要，点开可看完整过程" to
            "Collapse the previous turn's tool run into one line; tap to expand.",
        // 首页 / 菜单
        "＋ 新对话" to "+ New chat", "＋ 新建" to "+ New",
        "最近会话" to "Recent chats", "全部会话" to "All chats",
        "历史会话" to "History", "定时任务" to "Schedules",
        "配色、深浅、文字大小、轮次折叠" to "Accent, theme, text size, turn folding",
        "SKILL.md 技能库与启停" to "SKILL.md library and toggles",
        "步数、审批、虚拟屏与续跑" to "Steps, approvals, virtual display and resume",
        "搜索源与抓取方式" to "Search providers and fetching",
        "对话与视觉模型、服务商连接" to "Chat/vision models and providers",
        "文件工具允许操作的根目录" to "Allowed sandbox roots",
        "版本、构建与运行环境" to "Version, build and runtime",
        "输入任务，或点上方示例" to "Enter a task, or pick an example above",
        "发送" to "Send", "停止" to "Stop", "暂停" to "Pause", "继续" to "Resume",
        "排队中 · 本轮结束后发送" to "Queued · sent after this turn",
        "立即发送" to "Send now", "撤回" to "Undo",
        "本轮环境注明" to "Environment notes",
        // 能力
        "能力" to "Capabilities",
        "控制下一次任务的工具与观察能力；运行中的任务不改变。" to
            "Controls tools and observations for the next task; running tasks are unchanged.",
        "Agent 无障碍权限" to "Agent accessibility",
        "Agent Shizuku 权限" to "Agent Shizuku",
        "Agent Root权限" to "Agent Root",
        "屏幕读取" to "Screen reading", "文件读取" to "File reading",
        "主模型视觉" to "Main model vision", "视觉模型视觉" to "Vision model vision",
        "GUI 操作" to "GUI actions", "文件编辑" to "File editing",
        "沙箱内高风险操作" to "High-risk sandbox ops",
        "沙箱外文件操作" to "Outside sandbox",
        "设备与通知" to "Device and notifications", "剪贴板" to "Clipboard",
        "规划与待办" to "Planning and todos",
        "禁止操作 Axgent 自身界面（硬限制）" to "Axgent UI operations are blocked (hard limit)",
        // 运行与安全
        "运行与安全" to "Runtime & security",
        "后台虚拟屏" to "Background virtual display",
        "后台虚拟屏（需要 root）" to "Background virtual display (root)",
        "崩溃/被杀后自动续跑" to "Auto-resume after crash",
        "沙箱与回收站" to "Sandbox and trash",
        "权限分级" to "Permission tiers",
        "全局无障碍权限" to "Global accessibility",
        "全局 Shizuku 权限" to "Global Shizuku",
        "全局 Root 权限" to "Global Root",
        "自动处理弹窗" to "Auto-handle dialogs",
        "加入白名单" to "Add to allowlist",
        "请求 Shizuku 授权" to "Request Shizuku",
        "操作优先无障碍；需要 shell 时先走 Shizuku，最后才用 root。白名单用于跳过 ColorOS 的「关闭无障碍」提醒。" to
            "Accessibility first, then Shizuku, then root. The allowlist skips ColorOS accessibility reminders.",
        // 技能
        "导入目录" to "Import folder", "导入 ZIP" to "Import ZIP",
        "技能正文只在模型调用 skill_load 时进入上下文；关闭的技能不会出现在技能目录里。" to
            "Skill bodies enter context only when skill_load is called; disabled skills are hidden.",
        "技能已创建" to "Skill created", "技能已保存" to "Skill saved",
        "技能已删除" to "Skill deleted", "技能已导入" to "Skill imported",
        "删除技能" to "Delete skill", "新建技能" to "New skill", "编辑技能" to "Edit skill",
        "name（名称）" to "name", "description（何时使用）" to "description (when to use)",
        "SKILL.md 正文" to "SKILL.md body",
        // 会话
        "搜索会话" to "Search chats", "管理" to "Manage", "全选" to "Select all",
        "取消全选" to "Clear selection", "删除" to "Delete",
        "还没有会话" to "No chats yet", "没有匹配的会话" to "No matching chats",
        "发送第一个任务后，会话会出现在这里。" to "Your first task will appear here.",
        "换个关键词试试。" to "Try another keyword.",
        // 引导 / 权限
        "权限未完成" to "Permissions incomplete", "去设置" to "Open settings",
        "已开启" to "On", "未开启" to "Off", "已连接" to "Connected",
        "不可用" to "Unavailable", "可用" to "Available",
        "无障碍" to "Accessibility", "所有文件访问" to "All files access",
        "电池优化" to "Battery optimization", "通知" to "Notifications",
        "悬浮窗" to "Overlay", "开始使用" to "Get started",
        "下一步" to "Next", "上一步" to "Back",
        // 关于 / 诊断
        "诊断与日志" to "Diagnostics & logs",
        "当前状态" to "Current status", "配置" to "Config",
        "运行目录" to "Run directory", "当前模型" to "Current model",
        "搜索源" to "Search provider", "数据位置" to "Data locations",
        "脚本" to "Script", "构建" to "Build", "复制配置 JSON" to "Copy config JSON",
        "复制运行目录" to "Copy run path", "当前主题" to "Theme",
        // 常见提示
        "加载中…" to "Loading…", "处理中" to "Working", "超时" to "Timeout",
        "失败" to "Failed", "成功" to "Success", "已停止" to "Stopped",
        "等待确认" to "Waiting for confirmation", "任务完成" to "Task complete",
        "任务结束" to "Task finished", "暂无技能" to "No skills yet",
        "输入框为空" to "Input is empty", "复制成功" to "Copied",
        "复制失败" to "Copy failed", "保存失败" to "Save failed",
        "启用" to "enabled", "停用" to "disabled", "已停用" to "Disabled",
        "已暂停" to "Paused", "已完成" to "Done", "运行中" to "Running",
        "今天" to "Today", "昨天" to "Yesterday", "本周" to "This week",
        "更早" to "Earlier", "刚刚" to "Just now", "未检测" to "Not checked",
        // 首页
        "新对话" to "New chat", "今天要做什么？" to "What should we do today?",
        "直接说任务，Axgent 会自己看屏幕、点按钮、读写文件。" to
            "Just describe the task; Axgent will read the screen, tap and edit files on its own.",
        "给 Axgent 发消息…" to "Message Axgent…",
        "给 Axgent 发消息…（排队，本轮结束后发送）" to
            "Message Axgent… (queued, sent after this turn)",
        "操作界面" to "Interact with UI", "查看当前界面" to "View current screen",
        "视觉" to "Vision", "对话" to "Chat",
        "后台任务进度、暂停与终止" to "Background progress, pause and stop",
        "模型、能力与外观；修改后写入 ui-config.json，下次任务生效。" to
            "Models, capabilities and appearance; saved to ui-config.json and applied to the next task.",
        "版本、包名与构建信息" to "Version, package and build info",
        "技能库" to "Skill library", "通用" to "General", "外观与主题" to "Appearance & theme",
        // 外观
        "暗色、文字与玻璃会按同一明度自动推导。" to
            "Dark surfaces, text and glass are derived from one luminance scale.",
        "跟随壁纸" to "Wallpaper", "自定义配色" to "Custom accent",
        "颜色（#RRGGBB）" to "Color (#RRGGBB)",
        "配色会立即应用并写入 ui-config.json。切换时会全局淡入淡出。" to
            "Accent applies immediately and is saved to ui-config.json; switching cross-fades.",
        // 能力说明
        "读取界面控件并执行点击、滑动、输入；GUI 任务必需。" to
            "Read UI nodes and tap, swipe, type; required for GUI tasks.",
        "读取/写入手机文件、保存运行日志和技能；文件任务必需。" to
            "Read/write files and save logs and skills; required for file tasks.",
        "控件树、OCR、等待文字；关闭后 Agent 看不到界面" to
            "Node tree, OCR and text waiting; the Agent is blind without it",
        "允许主模型直接读图；视觉模型不可用时作为兜底" to
            "Let the main model read images; fallback when no vision model is set",
        "配置了视觉模型时优先使用；关闭后视觉只走主模型" to
            "Prefer the vision model when configured; otherwise vision uses the main model",
        "两项都关闭时视觉能力关闭：look 与自动看屏不可用；OCR 仍归屏幕读取。" to
            "Vision is off when both are disabled: look and auto screen reads stop; OCR still counts as screen reading.",
        "点击、输入、滑动、返回、桌面、启动 App" to
            "Tap, type, swipe, back, home and launch apps",
        "读取、列出、搜索、查看文件信息" to "Read, list, search and inspect files",
        "写入、追加、替换、建目录、复制" to "Write, append, replace, mkdir and copy",
        "删除、移动、批量；仅限沙箱根目录，默认开启" to
            "Delete, move and batch; sandbox roots only, on by default",
        "读写、删除、列目录、搜索沙箱外路径；默认关闭，代码强制校验" to
            "Read, write, delete and search outside the sandbox; off by default, enforced in code",
        "设备信息、应用列表、读取通知" to "Device info, app list and notifications",
        "读取与写入剪贴板" to "Read and write the clipboard",
        "计划、清单、关键事实记录" to "Plans, todo lists and key facts",
        "批量文件操作" to "Batch file operations",
        "允许 Agent 使用无障碍观察与操作界面；全局无障碍关闭时置灰" to
            "Allow the Agent to observe and operate the UI; greyed out when global accessibility is off",
        "允许 Agent 通过 Shizuku 执行 shell；全局 Shizuku 关闭时置灰" to
            "Allow the Agent to run shell via Shizuku; greyed out when global Shizuku is off",
        "允许 root_shell 等工具以 root 执行；关闭时在工具执行层硬拦截，不依赖提示词" to
            "Allow root_shell and similar tools to run as root; blocked in the tool layer, not by prompts",
        // 运行与安全
        "关闭后 App 与 Agent 都不使用无障碍观察/操作界面" to
            "When off, neither the app nor the Agent uses accessibility to observe or operate the UI",
        "关闭后任何模块都不得通过 Shizuku 执行 shell" to
            "When off, no module may run shell through Shizuku",
        "关闭后任何 su/root 调用都会被硬拦截；开启后 Agent Root 仍需单独开启" to
            "When off, all su/root calls are blocked; Agent Root still needs its own switch",
        "自动处理 ColorOS 无障碍提示、ANR 等白名单系统弹窗；不会自动授予 Shizuku/Root/无障碍权限" to
            "Auto-handle allowlisted ColorOS accessibility reminders and ANR dialogs; never auto-grants Shizuku/Root/accessibility",
        "用 Shizuku / root 开启无障碍" to "Enable accessibility via Shizuku / root",
        "后台虚拟屏（需要 root）" to "Background virtual display (root required)",
        "使用 App 内置运行时在 Display > 0 后台跑 App；重装后需重新授权 root。" to
            "Run apps on Display > 0 with the built-in runtime; re-authorize root after reinstall.",
        "Agent Root 权限未开启（或全局 Root 已关闭），无法启动虚拟屏" to
            "Agent Root is off (or global Root is off); the virtual display cannot start",
        "Agent Root 未开启：本次按物理屏运行" to
            "Agent Root is off: running on the physical screen this time",
        "Root 权限未开启，虚拟屏将在任务中跳过" to
            "Root is off; the virtual display will be skipped",
        "Root 权限已开启：模型可直接执行任意 root 命令，请只在可信任务中开启。" to
            "Root is on: the model can run any root command; enable only for trusted tasks.",
        "避免系统在后台或息屏后冻结 Agent。" to
            "Keep the system from freezing the Agent in the background or with the screen off.",
        "下次打开 App 时从 checkpoint 续跑一次；用户主动停止不会续跑。" to
            "Resume once from the checkpoint when the app reopens; manual stops do not resume.",
        "最大步数（1–100）" to "Max steps (1–100)",
        "最长运行分钟（1–60）" to "Max minutes (1–60)",
        "需要确认的工具（逗号分隔）" to "Tools requiring approval (comma separated)",
        "默认空 = 全自动。确认卡会暂停 Agent，在输入框上方显示允许/拒绝。" to
            "Empty means fully automatic. Approval cards pause the Agent above the input box.",
        "后台最大虚拟屏数量（1–5）" to "Max virtual displays (1–5)",
        "需要悬浮窗权限才能打开后台预览" to "Overlay permission is required for the background preview",
        "悬浮窗预览" to "Overlay preview", "流体云实时活动" to "Live capsule",
        "任务后台运行时用悬浮窗看实时画面；不开启不影响任务本身。" to
            "Watch the live screen in an overlay while a task runs; not required for the task itself.",
        "退到后台时在顶部胶囊显示当前动作" to
            "Show the current action in the status capsule while in the background",
        // 模型
        "暂无模型" to "No models yet",
        "暂无对话模型，点击添加模型开始使用" to
            "No chat model yet; add one to get started",
        "添加模型" to "Add model", "新建模型连接" to "New model connection",
        "服务商" to "Provider", "对话模型" to "Chat model", "视觉模型" to "Vision model",
        "模型用途" to "Model usage", "选择模型" to "Select model",
        "添加服务商" to "Add provider", "API 地址" to "API URL", "API 密钥" to "API key",
        "请输入 Base URL" to "Enter the base URL",
        "请先填写 API 地址与 API 密钥" to "Enter the API URL and key first",
        "无法获取模型列表" to "Could not fetch the model list",
        "服务商未返回可用模型" to "The provider returned no usable models",
        "正在获取模型列表…" to "Fetching models…",
        "搜索模型 ID 或名称" to "Search model ID or name",
        "搜索模型平台" to "Search model platforms",
        "添加所选模型" to "Add selected models",
        "已添加服务商" to "Provider added",
        "服务商配置已保存" to "Provider saved",
        "模型列表来自服务商接口。" to "The model list comes from the provider API.",
        "请选择至少一个模型" to "Select at least one model",
        "删除模型" to "Delete model", "删除服务商" to "Delete provider",
        "删除服务商？" to "Delete provider?",
        "将同时删除该服务商下的对话与视觉模型，且无法恢复。" to
            "This also deletes its chat and vision models; it cannot be undone.",
        "服务商名称" to "Provider name", "提供商名称" to "Provider name",
        "提供商类型" to "Provider type",
        "服务商已停用" to "Provider disabled", "服务商已不存在，请重新选择" to
            "Provider no longer exists; choose another",
        "请输入提供商名称" to "Enter a provider name",
        "请输入有效的 HTTP 或 HTTPS 地址" to "Enter a valid HTTP or HTTPS URL",
        "API 密钥不能为空" to "API key is required",
        "搜索 Provider 预设" to "Search provider presets",
        "从 Provider 预设创建…" to "Create from a provider preset…",
        "从预设创建" to "Create from preset", "从预设创建（可选）" to "Create from preset (optional)",
        "不使用预设" to "No preset", "跳过" to "Skip",
        "仅保存服务商配置（不添加模型）" to "Save provider only (no models)",
        "选择服务商后填写连接信息；对话与视觉模型共用同一套服务商配置。" to
            "Pick a provider and fill in the connection; chat and vision share the same provider config.",
        "对话模型和视觉模型共用同一套服务商连接；模型库可随时切换当前模型。" to
            "Chat and vision models share provider connections; switch the active model any time.",
        "内置服务商" to "Built-in provider", "服务商提供" to "Provided by",
        "更换服务商" to "Change provider", "未经检测" to "Not tested",
        // 会话与定时
        "搜索会话" to "Search chats", "没有匹配的设置项" to "No matching settings",
        "搜索设置" to "Search settings",
        "删除定时任务" to "Delete schedule", "新建定时任务" to "New schedule",
        "还没有定时任务" to "No schedules yet",
        "间隔（分钟）" to "Interval (minutes)", "任务内容" to "Task",
        "新建一个间隔任务，Axgent 会在后台按时执行。" to
            "Create a recurring task; Axgent runs it in the background on schedule.",
        "到达间隔后自动执行一次任务（沿用当前对话）；同一时间只跑一个任务，冲突时跳过本次。" to
            "Runs one task each interval in the current conversation; overlapping runs are skipped.",
        "请在系统设置中允许「闹钟和提醒」" to "Allow alarms and reminders in system settings",
        "未获得精确闹钟权限，触发时间可能被系统延后。点「新建」时会跳转到授权页。" to
            "Exact-alarm permission is missing, so runs may be delayed. New opens the permission page.",
        "将删除这些会话的全部运行记录与对话内容。此操作不可恢复。" to
            "This deletes all runs and messages in these chats; it cannot be undone.",
        "任务运行中，暂不能删除会话" to "Cannot delete a chat while a task is running",
        // 技能
        "已沉淀技能" to "Auto-created skills",
        "新建一个 SKILL.md，或导入包含 SKILL.md 的目录。" to
            "Create a SKILL.md or import a folder containing one.",
        "技能名称" to "Skill name", "技能描述" to "Skill description",
        "技能正文" to "Skill body",
        "输入一个包含 SKILL.md 的目录路径；会复制到本机技能库，不改动原目录。" to
            "Enter a folder containing SKILL.md; it is copied into the local library, the original untouched.",
        "删除技能失败" to "Failed to delete skill",
        "导入技能" to "Import skill", "导入中…" to "Importing…",
        "导入技能失败" to "Failed to import skill", "导入失败" to "Import failed",
        "导出失败" to "Export failed",
        "名称" to "Name", "描述" to "Description", "正文" to "Body",
        // 关于 / 诊断
        "关于 Axgent" to "About Axgent",
        "APK 内置 agent-src：首次安装或升级时自动部署到脚本目录，设备上的 config/local.js 不会被覆盖。" to
            "agent-src is bundled: it is deployed on first install or upgrade; a device-side config/local.js is never overwritten.",
        "提示：每次运行目录下都有 main.log、steps.jsonl、stream.jsonl、checkpoint.json。" to
            "Each run directory contains main.log, steps.jsonl, stream.jsonl and checkpoint.json.",
        "文件操作约定" to "File conventions",
        "配置、运行目录与日志路径" to "Config, run directory and log paths",
        "排障时优先看 main.log；每次运行都有 steps / stream / checkpoint。" to
            "Start with main.log when troubleshooting; each run has steps, stream and checkpoint files.",
        "路径已复制" to "Path copied", "运行目录已复制" to "Run directory copied",
        "配置 JSON 已复制" to "Config JSON copied", "已复制" to "Copied",
        // 引导与提示
        "首次使用需要几项系统权限。缺权限时仍可进入，但任务可能无法观察界面、读写文件或在后台继续。" to
            "Axgent needs a few system permissions. You can enter without them, but tasks may not see the screen, read/write files or keep running in the background.",
        "建议先开启「无障碍服务」和「所有文件访问」，否则任务成功率会很低。" to
            "Turn on Accessibility service and All files access first; otherwise most tasks will fail.",
        "随时可以在设置里调整；这里只做引导，不会上传权限数据。" to
            "You can change these later in Settings; this is only onboarding and uploads nothing.",
        "准备 Axgent" to "Preparing Axgent", "系统权限" to "System permissions",
        "所需权限已关闭" to "Required permissions are off",
        "无障碍服务" to "Accessibility service",
        "全文件访问" to "All files access",
        "电池优化白名单" to "Battery optimization allowlist",
        "通知权限" to "Notification permission",
        "稍后设置，先进入" to "Set up later", "完成并进入" to "Finish and enter",
        "去开启" to "Turn on", "去授权" to "Authorize",
        "需要你的确认" to "Confirmation needed", "等待人工接管" to "Waiting for you",
        "已请求；请在 Shizuku 弹窗中允许" to "Requested; allow it in the Shizuku dialog",
        "已通过 Shizuku/root 开启无障碍" to "Accessibility enabled via Shizuku/root",
        "开启失败：请安装并授权 Shizuku，或允许 root，之后再试" to
            "Failed: install and authorize Shizuku, or allow root, then retry",
        "内容为空，无法发送" to "Nothing to send",
        "仍然进入" to "Enter anyway",
        "至少保留一个沙箱根目录" to "Keep at least one sandbox root",
        "沙箱目录已保存" to "Sandbox roots saved",
        "沙箱目录必须是绝对路径，例如 /sdcard/Download/phone-agent" to
            "Sandbox roots must be absolute, e.g. /sdcard/Download/phone-agent",
        "沙箱目录不能包含 .. 或换行" to "Sandbox roots cannot contain .. or newlines",
        "沙箱外文件操作已开启：路径不再受沙箱 roots 保护，请确认你真的要允许。" to
            "Outside-sandbox access is on: paths are no longer protected by sandbox roots.",
        "保存到 ui-config.json 的 sandbox.roots；系统路径（runs、conversations、src）仍禁止写删。" to
            "Saved to sandbox.roots in ui-config.json; system paths (runs, conversations, src) stay read-only.",
        "文件工具限制在沙箱 roots；删除先进入 runs/.trash。" to
            "File tools are limited to sandbox roots; deletions go to runs/.trash first.",
        "暂无视觉模型；视觉查看会使用主模型或 OCR" to
            "No vision model; vision uses the main model or OCR",
        "未配置模型" to "No model configured", "未配置" to "Not configured",
        "未完成" to "Incomplete", "未知" to "Unknown",
        "全部" to "All", "收起" to "Collapse", "展开" to "Expand",
        "预算" to "Budget", "估算" to "Estimate", "调用" to "Call",
        "主模型视觉" to "Main model vision", "视觉模型视觉" to "Vision model vision",
        "联网搜索" to "Web search", "沙箱目录" to "Sandbox folders",
        "全自动" to "Fully automatic",
        "v1 · Compose · 内核 main.js" to "v1 · Compose · core main.js",
        "AutoX.js v7 · Compose · 内核 main.js" to "AutoX.js v7 · Compose · core main.js",
        "单任务的步数与时长、需要确认的工具、后台虚拟屏与续跑。" to
            "Steps and duration, approval tools, virtual display and resume.",
        "全局 Root 或 Agent Root 未开启：任务会按物理屏运行，虚拟屏不会启动。" to
            "Global Root or Agent Root is off: tasks run on the physical screen and the virtual display stays off.",
        "网页搜索与正文抓取" to "Web search and page fetching",
        "全局 Root 权限未开启，需先在「运行与安全 → 权限分级」中开启" to
            "Global Root is off; enable it under Runtime & security → Permission tiers",
        "已加入" to "Added", "未加入" to "Not added", "检测中…" to "Checking…",
        "ColorOS 白名单：已加入" to "ColorOS allowlist: added",
        "ColorOS 白名单：未加入" to "ColorOS allowlist: not added",
        "ColorOS 白名单：检测中…" to "ColorOS allowlist: checking…",
        "ColorOS 白名单：读取失败" to "ColorOS allowlist: read failed",
        "运行数据" to "Runtime data",
        "＋ 添加" to "+ Add", "当前" to "Active", "刚刚检测" to "Checked just now",
        "服务商提供" to "from provider",
        "Cherry 风格 SKILL.md：Agent 只看到已启用技能的名称与描述，匹配时用 skill_load 读取正文。" to
            "Cherry-style SKILL.md: the Agent sees only the names and descriptions of enabled skills, and calls skill_load to read the body when matched.",
        "搜索、切换或批量删除历史会话" to "Search, switch or batch-delete past chats",
        "创建" to "Create", "附件" to "Attach", "图片" to "Image", "文件" to "File"
    )
}

/**
 * 自动翻译的 Text 包装器。
 *
 * 所有 agent 包内的 Compose 文件不再直接 import Material3 Text，
 * 而是使用这个同包函数，从而在英文模式下统一翻译文案。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    M3Text(
        text = AxLang.t(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

/** Markdown / 富文本保持原始 AnnotatedString，不强行翻译。 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    M3Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style
    )
}
