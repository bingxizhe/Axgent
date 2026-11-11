package org.autojs.autojs.agent

import android.content.Context
import com.stardust.app.GlobalAppContext
import java.io.File

enum class AgentStatus { IDLE, RUNNING, PAUSED, WAITING, DONE, FAILED, STOPPED }

data class StepItem(
    val step: Int,
    val tool: String,
    val thought: String,
    val ok: Boolean,
    val err: String,
    val args: String = "",
    val preview: String = "",
    val result: String = "",
    val pkg: String = "",
    val changed: Boolean = false,
    val reasoning: String = ""
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val step: StepItem? = null,
    val animate: Boolean = false,
    val sources: List<String> = emptyList(),
    /** 本轮最后一次请求的 prompt tokens，仅 FINAL 使用。 */
    val promptTokens: Int = 0,
    /** 本轮模型上下文窗口，仅 FINAL 使用。 */
    val contextWindow: Int = 0
) {
    enum class Role { USER, SAY, STEP, FINAL, NOTICE, COMPACTION, RETRY }
}

data class TodoItem(val content: String, val status: String)

data class ApprovalRequest(
    val id: String,
    val tool: String,
    val args: String,
    val task: String,
    val step: Int
)

data class RunSummary(
    val id: String,
    val dir: File,
    val task: String,
    val done: Boolean,
    val steps: Int,
    val result: String,
    val modified: Long
)

/** 服务商：对话模型与视觉模型共用的连接配置。 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    /** openai | responses | anthropic | gemini | custom */
    val protocol: String = "openai",
    val presetId: String = "",
    val custom: Boolean = false,
    val enabled: Boolean = true
)

data class ModelProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val verified: Boolean = true,
    val providerId: String = "",
    val providerName: String = "",
    /** openai | responses | anthropic | gemini | custom */
    val protocol: String = "openai",
    val lastCheckedAt: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val lastMessage: String = "",
    /** 服务商返回的上下文窗口（tokens）；0 = 未知。 */
    val contextWindow: Int = 0,
    /** provider | estimated */
    val contextSource: String = ""
)

/** 全局权限分级：三类权限各自独立，关闭后任何模块都不得使用该层。 */
data class PrivilegeConfig(
    val accessibility: Boolean = true,
    val shizuku: Boolean = true,
    val root: Boolean = false
)

data class AgentCapabilities(
    val screenRead: Boolean = true,
    val fileRead: Boolean = true,
    /** 主模型视觉权限：允许主模型直接读图，也是视觉模型的兜底。 */
    val mainVision: Boolean = true,
    /** 视觉模型视觉权限：配置了视觉模型时优先使用。 */
    val visionModelVision: Boolean = true,
    val web: Boolean = true,
    val gui: Boolean = true,
    val fileEdit: Boolean = true,
    val sandboxHighRisk: Boolean = true,
    val outsideSandbox: Boolean = false,
    val device: Boolean = true,
    val clipboard: Boolean = true,
    val plan: Boolean = true,
    /** Agent 无障碍权限：关闭后不得使用无障碍观察/操作界面。 */
    val a11y: Boolean = true,
    /** Agent Shizuku 权限：关闭后任何工具都不得通过 Shizuku 执行 shell。 */
    val shizuku: Boolean = true,
    /** Agent Root 权限：开启后允许 root_shell 执行任意系统命令；关闭时工具层硬拦截。 */
    val root: Boolean = false
) {
    /** 兼容旧 UI/旧配置：任一视觉权限开启即视为视觉可用。 */
    val vision: Boolean get() = mainVision || visionModelVision
}

data class SkillEntry(
    val folder: String,
    val name: String,
    val description: String,
    val body: String,
    val enabled: Boolean = true
)

data class UiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val visionModel: String = "",
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val chatModels: List<ModelProfile> = emptyList(),
    val visionModels: List<ModelProfile> = emptyList(),
    val providers: List<ProviderProfile> = emptyList(),
    val activeModelId: String = "",
    val activeVisionModelId: String = "",
    val thinking: String = "default",
    val theme: String = "system",
    /** DSH 配色：mist | daiqing | dusk | turquoise | amber | graphite | custom | wallpaper */
    val accentId: String = "mist",
    val customAccent: String = "#4B67B4",
    val wallpaperAccent: Boolean = false,
    /** 聊天正文字号倍率（DSH 建议 0.85–1.3）。 */
    val chatTextScale: Float = 1f,
    /** 已完成轮次折叠成一行摘要。 */
    val foldCompletedTurns: Boolean = true,
    val capabilities: AgentCapabilities = AgentCapabilities(),
    /** 全局权限分级开关；关闭后 Agent 对应权限自动关闭。 */
    val privilege: PrivilegeConfig = PrivilegeConfig(),
    /** 自动处理弹窗：仅处理白名单系统弹窗，不自动授予敏感权限。 */
    val autoHandlePopup: Boolean = true,
    val sandboxRoots: List<String> = listOf("/sdcard/Download/phone-agent", "/sdcard/Documents"),
    val disabledSkills: List<String> = emptyList(),
    val onboarded: Boolean = false,
    val virtualDisplay: Boolean = false,
    val autoResume: Boolean = true,
    /** 后台最大虚拟屏数量（用户可设 1-5；系统硬上限 6，仅内部/配置可直接使用）。 */
    val maxParallelDisplays: Int = 2,
    val maxSteps: Int = 25,
    val maxMinutes: Int = 10,
    val approvalTools: String = "",
    val webProvider: String = "auto",
    val webMaxResults: Int = 6,
    val webExcludeDomains: String = "",
    val webFetchProvider: String = "auto",
    val allowPrivateUrls: Boolean = false
)

object AgentPaths {
    val root = File("/sdcard/Download/phone-agent")
    val runsDir = File(root, "runs")
    val taskFile = File(root, "task.txt")
    val controlFile = File(root, "control.txt")
    val approvalFile = File(root, "approval.json")
    val approvalAnswerFile = File(root, "approval.txt")
    val resumeFile = File(root, "resume.txt")
    val conversationFile = File(root, "conversation.txt")
    val configFile = File(root, "ui-config.json")
    val scriptFile = File("/sdcard/phone-agent/src/main.js")
    val skillsDir = File("/sdcard/phone-agent/src/skills")
    /** 旧版技能库（外部可写，仅用于一次性迁移）。 */
    val legacySkillsLibraryDir = File(root, "skills")
    val previewFile = File(root, ".preview.png")
    val previewMetaFile = File(root, ".preview.json")
    val previewFlagFile = File(root, ".preview.on")
    val previewSuppressFile = File(root, ".preview.suppress")
    val pausedFile = File(root, "paused.txt")

    @Volatile
    private var privateRootDir: File? = null

    /** 应用私有数据根目录：外部（含 shell 用户）无法直接修改。 */
    val privateRoot: File
        get() {
            privateRootDir?.let { return it }
            val dir = File(GlobalAppContext.get().filesDir, "agent")
            dir.mkdirs()
            privateRootDir = dir
            return dir
        }

    /** 技能库：应用私有目录，防止用户从外部直接改 SKILL.md。 */
    val skillsLibraryDir: File get() = File(privateRoot, "skills")

    /** 技能导出目录：用户可见的 Downloads。 */
    val skillExportsDir: File get() = File("/sdcard/Download/Axgent/skills")

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, "agent")
        dir.mkdirs()
        privateRootDir = dir
        skillsLibraryDir.mkdirs()
    }
}
