package org.autojs.autojs.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import com.stardust.autojs.servicecomponents.EngineController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * AgentStore —— UI 侧唯一的数据源。
 *
 * 只通过文件与 JS 内核通信：
 *   task.txt / control.txt / resume.txt / ui-config.json
 *   runs/<id>/steps.jsonl / checkpoint.json / main.log
 */
/** 用户随任务附带的图片/文件（已复制到 attachments 目录）。 */
data class AgentAttachment(
    val path: String,
    val name: String,
    val mime: String
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/** Agent 主动提问（ask_user），等待用户回答。 */
data class AgentQuestion(
    val id: String,
    val question: String,
    val options: List<String> = emptyList()
)

object AgentStore {

    var status by mutableStateOf(AgentStatus.IDLE); private set
    var lastError by mutableStateOf<String?>(null); private set
    /** 虚拟屏运行时错误（root 未授权、网关未启动等），供首页横幅展示。 */
    var vdError by mutableStateOf<String?>(null); private set
    val messages = mutableStateListOf<ChatMessage>()
    val runs = mutableStateListOf<RunSummary>()
    var config by mutableStateOf(UiConfig()); private set
    var currentRunId by mutableStateOf<String?>(null); private set
    var totalSteps by mutableStateOf(0); private set
    var currentTool by mutableStateOf(""); private set
    var elapsedSec by mutableStateOf(0); private set
    var liveText by mutableStateOf(""); private set
    var liveReasoning by mutableStateOf(""); private set
    var liveRetry by mutableStateOf(""); private set
    var pendingApproval by mutableStateOf<ApprovalRequest?>(null); private set
    var liveActive by mutableStateOf(false); private set
    var conversationId by mutableStateOf(""); private set
    var pendingTask by mutableStateOf<String?>(null); private set
    var todos by mutableStateOf<List<TodoItem>>(emptyList()); private set
    var planSteps by mutableStateOf<List<String>>(emptyList()); private set
    var pendingAttachment by mutableStateOf<AgentAttachment?>(null); private set
    var pendingQuestion by mutableStateOf<AgentQuestion?>(null); private set
    /** DSH 式排队：运行中发送的消息排到本轮结束后，可“立即发送”转为插话。 */
    var queuedMessage by mutableStateOf<String?>(null); private set

    private var lastStepsSize = -1L
    private var cachedDirs: List<File> = emptyList()
    private var lastDirsScan = 0L
    private var lastScheduleSyncCheck = 0L
    private var lastBrowserCheck = 0L
    private var lastBrowserCmd = ""
    private var lastAskCheck = 0L
    private val convIdCache = mutableMapOf<String, String>()
    private var startedAt = 0L
    private var pendingStartedAt = 0L
    private var stopRequested = false
    private var pausedByUser = false
    /** 当前进程内是否真的拉起过脚本；进程重启后为 false，用于识别残留副屏。 */
    private var scriptLaunched = false
    private var viewingRunId: String? = null
    private var appContext: Context? = null
    private val seen = mutableSetOf<String>()
    private var baselineLoaded = false
    private var runWasDoneAtLoad = false
    private var legacyMode = false

    fun init(context: Context) {
        appContext = context.applicationContext
        AgentPaths.init(context)
        ensureConversation()
        // 进程重启后不要把上一次遗留的 browser 命令再执行一遍。
        try {
            val cmd = File(AgentPaths.root, "browser/cmd.json")
            if (cmd.exists()) lastBrowserCmd = JSONObject(cmd.readText()).optString("id", "")
        } catch (_: Exception) {
        }
    }

    private fun ensureConversation(): String {
        if (conversationId.isNotEmpty()) return conversationId
        return try {
            if (AgentPaths.conversationFile.exists()) {
                val id = AgentPaths.conversationFile.readText().trim()
                if (id.isNotEmpty()) {
                    conversationId = id
                    legacyMode = (id == "c-legacy")
                    return id
                }
            }
            val hasRuns = AgentPaths.runsDir.listFiles { f -> f.isDirectory }?.isNotEmpty() == true
            val id = if (hasRuns) "c-legacy" else "c-" + System.currentTimeMillis()
            AgentPaths.root.mkdirs()
            AgentPaths.conversationFile.writeText(id)
            conversationId = id
            legacyMode = hasRuns
            id
        } catch (e: Exception) {
            conversationId = "c-fallback"
            conversationId
        }
    }

    fun newConversation() {
        if (status == AgentStatus.RUNNING || status == AgentStatus.PAUSED || status == AgentStatus.WAITING) {
            control("stop")
        }
        val id = "c-" + System.currentTimeMillis()
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.conversationFile.writeText(id)
        } catch (e: Exception) {
            lastError = e.message
        }
        conversationId = id
        legacyMode = false
        pendingTask = null
        pendingStartedAt = 0
        stopRequested = false
        pausedByUser = false
        pendingAttachment = null
        queuedMessage = null
        todos = emptyList()
        planSteps = emptyList()
        messages.clear()
        clearLive()
        pendingApproval = null
        currentRunId = null
        viewingRunId = null
        lastStepsSize = -1
        seen.clear()
        baselineLoaded = false
        runWasDoneAtLoad = false
        status = AgentStatus.IDLE
        totalSteps = 0
        currentTool = ""
        elapsedSec = 0
    }

    // ------------------------------------------------------------ 配置

    fun loadConfig() {
        val def = UiConfig()
        // 配置被清空/损坏时，优先从最近一次保存前留下的备份恢复。
        try {
            if (!AgentPaths.configFile.exists()) {
                val bak = File(AgentPaths.root, "ui-config.bak")
                if (bak.exists()) bak.copyTo(AgentPaths.configFile, overwrite = true)
            }
        } catch (_: Exception) {
        }
        val root = readJson(AgentPaths.configFile)
        val llm = root?.optJSONObject("llm")
        val run = root?.optJSONObject("run")
        val web = root?.optJSONObject("web")
        val caps = root?.optJSONObject("capabilities")
        val privilegeObj = root?.optJSONObject("privilege")
        val privilege = PrivilegeConfig(
            accessibility = privilegeObj?.optBoolean("accessibility", true) ?: true,
            shizuku = privilegeObj?.optBoolean("shizuku", true) ?: true,
            root = privilegeObj?.optBoolean("root", false) ?: false
        )
        val sandbox = root?.optJSONObject("sandbox")
        val skillsCfg = root?.optJSONObject("skills")
        val appearance = root?.optJSONObject("appearance")
        val sandboxRoots = arrayToList(sandbox?.optJSONArray("roots"))
        val baseUrl = llm?.optString("baseUrl", "") ?: ""
        val apiKey = llm?.optString("apiKey", "") ?: ""
        val model = llm?.optString("model", "") ?: ""
        val visionModel = llm?.optString("visionModel", "") ?: ""
        val visionBaseUrl = llm?.optString("visionBaseUrl", "") ?: ""
        val visionApiKey = llm?.optString("visionApiKey", "") ?: ""

        var chatModels = parseProfiles(root, "models")
        if (chatModels.isEmpty() && !(root?.has("models") ?: false)) chatModels = localProfiles(false)
        if (chatModels.isEmpty() && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
            chatModels = listOf(ModelProfile("legacy-chat", model, baseUrl, apiKey, model, true))
        }
        var visionModels = parseProfiles(root, "visionModels")
        if (visionModels.isEmpty() && !(root?.has("visionModels") ?: false)) visionModels = localProfiles(true)
        if (visionModels.isEmpty() && visionModel.isNotBlank()) {
            visionModels = listOf(
                ModelProfile(
                    "legacy-vision", visionModel,
                    visionBaseUrl.ifBlank { baseUrl },
                    visionApiKey.ifBlank { apiKey },
                    visionModel, true
                )
            )
        }
        // 服务商是对话/视觉模型共用的连接层：缺失时从已有模型自动生成，保证两套模型共用同一套管理逻辑。
        var providers = parseProviders(root)
        if (providers.isEmpty()) providers = legacyProviders(chatModels, visionModels)
        providers = normalizeLegacyProviderNames(providers)
        chatModels = attachProviders(chatModels, providers)
        visionModels = attachProviders(visionModels, providers)
        val activeModel = chatModels.firstOrNull { it.id == (root?.optString("activeModelId", "") ?: "") }
            ?: chatModels.firstOrNull()
        val activeVision = visionModels.firstOrNull { it.id == (root?.optString("activeVisionModelId", "") ?: "") }
            ?: visionModels.firstOrNull()
        val thinking = root?.optString("thinking", "")?.ifBlank { null }
            ?: llm?.optString("reasoningEffort", "")?.ifBlank { null }
            ?: "default"
        // 兼容旧的单一视觉开关：缺少新字段时两项都沿用 capabilities.vision。
        val legacyVision = caps?.optBoolean("vision", true) ?: true

        config = UiConfig(
            baseUrl = activeModel?.baseUrl ?: baseUrl,
            apiKey = activeModel?.apiKey ?: apiKey,
            model = activeModel?.model ?: model,
            visionModel = activeVision?.model ?: visionModel,
            visionBaseUrl = activeVision?.baseUrl ?: visionBaseUrl,
            visionApiKey = activeVision?.apiKey ?: visionApiKey,
            chatModels = chatModels,
            visionModels = visionModels,
            providers = providers,
            activeModelId = activeModel?.id ?: "",
            activeVisionModelId = activeVision?.id ?: "",
            thinking = if (thinking in THINKING_LEVELS) thinking else "default",
            theme = (root?.optString("theme", "system") ?: "system").let { if (it in listOf("system", "light", "dark")) it else "system" },
            accentId = (appearance?.optString("accent", "mist") ?: "mist").let {
                if (it in listOf("mist", "daiqing", "dusk", "turquoise", "amber", "graphite", "custom", "wallpaper")) it else "mist"
            },
            customAccent = appearance?.optString("customAccent", "#4B67B4")?.ifBlank { "#4B67B4" } ?: "#4B67B4",
            wallpaperAccent = appearance?.optBoolean("wallpaper", false) ?: false,
            chatTextScale = (appearance?.optDouble("chatTextScale", 1.0) ?: 1.0).toFloat().coerceIn(0.85f, 1.3f),
            foldCompletedTurns = appearance?.optBoolean("foldTurns", true) ?: true,
            capabilities = normalizeCaps(AgentCapabilities(
                screenRead = caps?.optBoolean("screenRead", true) ?: true,
                fileRead = caps?.optBoolean("fileRead", true) ?: true,
                mainVision = caps?.optBoolean("mainVision", legacyVision) ?: legacyVision,
                visionModelVision = caps?.optBoolean("visionModelVision", legacyVision) ?: legacyVision,
                web = caps?.optBoolean("web", true) ?: true,
                gui = caps?.optBoolean("gui", true) ?: true,
                fileEdit = caps?.optBoolean("fileEdit", true) ?: true,
                sandboxHighRisk = caps?.optBoolean("sandboxHighRisk", true) ?: true,
                outsideSandbox = caps?.optBoolean("outsideSandbox", false) ?: false,
                device = caps?.optBoolean("device", true) ?: true,
                clipboard = caps?.optBoolean("clipboard", true) ?: true,
                plan = caps?.optBoolean("plan", true) ?: true,
                a11y = caps?.optBoolean("a11y", true) ?: true,
                shizuku = caps?.optBoolean("shizuku", true) ?: true,
                root = if (caps != null && caps.has("root")) {
                    caps.optBoolean("root", false)
                } else {
                    root?.optJSONObject("root")?.optBoolean("enabled", false) ?: false
                }
            ), privilege),
            privilege = privilege,
            autoHandlePopup = root?.optBoolean("autoHandlePopup", true) ?: true,
            sandboxRoots = if (sandboxRoots.isNotEmpty()) sandboxRoots else listOf("/sdcard/Download/phone-agent", "/sdcard/Documents"),
            disabledSkills = arrayToList(skillsCfg?.optJSONArray("disabled")),
            onboarded = root?.optBoolean("onboarded", false) ?: false,
            maxSteps = run?.optInt("maxSteps", def.maxSteps) ?: def.maxSteps,
            maxMinutes = run?.optInt("maxMinutes", def.maxMinutes) ?: def.maxMinutes,
            approvalTools = arrayToCsv(run?.optJSONArray("approvalTools")),
            virtualDisplay = run?.optBoolean("virtualDisplay", false) ?: false,
            autoResume = run?.optBoolean("autoResume", true) ?: true,
            maxParallelDisplays = (run?.optInt("maxParallelDisplays", 2) ?: 2)
                .coerceIn(1, AgentVdRuntime.MAX_PARALLEL_SLOTS),
            webProvider = web?.optString("provider", "auto") ?: "auto",
            webMaxResults = web?.optInt("maxResults", 6) ?: 6,
            webExcludeDomains = arrayToCsv(web?.optJSONArray("excludeDomains")),
            webFetchProvider = web?.optString("fetchProvider", "auto") ?: "auto",
            allowPrivateUrls = web?.optBoolean("allowPrivateUrls", false) ?: false
        )
    }

    fun saveConfig(cfg: UiConfig) {
        try {
            val root = readJson(AgentPaths.configFile) ?: JSONObject()
            val llm = root.optJSONObject("llm") ?: JSONObject()
            llm.put("baseUrl", cfg.baseUrl)
            llm.put("apiKey", cfg.apiKey)
            llm.put("model", cfg.model)
            llm.put("visionModel", cfg.visionModel)
            llm.put("visionBaseUrl", cfg.visionBaseUrl)
            llm.put("visionApiKey", cfg.visionApiKey)
            val activeChatModel = cfg.chatModels.firstOrNull { m -> m.id == cfg.activeModelId }
            val activeChatProvider = cfg.providers.firstOrNull { it.id == activeChatModel?.providerId }
            val activeVisionProvider = cfg.providers.firstOrNull { it.id == cfg.visionModels.firstOrNull { m -> m.id == cfg.activeVisionModelId }?.providerId }
            llm.put("reasoningEffort", reasoningEffort(cfg.thinking, activeChatProvider))
            // Cherry Studio 范式：不再用提示词限制模型输出，也不再限制 max_tokens。
            llm.remove("thinkingPrompt")
            llm.remove("contextTokens")
            llm.put("maxTokens", 0)
            // 上下文窗口来自服务商；缺失时用模型族保守估算。80% 触发压缩（Cherry 默认）。
            val ctxWindow = activeChatModel?.contextWindow?.takeIf { it > 0 }
                ?: AgentModelContext.estimate(cfg.model, activeChatProvider?.protocol ?: "openai", activeChatProvider?.presetId ?: "")
            llm.put("contextWindow", ctxWindow)
            llm.put("contextSource", activeChatModel?.contextSource?.ifBlank { "estimated" } ?: "estimated")
            llm.put("contextCompressRatio", AgentModelContext.COMPRESS_THRESHOLD_PERCENT)
            llm.put("protocol", activeChatProvider?.protocol ?: "openai")
            llm.put("visionProtocol", activeVisionProvider?.protocol ?: "openai")
            root.put("llm", llm)

            root.put("providers", providersJson(cfg.providers))
            root.put("models", profilesJson(cfg.chatModels))
            root.put("visionModels", profilesJson(cfg.visionModels))
            root.put("activeModelId", cfg.activeModelId)
            root.put("activeVisionModelId", cfg.activeVisionModelId)
            root.put("thinking", cfg.thinking)
            root.remove("contextTokens")
            root.put("theme", cfg.theme)
            root.put("onboarded", cfg.onboarded)
            val appearance = root.optJSONObject("appearance") ?: JSONObject()
            appearance.put("accent", cfg.accentId)
            appearance.put("customAccent", cfg.customAccent)
            appearance.put("wallpaper", cfg.wallpaperAccent)
            appearance.put("chatTextScale", cfg.chatTextScale.toDouble())
            appearance.put("foldTurns", cfg.foldCompletedTurns)
            root.put("appearance", appearance)
            root.put(
                "capabilities",
                JSONObject()
                    .put("screenRead", cfg.capabilities.screenRead)
                    .put("fileRead", cfg.capabilities.fileRead)
                    .put("mainVision", cfg.capabilities.mainVision)
                    .put("visionModelVision", cfg.capabilities.visionModelVision)
                    .put("vision", cfg.capabilities.vision)
                    .put("web", cfg.capabilities.web)
                    .put("gui", cfg.capabilities.gui)
                    .put("fileEdit", cfg.capabilities.fileEdit)
                    .put("sandboxHighRisk", cfg.capabilities.sandboxHighRisk)
                    .put("outsideSandbox", cfg.capabilities.outsideSandbox)
                    .put("device", cfg.capabilities.device)
                    .put("clipboard", cfg.capabilities.clipboard)
                    .put("plan", cfg.capabilities.plan)
                    .put("a11y", cfg.capabilities.a11y)
                    .put("shizuku", cfg.capabilities.shizuku)
                    .put("root", cfg.capabilities.root)
            )

            root.put(
                "privilege",
                JSONObject()
                    .put("accessibility", cfg.privilege.accessibility)
                    .put("shizuku", cfg.privilege.shizuku)
                    .put("root", cfg.privilege.root)
            )
            root.put("autoHandlePopup", cfg.autoHandlePopup)

            val rootCfg = root.optJSONObject("root") ?: JSONObject()
            rootCfg.put("enabled", cfg.capabilities.root)
            root.put("root", rootCfg)

            val sandbox = root.optJSONObject("sandbox") ?: JSONObject()
            val roots = JSONArray()
            cfg.sandboxRoots.forEach { roots.put(it) }
            sandbox.put("roots", roots)
            root.put("sandbox", sandbox)

            val skillsCfg = root.optJSONObject("skills") ?: JSONObject()
            val disabled = JSONArray()
            cfg.disabledSkills.forEach { disabled.put(it) }
            skillsCfg.put("disabled", disabled)
            root.put("skills", skillsCfg)

            val run = root.optJSONObject("run") ?: JSONObject()
            run.put("maxSteps", cfg.maxSteps)
            run.put("maxMinutes", cfg.maxMinutes)
            run.put("approvalTools", csvToArray(cfg.approvalTools))
            run.put("virtualDisplay", cfg.virtualDisplay)
            run.put("autoResume", cfg.autoResume)
            run.put("maxParallelDisplays", cfg.maxParallelDisplays)
            run.remove("compactAfterMessages")
            root.put("run", run)

            // 跨任务历史按上下文窗口给一个小预算（Cherry 的 context 管理思路），避免旧对话挤占窗口。
            val conv = root.optJSONObject("conversation") ?: JSONObject()
            val ctxWindowForHistory = activeChatModel?.contextWindow?.takeIf { it > 0 }
                ?: AgentModelContext.estimate(cfg.model, activeChatProvider?.protocol ?: "openai")
            conv.put("maxTurns", 8)
            conv.put("maxChars", minOf(12000, maxOf(3000, ctxWindowForHistory)))
            root.put("conversation", conv)

            val web = root.optJSONObject("web") ?: JSONObject()
            web.put("provider", cfg.webProvider)
            web.put("maxResults", cfg.webMaxResults)
            web.put("excludeDomains", csvToArray(cfg.webExcludeDomains))
            web.put("fetchProvider", cfg.webFetchProvider)
            web.put("allowPrivateUrls", cfg.allowPrivateUrls)
            root.put("web", web)

            AgentPaths.root.mkdirs()
            // 每次写盘前保留上一版，避免引导/重装流程把模型配置覆盖后无法找回。
            try {
                if (AgentPaths.configFile.exists()) {
                    AgentPaths.configFile.copyTo(File(AgentPaths.root, "ui-config.bak"), overwrite = true)
                }
            } catch (_: Exception) {
            }
            AgentPaths.configFile.writeText(root.toString())
            config = cfg
            lastError = null
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    fun selectModel(id: String) {
        val p = config.chatModels.firstOrNull { it.id == id } ?: return
        val provider = config.providers.firstOrNull { it.id == p.providerId }
        if (provider != null && !provider.enabled) {
            lastError = "服务商「${provider.name}」已停用"
            return
        }
        saveConfig(config.copy(activeModelId = p.id, baseUrl = p.baseUrl, apiKey = p.apiKey, model = p.model))
    }

    fun selectVisionModel(id: String) {
        val p = config.visionModels.firstOrNull { it.id == id } ?: return
        val provider = config.providers.firstOrNull { it.id == p.providerId }
        if (provider != null && !provider.enabled) {
            lastError = "服务商「${provider.name}」已停用"
            return
        }
        saveConfig(
            config.copy(
                activeVisionModelId = p.id,
                visionModel = p.model,
                visionBaseUrl = p.baseUrl,
                visionApiKey = p.apiKey
            )
        )
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        val providers = config.providers.map { if (it.id == id) it.copy(enabled = enabled) else it }
        var chat = config.chatModels
        var vision = config.visionModels
        var activeChat = chat.firstOrNull { it.id == config.activeModelId }
        var activeVision = vision.firstOrNull { it.id == config.activeVisionModelId }
        if (!enabled) {
            val enabledProviderIds = providers.filter { it.enabled }.map { it.id }.toSet()
            if (activeChat?.providerId == id) {
                activeChat = chat.firstOrNull { it.providerId in enabledProviderIds }
            }
            if (activeVision?.providerId == id) {
                activeVision = vision.firstOrNull { it.providerId in enabledProviderIds }
            }
        }
        saveConfig(
            config.copy(
                providers = providers,
                activeModelId = activeChat?.id ?: "",
                baseUrl = activeChat?.baseUrl ?: "",
                apiKey = activeChat?.apiKey ?: "",
                model = activeChat?.model ?: "",
                activeVisionModelId = activeVision?.id ?: "",
                visionModel = activeVision?.model ?: "",
                visionBaseUrl = activeVision?.baseUrl ?: "",
                visionApiKey = activeVision?.apiKey ?: ""
            )
        )
    }

    fun addModel(profile: ModelProfile, vision: Boolean) {
        val cfg = if (vision) {
            val select = config.activeVisionModelId.isBlank()
            config.copy(
                visionModels = config.visionModels + profile,
                activeVisionModelId = if (select) profile.id else config.activeVisionModelId,
                visionModel = if (select) profile.model else config.visionModel,
                visionBaseUrl = if (select) profile.baseUrl else config.visionBaseUrl,
                visionApiKey = if (select) profile.apiKey else config.visionApiKey
            )
        } else {
            val select = config.activeModelId.isBlank()
            config.copy(
                chatModels = config.chatModels + profile,
                activeModelId = if (select) profile.id else config.activeModelId,
                baseUrl = if (select) profile.baseUrl else config.baseUrl,
                apiKey = if (select) profile.apiKey else config.apiKey,
                model = if (select) profile.model else config.model
            )
        }
        saveConfig(cfg)
    }

    fun deleteModel(id: String, vision: Boolean) {
        if (vision) {
            val list = config.visionModels.filterNot { it.id == id }
            val active = if (config.activeVisionModelId == id) list.firstOrNull() else list.firstOrNull { it.id == config.activeVisionModelId }
            saveConfig(
                config.copy(
                    visionModels = list,
                    activeVisionModelId = active?.id ?: "",
                    visionModel = active?.model ?: "",
                    visionBaseUrl = active?.baseUrl ?: "",
                    visionApiKey = active?.apiKey ?: ""
                )
            )
        } else {
            val list = config.chatModels.filterNot { it.id == id }
            val active = if (config.activeModelId == id) list.firstOrNull() else list.firstOrNull { it.id == config.activeModelId }
            saveConfig(
                config.copy(
                    chatModels = list,
                    activeModelId = active?.id ?: "",
                    baseUrl = active?.baseUrl ?: "",
                    apiKey = active?.apiKey ?: "",
                    model = active?.model ?: ""
                )
            )
        }
    }

    fun setThinking(level: String) {
        if (level !in THINKING_LEVELS) return
        saveConfig(config.copy(thinking = level))
    }

    // ------------------------------------------------------------ 服务商（对话/视觉共用）

    fun addProvider(provider: ProviderProfile) {
        val list = config.providers.filterNot { it.id == provider.id } + provider
        saveConfig(config.copy(providers = list))
    }

    fun updateProvider(provider: ProviderProfile) {
        if (config.providers.none { it.id == provider.id }) {
            addProvider(provider)
            return
        }
        val providers = config.providers.map { if (it.id == provider.id) provider else it }
        val chat = config.chatModels.map { m ->
            if (m.providerId == provider.id) m.copy(baseUrl = provider.baseUrl, apiKey = provider.apiKey, providerName = provider.name) else m
        }
        val vision = config.visionModels.map { m ->
            if (m.providerId == provider.id) m.copy(baseUrl = provider.baseUrl, apiKey = provider.apiKey, providerName = provider.name) else m
        }
        val activeChat = chat.firstOrNull { it.id == config.activeModelId }
        val activeVision = vision.firstOrNull { it.id == config.activeVisionModelId }
        saveConfig(
            config.copy(
                providers = providers,
                chatModels = chat,
                visionModels = vision,
                baseUrl = activeChat?.baseUrl ?: config.baseUrl,
                apiKey = activeChat?.apiKey ?: config.apiKey,
                visionBaseUrl = activeVision?.baseUrl ?: config.visionBaseUrl,
                visionApiKey = activeVision?.apiKey ?: config.visionApiKey
            )
        )
    }

    fun deleteProvider(id: String) {
        val chat = config.chatModels.filterNot { it.providerId == id }
        val vision = config.visionModels.filterNot { it.providerId == id }
        val active = chat.firstOrNull { it.id == config.activeModelId } ?: chat.firstOrNull()
        val activeV = vision.firstOrNull { it.id == config.activeVisionModelId } ?: vision.firstOrNull()
        saveConfig(
            config.copy(
                providers = config.providers.filterNot { it.id == id },
                chatModels = chat,
                visionModels = vision,
                activeModelId = active?.id ?: "",
                activeVisionModelId = activeV?.id ?: "",
                baseUrl = active?.baseUrl ?: "",
                apiKey = active?.apiKey ?: "",
                model = active?.model ?: "",
                visionModel = activeV?.model ?: "",
                visionBaseUrl = activeV?.baseUrl ?: "",
                visionApiKey = activeV?.apiKey ?: ""
            )
        )
    }

    /** 连接检测结果实时回写；视觉/对话共用同一套状态字段。 */
    fun updateModelCheck(
        modelId: String,
        vision: Boolean,
        ok: Boolean,
        message: String,
        latencyMs: Long,
        contextWindow: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val list = if (vision) config.visionModels else config.chatModels
        if (list.none { it.id == modelId }) return
        val updated = list.map {
            if (it.id == modelId) {
                it.copy(
                    verified = ok,
                    lastCheckedAt = now,
                    lastLatencyMs = latencyMs,
                    lastMessage = message,
                    contextWindow = if (contextWindow > 0) contextWindow else it.contextWindow,
                    contextSource = if (contextWindow > 0) "provider" else it.contextSource
                )
            } else it
        }
        if (vision) {
            val active = updated.firstOrNull { it.id == config.activeVisionModelId }
            saveConfig(config.copy(visionModels = updated, visionApiKey = active?.apiKey ?: config.visionApiKey))
        } else {
            val active = updated.firstOrNull { it.id == config.activeModelId }
            saveConfig(config.copy(chatModels = updated, apiKey = active?.apiKey ?: config.apiKey))
        }
    }

    fun setTheme(theme: String) {
        saveConfig(config.copy(theme = theme))
    }

    /** DSH 配色切换：预设 / 自定义 / 跟随壁纸。 */
    fun setAccent(accentId: String, customHex: String = config.customAccent, wallpaper: Boolean = config.wallpaperAccent) {
        saveConfig(config.copy(accentId = accentId, customAccent = customHex, wallpaperAccent = wallpaper))
    }

    fun setChatTextScale(scale: Float) {
        saveConfig(config.copy(chatTextScale = scale.coerceIn(0.85f, 1.3f)))
    }

    fun setFoldCompletedTurns(value: Boolean) {
        saveConfig(config.copy(foldCompletedTurns = value))
    }

    // ------------------------------------------------------------ 会话中心

    data class ConversationSummary(
        val id: String,
        val title: String,
        val lastModified: Long,
        val runCount: Int,
        val done: Boolean,
        val running: Boolean
    )

    /** 按 conversationId 聚合 run，最近更新的在前。 */
    fun conversationSummaries(): List<ConversationSummary> {
        val dirs = AgentPaths.runsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        val groups = LinkedHashMap<String, MutableList<File>>()
        dirs.sortedByDescending { it.lastModified() }.forEach { dir ->
            val cid = conversationIdOf(dir).ifEmpty { "c-legacy" }
            groups.getOrPut(cid) { mutableListOf() }.add(dir)
        }
        return groups.map { (cid, list) ->
            val latest = list.maxByOrNull { it.lastModified() }
            val meta = latest?.let { metaOf(it) }
            val running = latest != null && !meta!!.done && !meta.ended &&
                !File(latest, ".stopped").exists() &&
                System.currentTimeMillis() - latest.lastModified() < 120_000
            ConversationSummary(
                id = cid,
                title = meta?.task?.takeIf { it.isNotBlank() } ?: "(无任务文本)",
                lastModified = latest?.lastModified() ?: 0L,
                runCount = list.size,
                done = meta?.done == true || meta?.ended == true,
                running = running
            )
        }.sortedByDescending { it.lastModified }
    }

    /** 切换到某段历史会话：写 conversation.txt，下一次 refresh 聚合该会话全部 run。 */
    fun openConversation(id: String) {
        if (status == AgentStatus.RUNNING || status == AgentStatus.PAUSED || status == AgentStatus.WAITING) return
        if (id == conversationId) return
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.conversationFile.writeText(id)
        } catch (e: Exception) {
            lastError = e.message
            return
        }
        conversationId = id
        legacyMode = (id == "c-legacy")
        viewingRunId = null
        currentRunId = null
        lastStepsSize = -1
        seen.clear()
        baselineLoaded = false
        runWasDoneAtLoad = false
        clearLive()
        pendingApproval = null
        todos = emptyList()
        planSteps = emptyList()
        messages.clear()
        status = AgentStatus.IDLE
        refresh()
    }

    /** 删除整段会话及其全部 run；运行中的会话拒绝删除。 */
    fun deleteConversations(ids: Collection<String>): Boolean {
        if (status == AgentStatus.RUNNING || status == AgentStatus.PAUSED || status == AgentStatus.WAITING) return false
        val dirs = AgentPaths.runsDir.listFiles { f -> f.isDirectory } ?: return false
        val targets = ids.toSet()
        var changed = false
        dirs.forEach { dir ->
            val cid = conversationIdOf(dir).ifEmpty { "c-legacy" }
            if (cid !in targets) return@forEach
            try {
                if (dir.deleteRecursively()) {
                    changed = true
                    convIdCache.remove(dir.name)
                    metaCache.remove(dir.name)
                }
            } catch (_: Exception) {
            }
        }
        targets.forEach { cid ->
            if (cid != "c-legacy") {
                File(AgentPaths.root, "conversations/$cid.jsonl").delete()
                File(AgentPaths.root, "conversations/$cid.summary.txt").delete()
            }
            if (cid == conversationId) {
                val next = "c-" + System.currentTimeMillis()
                try { AgentPaths.conversationFile.writeText(next) } catch (_: Exception) {}
                conversationId = next
                legacyMode = false
            }
        }
        if (changed) {
            cachedDirs = emptyList()
            lastDirsScan = 0
            viewingRunId = null
            currentRunId = null
            messages.clear()
            status = AgentStatus.IDLE
            refresh()
        }
        return changed
    }

    /** 全局关闭时，Agent 对应权限必须为 false；这是读出配置后的归一化，不依赖 UI。 */
    private fun normalizeCaps(c: AgentCapabilities, p: PrivilegeConfig): AgentCapabilities {
        var n = c
        if (!p.accessibility) n = n.copy(a11y = false)
        if (!p.shizuku) n = n.copy(shizuku = false)
        if (!p.root) n = n.copy(root = false)
        return n
    }

    fun setCapability(key: String, value: Boolean) {
        val c = config.capabilities
        val p = config.privilege
        // 硬限制：全局未开启时，Agent 侧不能自行开启对应权限。
        if (value) {
            when (key) {
                "a11y" -> if (!p.accessibility) return
                "shizuku" -> if (!p.shizuku) return
                "root" -> if (!p.root) return
            }
        }
        val next = when (key) {
            "screenRead" -> c.copy(screenRead = value)
            "fileRead" -> c.copy(fileRead = value)
            "mainVision" -> c.copy(mainVision = value)
            "visionModelVision" -> c.copy(visionModelVision = value)
            "vision" -> c.copy(mainVision = value, visionModelVision = value)
            "web" -> c.copy(web = value)
            "gui" -> c.copy(gui = value)
            "fileEdit" -> c.copy(fileEdit = value)
            "sandboxHighRisk" -> c.copy(sandboxHighRisk = value)
            "outsideSandbox" -> c.copy(outsideSandbox = value)
            "device" -> c.copy(device = value)
            "clipboard" -> c.copy(clipboard = value)
            "plan" -> c.copy(plan = value)
            "a11y" -> c.copy(a11y = value)
            "shizuku" -> c.copy(shizuku = value)
            "root" -> c.copy(root = value)
            else -> c
        }
        saveConfig(config.copy(capabilities = next))
    }

    /**
     * 全局权限开关联动：
     * - 关闭：Agent 对应权限立即关闭（全部三权限）。
     * - 开启：Agent 无障碍 / Shizuku 自动跟随开启；Root 保持原值（默认关闭）。
     */
    fun setPrivilege(key: String, enabled: Boolean) {
        val p = config.privilege
        val nextP = when (key) {
            "accessibility" -> p.copy(accessibility = enabled)
            "shizuku" -> p.copy(shizuku = enabled)
            "root" -> p.copy(root = enabled)
            else -> p
        }
        val c = config.capabilities
        val nextC = when (key) {
            "accessibility" -> c.copy(a11y = enabled)
            "shizuku" -> c.copy(shizuku = enabled)
            "root" -> if (enabled) c else c.copy(root = false)
            else -> c
        }
        saveConfig(config.copy(privilege = nextP, capabilities = nextC))
    }

    fun setAutoHandlePopup(enabled: Boolean) {
        saveConfig(config.copy(autoHandlePopup = enabled))
    }

    fun updateSandboxRoots(roots: List<String>) {
        saveConfig(config.copy(sandboxRoots = roots))
    }

    fun setOnboarded(value: Boolean) {
        // 首次启动时如果存储权限还没授予，loadConfig 会退到默认值；
        // 此时直接 saveConfig 会把磁盘上已有的模型、虚拟屏等设置覆盖掉。
        // 这里只改 onboarded 标记并重新载入，避免引导完成时丢配置。
        try {
            val root = readJson(AgentPaths.configFile) ?: JSONObject()
            root.put("onboarded", value)
            AgentPaths.root.mkdirs()
            // 每次写盘前保留上一版，避免引导/重装流程把模型配置覆盖后无法找回。
            try {
                if (AgentPaths.configFile.exists()) {
                    AgentPaths.configFile.copyTo(File(AgentPaths.root, "ui-config.bak"), overwrite = true)
                }
            } catch (_: Exception) {
            }
            AgentPaths.configFile.writeText(root.toString())
            loadConfig()
            lastError = null
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    fun setSkillEnabled(folder: String, enabled: Boolean) {
        val next = config.disabledSkills.toMutableSet()
        if (enabled) next.remove(folder) else next.add(folder)
        saveConfig(config.copy(disabledSkills = next.toList()))
    }

    fun hasAccessibility(): Boolean {
        val ctx = appContext ?: return false
        val enabled = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
    }

    /**
     * 确保无障碍开启：已开启直接返回；否则按 Shizuku → root 的顺序写入 Secure 设置。
     * 返回 true 表示已开启（原本就开启或写入成功并已连接）。
     */
    fun ensureAccessibilityByRoot(): Boolean {
        // Agent 无障碍关闭时不得自动开启；同时必须有可用的 shell 权限层级。
        if (!config.capabilities.a11y) return false
        if (!config.capabilities.shizuku && !config.capabilities.root) return false
        val r = runCatching {
            org.json.JSONObject(PrivilegeManager.ensureAccessibility())
        }.getOrNull() ?: return false
        return r.optBoolean("ok", false)
    }

    fun openAccessibilitySettings() {
        val ctx = appContext ?: return
        try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun updateWebSettings(provider: String, maxResults: Int, excludeDomains: String, fetchProvider: String, allowPrivateUrls: Boolean) {
        saveConfig(config.copy(webProvider = provider, webMaxResults = maxResults, webExcludeDomains = excludeDomains, webFetchProvider = fetchProvider, allowPrivateUrls = allowPrivateUrls))
    }

    fun updateRunSettings(
        maxSteps: Int,
        maxMinutes: Int,
        approvalTools: String,
        virtualDisplay: Boolean = config.virtualDisplay,
        autoResume: Boolean = config.autoResume,
        maxParallelDisplays: Int = config.maxParallelDisplays
    ) {
        saveConfig(
            config.copy(
                maxSteps = maxSteps,
                maxMinutes = maxMinutes,
                approvalTools = approvalTools,
                virtualDisplay = virtualDisplay,
                autoResume = autoResume,
                // 用户入口硬上限 5；系统内部硬上限 6（AgentVdRuntime.MAX_PARALLEL_SLOTS）。
                maxParallelDisplays = maxParallelDisplays.coerceIn(1, 5)
            )
        )
    }

    private fun localValue(text: String, key: String): String {
        val m = Regex("""(?m)\b""" + Regex.escape(key) + """\s*:\s*['"]([^'"]+)['"]""").find(text)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun localProfiles(vision: Boolean): List<ModelProfile> {
        val file = File("/sdcard/phone-agent/src/config/local.js")
        if (!file.exists()) return emptyList()
        val text = try { file.readText() } catch (e: Exception) { return emptyList() }
        val chatBase = localValue(text, "baseUrl")
        val chatKey = localValue(text, "apiKey")
        val chatModel = localValue(text, "model")
        if (!vision) {
            if (chatModel.isBlank()) return emptyList()
            return listOf(ModelProfile("local-chat", chatModel, chatBase, chatKey, chatModel, true))
        }
        val vm = localValue(text, "visionModel")
        if (vm.isBlank()) return emptyList()
        return listOf(
            ModelProfile(
                "local-vision", vm,
                localValue(text, "visionBaseUrl").ifBlank { chatBase },
                localValue(text, "visionApiKey").ifBlank { chatKey },
                vm, true
            )
        )
    }

    private fun parseProfiles(root: JSONObject?, key: String): List<ModelProfile> {
        val arr = root?.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<ModelProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val m = o.optString("model", "")
            if (m.isBlank()) continue
            out.add(
                ModelProfile(
                    id = o.optString("id", "$key-$i"),
                    name = o.optString("name", m),
                    baseUrl = o.optString("baseUrl", ""),
                    apiKey = o.optString("apiKey", ""),
                    model = m,
                    verified = o.optBoolean("verified", true),
                    providerId = o.optString("providerId", ""),
                    providerName = o.optString("providerName", ""),
                    protocol = o.optString("protocol", "openai"),
                    lastCheckedAt = o.optLong("lastCheckedAt", 0L),
                    lastLatencyMs = o.optLong("lastLatencyMs", 0L),
                    lastMessage = o.optString("lastMessage", ""),
                    contextWindow = o.optInt("contextWindow", 0).let {
                        if (it > 0) it else AgentModelContext.estimate(m, o.optString("protocol", "openai"))
                    },
                    contextSource = o.optString("contextSource", "").ifBlank { "estimated" }
                )
            )
        }
        return out
    }

    private fun profilesJson(list: List<ModelProfile>): JSONArray {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
                    .put("model", p.model)
                    .put("verified", p.verified)
                    .put("providerId", p.providerId)
                    .put("providerName", p.providerName)
                    .put("protocol", p.protocol)
                    .put("lastCheckedAt", p.lastCheckedAt)
                    .put("lastLatencyMs", p.lastLatencyMs)
                    .put("lastMessage", p.lastMessage)
                    .put("contextWindow", p.contextWindow)
                    .put("contextSource", p.contextSource)
            )
        }
        return arr
    }

    private fun parseProviders(root: JSONObject?): List<ProviderProfile> {
        val arr = root?.optJSONArray("providers") ?: return emptyList()
        val out = mutableListOf<ProviderProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "")
            val baseUrl = o.optString("baseUrl", "")
            if (name.isBlank() && baseUrl.isBlank()) continue
            out.add(
                ProviderProfile(
                    id = o.optString("id", "pv-$i"),
                    name = name.ifBlank { baseUrl },
                    baseUrl = baseUrl,
                    apiKey = o.optString("apiKey", ""),
                    protocol = o.optString("protocol", "openai"),
                    presetId = o.optString("presetId", ""),
                    custom = o.optBoolean("custom", false),
                    enabled = o.optBoolean("enabled", true)
                )
            )
        }
        return out
    }

    private fun providersJson(list: List<ProviderProfile>): JSONArray {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
                    .put("protocol", p.protocol)
                    .put("presetId", p.presetId)
                    .put("custom", p.custom)
                    .put("enabled", p.enabled)
            )
        }
        return arr
    }

    /** 旧配置没有服务商层：按 baseUrl + apiKey 去重生成，模型仍可单独选择。 */
    private fun legacyProviders(chat: List<ModelProfile>, vision: List<ModelProfile>): List<ProviderProfile> {
        val out = mutableListOf<ProviderProfile>()
        (chat + vision).forEach { m ->
            if (m.baseUrl.isBlank()) return@forEach
            val same = out.firstOrNull { it.baseUrl == m.baseUrl && it.apiKey == m.apiKey }
            if (same != null) return@forEach
            val host = hostOf(m.baseUrl).ifBlank { m.baseUrl }
            val preset = presetByHost(m.baseUrl)
            out.add(
                ProviderProfile(
                    id = "pv-" + Integer.toHexString((m.baseUrl + "|" + m.apiKey).hashCode()),
                    name = preset?.name ?: host.ifBlank { "服务商" },
                    baseUrl = m.baseUrl,
                    apiKey = m.apiKey,
                    protocol = preset?.protocol ?: "openai",
                    presetId = preset?.id ?: "",
                    custom = preset == null
                )
            )
        }
        return out
    }

    /** 自动生成的服务商名称如果是主机名，用内置预设的中文/品牌名替换。 */
    private fun normalizeLegacyProviderNames(list: List<ProviderProfile>): List<ProviderProfile> {
        return list.map { p ->
            val host = hostOf(p.baseUrl)
            val raw = p.baseUrl.trim().replace(Regex("""https?://"""), "").trimEnd('/')
            if (p.presetId.isBlank() && (p.name == host || p.name == raw)) {
                val preset = presetByHost(p.baseUrl)
                if (preset != null) {
                    p.copy(name = preset.name, presetId = preset.id, protocol = preset.protocol, custom = false)
                } else p
            } else p
        }
    }

    private fun hostOf(url: String): String {
        val m = Regex("""https?://([^/?#]+)""").find(url.trim())
        return m?.groupValues?.get(1) ?: ""
    }

    private fun presetByHost(url: String): ProviderPreset? {
        val host = hostOf(url)
        if (host.isBlank()) return null
        return ProviderPresets.all.firstOrNull {
            it.baseUrl.isNotBlank() && hostOf(it.baseUrl) == host
        }
    }

    private fun attachProviders(models: List<ModelProfile>, providers: List<ProviderProfile>): List<ModelProfile> {
        if (providers.isEmpty()) {
            return models.map { m ->
                if (m.contextWindow > 0) m else m.copy(
                    contextWindow = AgentModelContext.estimate(m.model, m.protocol),
                    contextSource = "estimated"
                )
            }
        }
        return models.map { m ->
            val existing = providers.firstOrNull { it.id == m.providerId }
            val p = existing
                ?: providers.firstOrNull { it.baseUrl == m.baseUrl && it.apiKey == m.apiKey }
                ?: providers.firstOrNull { it.baseUrl == m.baseUrl }
                ?: providers.firstOrNull()
            val withCtx = if (m.contextWindow > 0) m else m.copy(
                contextWindow = AgentModelContext.estimate(m.model, p?.protocol ?: m.protocol, p?.presetId ?: ""),
                contextSource = "estimated"
            )
            if (p == null) withCtx
            else withCtx.copy(providerId = p.id, providerName = p.name, protocol = p.protocol)
        }
    }

    private fun reasoningEffort(level: String, provider: ProviderProfile?): String {
        if (provider != null && (provider.protocol == "openai" || provider.protocol == "responses")) {
            val key = provider.presetId.ifBlank { provider.id }
            val mapped = AgentReasoningProfiles.effortFor(key, level)
            if (!mapped.isNullOrBlank()) return mapped
        }
        return when (level) {
            // Cherry Studio 的 generic effort 端点把 auto 归一为 medium。
            "auto" -> "medium"
            "none" -> "none"
            "minimal" -> "minimal"
            "low" -> "low"
            "medium" -> "medium"
            "high" -> "high"
            "xhigh" -> "xhigh"
            "max" -> "max"
            else -> ""
        }
    }

    private fun csvToArray(csv: String): JSONArray {
        val arr = JSONArray()
        csv.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { arr.put(it) }
        return arr
    }

    private fun arrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotBlank() && !out.contains(s)) out.add(s)
        }
        return out
    }

    private fun arrayToCsv(arr: JSONArray?): String {
        if (arr == null) return ""
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotBlank()) out.add(s)
        }
        return out.joinToString(",")
    }

    // ------------------------------------------------------------ 任务

    /** 把用户选择的图片/文件复制到工作目录；失败时返回 null 并写 lastError。 */
    fun attach(uri: Uri): AgentAttachment? {
        val ctx = appContext ?: return null
        return try {
            val resolver = ctx.contentResolver
            val mime = resolver.getType(uri) ?: guessMime(uri)
            var name = ""
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: ""
                }
            } catch (_: Exception) {
            }
            if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val dir = File(AgentPaths.root, "attachments").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_").take(60)
            val out = File(dir, System.currentTimeMillis().toString() + "_" + safe)
            val input = resolver.openInputStream(uri) ?: return null
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            if (out.length() > 60L * 1024 * 1024) {
                out.delete()
                lastError = "附件超过 60MB，已取消"
                return null
            }
            AgentAttachment(out.absolutePath, name, mime).also { pendingAttachment = it }
        } catch (e: Exception) {
            lastError = "附件读取失败：${e.message}"
            null
        }
    }

    fun clearAttachment() {
        pendingAttachment = null
    }

    private fun guessMime(uri: Uri): String {
        val n = (uri.lastPathSegment ?: "").lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".pdf") -> "application/pdf"
            n.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            n.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            n.endsWith(".doc") -> "application/msword"
            n.endsWith(".xls") -> "application/vnd.ms-excel"
            n.endsWith(".csv") -> "text/csv"
            n.endsWith(".md") -> "text/markdown"
            n.endsWith(".txt") -> "text/plain"
            n.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }

    /** 把本次附件写入 attachment.json，供 JS 内核在启动时读取。 */
    private fun writeAttachmentFile() {
        val f = File(AgentPaths.root, "attachment.json")
        try {
            val a = pendingAttachment
            if (a == null) {
                if (f.exists()) f.delete()
            } else {
                f.writeText(
                    JSONObject()
                        .put("path", a.path)
                        .put("name", a.name)
                        .put("mime", a.mime)
                        .toString()
                )
            }
        } catch (_: Exception) {
        }
        pendingAttachment = null
    }

    fun startTask(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.taskFile.writeText(t)
            writeAttachmentFile()
            // 清理上一次运行遗留的提问/回答，避免新任务一启动就弹出旧问题。
            runCatching { File(AgentPaths.root, "ask.json").delete() }
            runCatching { File(AgentPaths.root, "answer.txt").delete() }
            pendingQuestion = null
            AgentPaths.controlFile.writeText("")
            if (AgentPaths.resumeFile.exists()) AgentPaths.resumeFile.delete()
            if (!AgentPaths.scriptFile.exists()) {
                // 首次启动或用户已授权“所有文件访问”时，兜底再尝试部署一次内置脚本。
                appContext?.let { ctx ->
                    runCatching { AgentScriptInstaller.ensureInstalled(ctx) }
                }
            }
            if (!AgentPaths.scriptFile.exists()) {
                lastError = "找不到脚本：${AgentPaths.scriptFile}"
                status = AgentStatus.FAILED
                return
            }
            val vdAllowed = config.virtualDisplay && config.privilege.root && config.capabilities.root
            if (config.virtualDisplay && !vdAllowed) {
                // 硬限制下不能使用 root：不报错，直接按物理屏运行。
                lastError = "Agent Root 未开启：本次按物理屏运行"
            }
            if (vdAllowed) {
                val ctx = appContext ?: throw IllegalStateException("AgentStore 未初始化")
                // 虚拟屏准备期间就让 UI 进入运行态：用户此时可以暂停/终止，
                // 也避免准备阶段的重复发送。
                pendingTask = t
                // pendingStartedAt 仍由 startTaskReady 设置：虚拟屏准备可能要等 root 授权，
                // 不能让 refresh 的 10 秒“引擎未启动”误判提前触发。
                stopRequested = false
                status = AgentStatus.RUNNING
                // 不在主线程做 HTTP/root 准备工作，否则 NetworkOnMainThreadException
                // 或 su 等待会把发送按钮拖到无响应。
                Thread {
                    // 并行控件树后端复用无障碍服务；虚拟屏模式下也要确保它已开启。
                    // 失败不影响任务：脚本会自动回退 ToolMain 串行后端。
                    runCatching { ensureAccessibilityByRoot() }
                    val vd = AgentVdRuntime.ensureServer(ctx)
                    Handler(Looper.getMainLooper()).post {
                        if (!vd.ok) {
                            if (stopRequested) {
                                pendingTask = null
                                pendingStartedAt = 0
                                stopRequested = false
                                status = AgentStatus.DONE
                            } else {
                                lastError = "后台虚拟屏不可用：${vd.message}"
                                vdError = vd.message
                                status = AgentStatus.FAILED
                                pendingTask = null
                                pendingStartedAt = 0
                            }
                        } else {
                            startTaskReady(t)
                        }
                    }
                }.start()
                return
            }
            startTaskReady(t)
        } catch (e: Exception) {
            lastError = e.message
            status = AgentStatus.FAILED
            AgentTaskService.stop(appContext)
        }
    }

    /**
     * 从断点续跑（崩溃/被系统杀死后）。与 startTask 的区别是保留 resume.txt，
     * 由 main.js 读取并加载 checkpoint。
     */
    fun resumeRun(id: String) {
        try {
            val dir = File(AgentPaths.runsDir, id)
            if (!dir.exists()) return
            AgentPaths.root.mkdirs()
            AgentPaths.resumeFile.writeText(id)
            AgentPaths.controlFile.writeText("")
            AgentPaths.pausedFile.delete()
            val originalTask = metaOf(dir).task
            pendingTask = originalTask.ifBlank { "续跑：$id" }
            pendingStartedAt = System.currentTimeMillis()
            stopRequested = false
            pausedByUser = false
            todos = emptyList()
            planSteps = emptyList()
            liveText = ""
            liveReasoning = ""
            liveRetry = ""
            pendingApproval = null
            liveActive = false
            viewingRunId = null
            currentRunId = id
            lastStepsSize = -1
            seen.clear()
            baselineLoaded = false
            runWasDoneAtLoad = false
            cachedDirs = emptyList()
            lastDirsScan = 0L
            startedAt = System.currentTimeMillis()
            totalSteps = 0
            currentTool = ""
            elapsedSec = 0
            lastError = null
            vdError = null
            status = AgentStatus.RUNNING
            scriptLaunched = true
            launchScript()
            appContext?.let { AgentTaskService.start(it) }
        } catch (e: Exception) {
            lastError = e.message
            status = AgentStatus.FAILED
            AgentTaskService.stop(appContext)
        }
    }

    private fun startTaskReady(t: String) {
        if (stopRequested) {
            // 虚拟屏准备期间用户已终止：不要启动脚本，也不要留下悬挂任务。
            pendingTask = null
            pendingStartedAt = 0
            stopRequested = false
            status = AgentStatus.DONE
            return
        }
        try {
            ensureConversation()
            val keepPaused = pausedByUser
            pendingTask = t
            pendingStartedAt = System.currentTimeMillis()
            stopRequested = false
            pausedByUser = false
            if (!keepPaused) AgentPaths.pausedFile.delete()
            AgentPaths.previewFlagFile.delete()
            AgentPaths.previewSuppressFile.delete()
            AgentPaths.previewFile.delete()
            AgentPaths.previewMetaFile.delete()
            todos = emptyList()
            planSteps = emptyList()
            liveText = ""
            liveReasoning = ""
            liveRetry = ""
            pendingApproval = null
            liveActive = false
            viewingRunId = null
            currentRunId = null
            lastStepsSize = -1
            seen.clear()
            baselineLoaded = false
            runWasDoneAtLoad = false
            // 新 run 目录需要尽快被发现，清空目录缓存让下一次 refresh 立即扫描。
            cachedDirs = emptyList()
            lastDirsScan = 0L
            startedAt = System.currentTimeMillis()
            totalSteps = 0
            currentTool = ""
            elapsedSec = 0
            lastError = null
            vdError = null
            status = if (keepPaused) AgentStatus.PAUSED else AgentStatus.RUNNING
            scriptLaunched = true
            launchScript()
            if (appContext == null) android.util.Log.w("AgentStore", "startTaskReady: appContext is null, skip AgentTaskService")
            appContext?.let { AgentTaskService.start(it) }
        } catch (e: Exception) {
            lastError = e.message
            status = AgentStatus.FAILED
            AgentTaskService.stop(appContext)
        }
    }

    fun pauseTask() {
        if (status != AgentStatus.RUNNING && status != AgentStatus.WAITING && status != AgentStatus.PAUSED) return
        pausedByUser = true
        control("pause")
        AgentPaths.pausedFile.writeText("1")
        status = AgentStatus.PAUSED
        liveActive = false
    }

    fun resumeTask() {
        if (!pausedByUser && status != AgentStatus.PAUSED) return
        pausedByUser = false
        control("resume")
        AgentPaths.pausedFile.delete()
        status = AgentStatus.RUNNING
    }

    fun stop() {
        stopRequested = true
        pausedByUser = false
        scriptLaunched = false
        control("stop")
        AgentPaths.pausedFile.delete()
        markRunStopped()
        // 立即结束 UI 状态：脚本随后停止，刷新时补上最终结果
        status = AgentStatus.DONE
        pendingTask = null
        pendingStartedAt = 0
        liveText = ""
        liveReasoning = ""
        liveRetry = ""
        pendingApproval = null
        liveActive = false
        currentTool = ""
        elapsedSec = 0
        lastError = null
        AgentTaskService.stop(appContext)
        AgentPreviewOverlay.hide()
        // 脚本可能已经卡死/崩溃，直接按网关销毁副屏，避免 Display 泄漏。
        AgentVdRuntime.stopDisplayAsync()
    }

    // 通知栏/悬浮预览等外部入口触发的状态同步（文件通道由 AgentControl 负责写）
    fun setVdRuntimeError(msg: String?) {
        vdError = msg
    }

    fun isTaskActive(): Boolean =
        status == AgentStatus.RUNNING || status == AgentStatus.PAUSED || status == AgentStatus.WAITING

    fun isScriptLaunched(): Boolean = scriptLaunched

    fun onExternalPause() {
        if (!isTaskActive()) return
        pausedByUser = true
        status = AgentStatus.PAUSED
        liveActive = false
    }

    fun onExternalResume() {
        if (status != AgentStatus.PAUSED) return
        pausedByUser = false
        status = AgentStatus.RUNNING
    }

    fun onExternalStop() {
        stopRequested = true
        pausedByUser = false
        scriptLaunched = false
        status = AgentStatus.DONE
        pendingTask = null
        pendingStartedAt = 0
        liveText = ""
        liveReasoning = ""
        liveRetry = ""
        pendingApproval = null
        liveActive = false
        currentTool = ""
        elapsedSec = 0
        AgentPaths.pausedFile.delete()
        markRunStopped()
        AgentVdRuntime.stopDisplayAsync()
    }

    private fun launchScript() {
        if (appContext == null) throw IllegalStateException("AgentStore 未初始化")
        // 单 APK：只使用本包引擎，不再回退到旧 release。
        try {
            EngineController.runScript(AgentPaths.scriptFile)
        } catch (e: Throwable) {
            throw IllegalStateException("本包引擎启动失败：${e.message ?: e.toString()}", e)
        }
    }

    fun openRun(id: String) {
        val dir = File(AgentPaths.runsDir, id)
        if (!dir.exists()) return
        stopRequested = false
        pausedByUser = false
        viewingRunId = id
        currentRunId = id
        lastStepsSize = -1
        clearLive()
        pendingApproval = null
        seen.clear()
        baselineLoaded = false
        val meta = metaOf(dir)
        runWasDoneAtLoad = meta.done
        syncMessages(buildRunMessages(dir))
        status = if (meta.done || File(dir, ".stopped").exists()) AgentStatus.DONE else AgentStatus.IDLE
    }

    /** 历史页只展示最近 50 条，批量删除需要拿到全部 run 目录。 */
    fun allRunIds(): List<String> =
        AgentPaths.runsDir.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()

    /** app 刚启动时 currentRunId 可能还没恢复，用最新的 run 兜底，避免误删当前会话。 */
    fun newestRunId(): String? =
        AgentPaths.runsDir.listFiles { f -> f.isDirectory }?.maxByOrNull { it.lastModified() }?.name

    /** 可批量删除的 run：排除当前查看的与最新的（最新视为当前会话）。 */
    fun deletableRunIds(): List<String> {
        val keep = setOfNotNull(currentRunId, newestRunId())
        return allRunIds().filter { it !in keep }
    }

    /** 删除历史 run（不删当前正在查看/运行的那条），并清理无其他 run 引用的会话文件。 */
    fun deleteRuns(ids: Collection<String>) {
        val keep = setOfNotNull(currentRunId, newestRunId())
        var changed = false
        ids.filter { it !in keep }.forEach { id ->
            try {
                val dir = File(AgentPaths.runsDir, id)
                if (!dir.exists()) return@forEach
                val cid = conversationIdOf(dir)
                if (!dir.deleteRecursively()) return@forEach
                changed = true
                convIdCache.remove(id)
                metaCache.remove(id)
                if (cid.isNotEmpty()) {
                    val stillUsed = AgentPaths.runsDir.listFiles { f -> f.isDirectory }
                        ?.any { conversationIdOf(it) == cid } == true
                    if (!stillUsed) {
                        File(AgentPaths.root, "conversations/$cid.jsonl").delete()
                        File(AgentPaths.root, "conversations/$cid.summary.txt").delete()
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (changed) {
            cachedDirs = emptyList()
            lastDirsScan = 0
            refresh()
        }
    }

    /**
     * 终止时给当前 run 留一个落盘标记：脚本可能已经崩溃/来不及写 checkpoint，
     * 只靠内存状态的话下次打开 App 会看到“运行中/失败”。applyState 会把标记当成已结束。
     */
    private fun markRunStopped() {
        try {
            val dir = currentRunId?.let { File(AgentPaths.runsDir, it) }
                ?: AgentPaths.runsDir.listFiles { f -> f.isDirectory }?.maxByOrNull { it.lastModified() }
                ?: return
            if (!dir.exists()) return
            File(dir, ".stopped").writeText("用户终止 " + System.currentTimeMillis())
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    private fun control(cmd: String) {
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.controlFile.writeText(cmd)
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    /** 运行中追加指令：落盘到 followups.jsonl 供 UI 回显，同时写 control.txt 注入 Agent。 */
    fun queueMessage(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        queuedMessage = t
    }

    fun clearQueuedMessage() {
        queuedMessage = null
    }

    /** 把排队消息立刻转成插话，交给当前轮次。 */
    fun sendQueuedNow() {
        val t = queuedMessage ?: return
        queuedMessage = null
        sendFollowUp(t)
    }

    fun sendFollowUp(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val runId = currentRunId ?: newestRunId() ?: return
        try {
            val f = File(AgentPaths.runsDir, "$runId/followups.jsonl")
            f.parentFile?.mkdirs()
            f.appendText(
                JSONObject()
                    .put("ts", System.currentTimeMillis())
                    .put("text", t)
                    .toString() + "\n"
            )
        } catch (_: Exception) {
        }
        control("followup:" + t.replace("\r", " ").replace("\n", " "))
        messages.add(
            ChatMessage("follow-$runId-${System.currentTimeMillis()}", ChatMessage.Role.USER, t)
        )
        runCache.remove(runId)
    }

    fun decideApproval(allow: Boolean) {
        val ap = pendingApproval ?: return
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.approvalAnswerFile.writeText(ap.id + if (allow) " allow" else " deny")
        } catch (e: Exception) {
            lastError = e.message
        }
        pendingApproval = null
        if (status == AgentStatus.WAITING) status = AgentStatus.RUNNING
    }

    private fun refreshApproval() {
        val file = AgentPaths.approvalFile
        val o = if (file.exists()) readJson(file) else null
        val id = o?.optString("id", "") ?: ""
        if (id.isNotBlank() && (status == AgentStatus.RUNNING || status == AgentStatus.WAITING)) {
            pendingApproval = ApprovalRequest(
                id = id,
                tool = o?.optString("tool", "") ?: "",
                args = o?.optString("args", "") ?: "",
                task = o?.optString("task", "") ?: "",
                step = o?.optInt("step", 0) ?: 0
            )
            status = AgentStatus.WAITING
        } else {
            pendingApproval = null
        }
    }

    // ------------------------------------------------------------ 轮询

    /**
     * JS 侧通过 schedule_add/remove 直接写 schedules.json，并创建 schedule.sync 标记文件。
     * 这里消费标记，重新注册所有闹钟（AlarmManager 只在 Kotlin 侧可用）。
     */
    private fun consumeScheduleSync() {
        val now = System.currentTimeMillis()
        if (now - lastScheduleSyncCheck < 1000) return
        lastScheduleSyncCheck = now
        val marker = File(AgentPaths.root, "schedule.sync")
        if (!marker.exists()) return
        try {
            marker.delete()
            val ctx = appContext ?: return
            AgentScheduleStore.scheduleAll(ctx)
        } catch (_: Exception) {
        }
    }

    /** ask_user：JS 写 ask.json，UI 读出来展示；用户回答写 answer.txt。 */
    private fun refreshQuestion() {
        val now = System.currentTimeMillis()
        if (now - lastAskCheck < 1000) return
        lastAskCheck = now
        val f = File(AgentPaths.root, "ask.json")
        if (!f.exists()) {
            pendingQuestion = null
            return
        }
        try {
            val o = JSONObject(f.readText())
            val id = o.optString("id", "")
            val q = o.optString("question", "")
            if (id.isEmpty() || q.isEmpty()) {
                pendingQuestion = null
                return
            }
            val arr = o.optJSONArray("options")
            val opts = mutableListOf<String>()
            if (arr != null) for (i in 0 until arr.length()) opts.add(arr.optString(i, ""))
            pendingQuestion = AgentQuestion(id, q, opts)
        } catch (_: Exception) {
            pendingQuestion = null
        }
    }

    fun answerQuestion(answer: String) {
        val q = pendingQuestion ?: return
        val a = answer.trim()
        if (a.isEmpty()) return
        try {
            File(AgentPaths.root, "answer.txt").writeText(
                JSONObject().put("id", q.id).put("answer", a).toString()
            )
        } catch (_: Exception) {
        }
        pendingQuestion = null
    }

    /**
     * JS 内核在 :script 进程里直接 startActivity 会被 Android 判定为后台启动而取消；
     * 由前台 UI 进程代劳：发现新的 browser/cmd.json 就拉起内置浏览器窗口。
     */
    private fun maybeOpenBrowser() {
        val now = System.currentTimeMillis()
        if (now - lastBrowserCheck < 1000) return
        lastBrowserCheck = now
        val cmd = File(AgentPaths.root, "browser/cmd.json")
        if (!cmd.exists()) return
        try {
            val id = JSONObject(cmd.readText()).optString("id", "")
            if (id.isEmpty() || id == lastBrowserCmd) return
            lastBrowserCmd = id
            appContext?.let { AgentBrowserActivity.start(it) }
        } catch (_: Exception) {
        }
    }

    /** 后台预热 checkpoint 元数据；只操作普通 Map/文件，不写 Compose 状态。 */
    fun prewarmRuns() {
        try {
            AgentPaths.runsDir.listFiles { f -> f.isDirectory }?.forEach { metaOf(it) }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun refresh() {
        // Compose 状态在后台线程读写必须放进可变快照，否则会与主线程组合快照竞争，
        // 触发 “Reading a state that was created after the snapshot was taken”。
        try {
            Snapshot.withMutableSnapshot { refreshState() }
        } catch (e: Exception) {
            android.util.Log.w("AgentStore", "refresh snapshot failed: ${e.message}")
        }
    }

    private fun refreshState() {
        try {
            if (!AgentPaths.root.exists()) AgentPaths.root.mkdirs()
            if (conversationId.isEmpty()) ensureConversation()
            consumeScheduleSync()
            maybeOpenBrowser()
            refreshQuestion()
            refreshApproval()
            // 扫描 run 目录是 /sdcard FUSE 上最贵的操作之一；运行中 5fps 轮询时
            // 没有必要每次都扫，1s 一次足够发现新 run 与历史变化。
            val now = System.currentTimeMillis()
            if (cachedDirs.isEmpty() || now - lastDirsScan >= 1000) {
                cachedDirs = AgentPaths.runsDir.listFiles { f -> f.isDirectory }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                lastDirsScan = now
                if (runs.size != cachedDirs.size || runs.firstOrNull()?.id != cachedDirs.firstOrNull()?.name) {
                    runs.clear()
                    cachedDirs.take(50).forEach { runs.add(readSummary(it)) }
                }
            }
            val dirs = cachedDirs

            val viewing = viewingRunId?.let { File(AgentPaths.runsDir, it) }
            if (viewing != null && viewing.exists()) {
                syncMessages(buildRunMessages(viewing))
                currentRunId = viewing.name
                applyState(viewing)
                refreshApproval()
                return
            }

            // 对话模式：聚合当前 conversationId 下的所有 run
            val convDirs = dirs.sortedBy { it.lastModified() }.filter { d ->
                val cid = conversationIdOf(d)
                cid == conversationId || (legacyMode && cid.isEmpty()) ||
                    // 暂停在第一步之前时 checkpoint 还不存在，新建的 run 目录也要算进来
                    (cid.isEmpty() && pendingStartedAt > 0 && d.lastModified() >= pendingStartedAt - 1000)
            }
            val latest = convDirs.lastOrNull()
            if (latest == null) {
                if (pendingTask == null) messages.clear()
                return
            }
            val latestTask = metaOf(latest).task
            // 同一句任务重复发送时，旧 run 的 task 会和 pendingTask 相同；
            // 必须同时要求 run 目录晚于本次启动，否则会把新任务的运行态刷回上一次的结果。
            // 暂停在第一步之前时 checkpoint.json 还不存在，此时以“目录是新建的”为准，
            // 不能把它误判成引擎没启动。
            val latestFresh = pendingStartedAt > 0 && latest.lastModified() >= pendingStartedAt - 1000
            val latestMatched = if (latestTask.isEmpty()) latestFresh else (latestTask == pendingTask && latestFresh)
            if (pendingTask != null && !latestMatched) {
                if (!stopRequested && pendingStartedAt > 0 && System.currentTimeMillis() - pendingStartedAt > 10000) {
                    status = AgentStatus.FAILED
                    lastError = "引擎未启动：本包脚本引擎没有创建 run，请重新打开 App 或重装；无需旧 release"
                    liveRetry = ""
                    pendingApproval = null
                    liveActive = false
                }
                return
            }
            pendingTask = null

            val all = mutableListOf<ChatMessage>()
            for (d in convDirs) all.addAll(buildRunMessages(d))
            syncMessages(all)
            currentRunId = latest.name
            applyState(latest)
            refreshApproval()
            maybeAutoResume()
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    /**
     * 崩溃/被杀后的兜底续跑：最新 run 未完成、未被用户停止、未被暂停、
     * 且在 6 小时内，自动续跑一次（每个 run 只尝试一次）。
     */
    private fun maybeAutoResume() {
        if (!config.autoResume) return
        // 注意：进程重启后 applyState 可能把未完成的 run 显示成 RUNNING，
        // 但本进程并没有真的拉起脚本；以 scriptLaunched 为准判断是否在跑。
        if (scriptLaunched) return
        if (pendingTask != null || viewingRunId != null) return
        if (AgentPaths.pausedFile.exists()) return
        if (System.currentTimeMillis() - startedAt < 3000) return
        // 只续跑当前会话里的最新 run，避免把别的会话拉起来。
        val dir = cachedDirs.firstOrNull { d ->
            val cid = conversationIdOf(d)
            cid == conversationId || (legacyMode && cid.isEmpty())
        } ?: return
        if (metaOf(dir).task.isBlank()) return
        if (File(dir, ".stopped").exists()) return
        val meta = metaOf(dir)
        if (meta.done || meta.ended) return
        if (System.currentTimeMillis() - dir.lastModified() > 6 * 60 * 60_000L) return
        val marker = File(AgentPaths.root, "auto-resume.json")
        val last = try { if (marker.exists()) marker.readText().trim() else "" } catch (_: Exception) { "" }
        if (last == dir.name) return
        try {
            marker.writeText(dir.name)
        } catch (_: Exception) {
        }
        android.util.Log.i("AgentStore", "auto-resume ${dir.name}")
        resumeRun(dir.name)
    }

    private fun applyState(dir: File) {
        val meta = metaOf(dir)
        pausedByUser = AgentPaths.pausedFile.exists()
        val stoppedMark = File(dir, ".stopped").exists()
        var isDone = stoppedMark
        if (stoppedMark) status = AgentStatus.DONE
        if (meta.parsed) {
            val done = meta.done
            val ended = meta.ended
            isDone = done || ended || stoppedMark
            if (done || ended) stopRequested = false
            val result = meta.result
            totalSteps = meta.steps ?: totalSteps
            val stale = !done && !ended && !stoppedMark && (System.currentTimeMillis() - dir.lastModified() > 120000)
            when {
                done -> status = if (result.contains("卡死")) AgentStatus.FAILED else AgentStatus.DONE
                ended -> status = if (result.contains("卡死") || result.contains("失败") || result.contains("超时")) AgentStatus.FAILED else AgentStatus.DONE
                stoppedMark -> status = AgentStatus.DONE
                pausedByUser -> status = AgentStatus.PAUSED
                stopRequested -> status = AgentStatus.DONE
                result.contains("卡死") -> status = AgentStatus.FAILED
                stale -> status = AgentStatus.FAILED
                else -> status = AgentStatus.RUNNING
            }
            todos = meta.todos
            planSteps = meta.plan
        }
        if (isDone) {
            scriptLaunched = false
            clearLive()
            pendingApproval = null
        } else {
            readLive(dir)
        }
        if (status == AgentStatus.RUNNING && startedAt > 0) {
            elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        }
    }

    private class LiveCache(val runId: String) {
        var offset = 0L
        var lastStart = 0L
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var retry = ""
    }

    private var liveCache: LiveCache? = null
    private var lastLivePublishAt = 0L

    private fun clearLive() {
        liveText = ""
        liveReasoning = ""
        liveRetry = ""
        liveActive = false
        liveCache = null
        lastLivePublishAt = 0L
    }

    /**
     * 增量读取 stream.jsonl：只解析上次 offset 之后新增的完整行。
     * 旧实现每 120ms 重读并 JSON.parse 整个文件，流越长主线程越卡。
     */
    private fun readLive(dir: File) {
        val streamFile = File(dir, "stream.jsonl")
        if (!streamFile.exists()) {
            clearLive()
            return
        }
        val stepsFile = File(dir, "steps.jsonl")
        val len = streamFile.length()
        var c = liveCache
        if (c == null || c.runId != dir.name || len < c.offset) {
            c = LiveCache(dir.name)
            liveCache = c
        }
        if (len > c.offset) {
            try {
                RandomAccessFile(streamFile, "r").use { raf ->
                    raf.seek(c.offset)
                    while (true) {
                        val line = readCompleteLine(raf) ?: break
                        c.offset = raf.filePointer
                        if (line.isBlank()) continue
                        val o = try { JSONObject(line) } catch (e: Exception) { null } ?: continue
                        if (o.optBoolean("start")) {
                            c.text.setLength(0)
                            c.reasoning.setLength(0)
                            c.retry = ""
                            c.lastStart = o.optLong("t", 0)
                        } else if (o.has("d")) {
                            c.text.append(o.optString("d"))
                        } else if (o.has("r")) {
                            c.reasoning.append(o.optString("r"))
                        } else if (o.has("retry")) {
                            c.retry = "第 ${o.optInt("retry", 1)} 次重试：${o.optString("err", "").take(160)}"
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (c.lastStart >= stepsFile.lastModified() - 100) {
            val changed = c.text.length != liveText.length ||
                c.reasoning.length != liveReasoning.length ||
                c.retry != liveRetry
            val reset = c.text.length < liveText.length || c.reasoning.length < liveReasoning.length
            val now = System.currentTimeMillis()
            // 最多 5fps 发布流式文本：每次发布都会让 Compose 重新布局整段文本，
            // 120ms 一次在长回答上会积累明显掉帧。
            if (changed && (reset || lastLivePublishAt == 0L || now - lastLivePublishAt >= 200)) {
                liveText = c.text.toString()
                liveReasoning = c.reasoning.toString()
                liveRetry = c.retry
                liveActive = c.text.isNotEmpty() || c.reasoning.isNotEmpty() || c.retry.isNotEmpty()
                lastLivePublishAt = now
            }
        } else {
            liveText = ""
            liveReasoning = ""
            liveRetry = ""
            liveActive = false
            lastLivePublishAt = 0L
        }
    }

    /** 只读取完整行；文件末尾尚未写完的半行留到下次轮询。 */
    private fun readCompleteLine(raf: RandomAccessFile): String? {
        val start = raf.filePointer
        val bytes = ByteArrayOutputStream()
        var b = raf.read()
        while (b != -1 && b != '\n'.code) {
            bytes.write(b)
            b = raf.read()
        }
        if (b == -1) {
            raf.seek(start)
            return null
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8).trimEnd('\r')
    }

    /** 仅同步真正变化的条目，避免每轮 refresh 都清空并重加整个列表。 */
    private fun syncMessages(next: List<ChatMessage>) {
        var unchanged = messages.size == next.size
        if (unchanged) {
            for (i in messages.indices) {
                if (messages[i] != next[i]) {
                    unchanged = false
                    break
                }
            }
        }
        if (unchanged) return
        val common = minOf(messages.size, next.size)
        for (i in 0 until common) {
            if (messages[i] != next[i]) messages[i] = next[i]
        }
        while (messages.size > next.size) messages.removeAt(messages.size - 1)
        for (i in messages.size until next.size) messages.add(next[i])
    }

    /** checkpoint 的轻量投影；按 (mtime, length) 缓存，避免每次轮询都解析全部 run 的大 JSON。 */
    private data class RunMeta(
        val exists: Boolean,
        val parsed: Boolean,
        val mtime: Long,
        val length: Long,
        val conversationId: String,
        val task: String,
        val done: Boolean,
        val ended: Boolean,
        val result: String,
        val steps: Int?,
        val todos: List<TodoItem>,
        val plan: List<String>
    )

    private val metaCache = mutableMapOf<String, RunMeta>()

    /** conversationId 在 run 创建后不会改变，命中后无需再 stat/解析 checkpoint。 */
    private fun conversationIdOf(dir: File): String {
        val known = convIdCache[dir.name]
        if (!known.isNullOrEmpty()) return known
        val cid = metaOf(dir).conversationId
        if (cid.isNotEmpty()) convIdCache[dir.name] = cid
        return cid
    }

    @Synchronized
    private fun metaOf(dir: File): RunMeta {
        val file = File(dir, "checkpoint.json")
        val exists = file.exists()
        val mtime = if (exists) file.lastModified() else 0L
        val length = if (exists) file.length() else 0L
        metaCache[dir.name]?.let {
            if (it.exists == exists && it.mtime == mtime && it.length == length) return it
        }
        val cp = if (exists) readJson(file) else null
        val todos = mutableListOf<TodoItem>()
        cp?.optJSONArray("todos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                todos.add(TodoItem(o.optString("content", ""), o.optString("status", "pending")))
            }
        }
        val plan = mutableListOf<String>()
        cp?.optJSONObject("plan")?.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) plan.add(s)
            }
        }
        val meta = RunMeta(
            exists = exists,
            parsed = cp != null,
            mtime = mtime,
            length = length,
            conversationId = cp?.optString("conversationId", "") ?: "",
            task = cp?.optString("task", "") ?: "",
            done = cp?.optBoolean("done", false) ?: false,
            ended = cp?.optBoolean("ended", false) ?: false,
            result = cp?.optString("result", "") ?: "",
            steps = if (cp?.has("steps") == true) cp.optInt("steps", 0) else null,
            todos = todos,
            plan = plan
        )
        metaCache[dir.name] = meta
        return meta
    }

    private data class CachedRun(val sig: String, val msgs: List<ChatMessage>)
    private val runCache = mutableMapOf<String, CachedRun>()

    private fun buildRunMessages(dir: File): List<ChatMessage> {
        val meta = metaOf(dir)
        val done = meta.done
        val ended = meta.ended
        val result = meta.result
        val stepsFile = File(dir, "steps.jsonl")
        val stoppedMark = File(dir, ".stopped").exists()
        val followFile = File(dir, "followups.jsonl")
        val sig = "${stepsFile.length()}|$done|$ended|${result.length}|$stoppedMark|${followFile.length()}"
        runCache[dir.name]?.let { if (it.sig == sig) return it.msgs }

        val list = mutableListOf<ChatMessage>()
        // 技能沉淀提示放在最终回答之后，避免打断任务结论
        val skillNotices = mutableListOf<ChatMessage>()
        val sources = linkedSetOf<String>()
        val task = meta.task
        var lastPromptTokens = 0
        var lastContextWindow = 0
        if (task.isNotBlank()) {
            list.add(ChatMessage("task-${dir.name}", ChatMessage.Role.USER, task))
        }
        if (stepsFile.exists()) {
            var index = 0
            stepsFile.forEachLine { line ->
                index++
                val o = try { JSONObject(line) } catch (e: Exception) { null } ?: return@forEachLine
                val kind = o.optString("kind", "step")
                val ctxWindow = o.optInt("contextWindow", 0)
                if (ctxWindow > 0) lastContextWindow = ctxWindow
                val promptTokens = o.optInt("promptTokens", 0).takeIf { it > 0 }
                    ?: o.optJSONObject("usage")?.optInt("prompt_tokens", 0)?.takeIf { it > 0 }
                if (promptTokens != null) lastPromptTokens = promptTokens
                if (kind == "compaction") {
                    list.add(
                        ChatMessage(
                            id = "c-${dir.name}-$index",
                            role = ChatMessage.Role.COMPACTION,
                            text = "上下文已压缩：摘要 ${o.optInt("summaryLen", 0)} 字，保留最近 ${o.optInt("keptTurns", 2)} 回合（epoch ${o.optInt("epoch", 0)}）"
                        )
                    )
                    return@forEachLine
                }
                if (kind == "retry") {
                    list.add(
                        ChatMessage(
                            id = "r-${dir.name}-$index",
                            role = ChatMessage.Role.RETRY,
                            text = "模型请求失败，正在重试（第 ${o.optInt("attempt", 1)} 次）：${o.optString("err", "")}".take(320)
                        )
                    )
                    return@forEachLine
                }
                if (kind == "skill") {
                    val sname = o.optString("name", "")
                    val sdesc = o.optString("description", "")
                    skillNotices.add(
                        ChatMessage(
                            id = "k-${dir.name}-$index",
                            role = ChatMessage.Role.NOTICE,
                            text = o.optString("text", "").ifBlank {
                                if (sname.isNotBlank()) "已沉淀技能「$sname」：$sdesc" else "已沉淀技能"
                            }
                        )
                    )
                    return@forEachLine
                }
                if (kind != "step") return@forEachLine
                val tool = o.optString("tool", "")
                val thought = o.optString("thought", "")
                val argsObj = o.optJSONObject("args")
                val preview = o.optString("preview", "")
                collectSources(tool, argsObj, sources)
                Regex("""https?://[^\s`"'<>）)]+""").findAll(preview).forEach {
                    sources.add(it.value.trimEnd('.', ',', '。', '）', ')', '”', '’'))
                }
                Regex("""/sdcard/[^\s`"']+""").findAll(preview).forEach {
                    sources.add(it.value.trimEnd('.', ',', '。', '）', ')', '”', '’'))
                }
                currentTool = tool
                // 模型在步骤中写的文字放到对话里，步骤卡片只留工具动作与返回
                if (thought.isNotBlank()) {
                    list.add(
                        ChatMessage(
                            id = "say-${dir.name}-$index",
                            role = ChatMessage.Role.SAY,
                            text = thought
                        )
                    )
                }
                list.add(
                    ChatMessage(
                        id = "s-${dir.name}-$index",
                        role = ChatMessage.Role.STEP,
                        text = "",
                        step = StepItem(
                            step = o.optInt("step", index),
                            tool = tool,
                            thought = thought,
                            ok = o.optBoolean("ok", true),
                            err = o.optString("err", ""),
                            args = argsObj?.toString() ?: "",
                            preview = o.optString("preview", ""),
                            result = o.optString("result", ""),
                            pkg = o.optString("pkgAfter", ""),
                            changed = o.optBoolean("changed", false),
                            reasoning = o.optString("reasoning", "")
                        )
                    )
                )
            }
        }
        // 运行中追加的指令：出现在步骤之后、最终回答之前
        if (followFile.exists()) {
            var followIndex = 0
            followFile.forEachLine { line ->
                val o = try { JSONObject(line) } catch (e: Exception) { null } ?: return@forEachLine
                val t = o.optString("text", "")
                if (t.isNotBlank()) {
                    followIndex++
                    list.add(ChatMessage("follow-${dir.name}-$followIndex", ChatMessage.Role.USER, t))
                }
            }
        }
        val finalSources = linkedSetOf<String>()
        if (result.isNotEmpty()) {
            Regex("""/sdcard/[^\s`"'）)]+""").findAll(result).forEach {
                finalSources.add(it.value.trimEnd('.', ',', '。', '）', ')', '”', '’'))
            }
            Regex("""https?://[^\s`"'<>）)]+""").findAll(result).forEach {
                finalSources.add(it.value.trimEnd('.', ',', '。', '）', ')', '”', '’'))
            }
        }
        finalSources.addAll(sources)
        if ((done || ended) && result.isNotEmpty()) {
            list.add(
                ChatMessage(
                    "final-${dir.name}",
                    ChatMessage.Role.FINAL,
                    result,
                    sources = finalSources.take(8).toList(),
                    promptTokens = lastPromptTokens,
                    contextWindow = lastContextWindow
                )
            )
        } else if (stoppedMark) {
            // 脚本被强杀/来不及写结论时，用 .stopped 标记给用户一个明确收尾。
            list.add(ChatMessage("final-${dir.name}", ChatMessage.Role.FINAL, "用户终止"))
        }
        list.addAll(skillNotices)
        runCache[dir.name] = CachedRun(sig, list)
        return list
    }

    private fun collectSources(tool: String, args: JSONObject?, out: MutableSet<String>) {
        fun add(p: String?) {
            val s = p?.trim().orEmpty()
            if (s.startsWith("/sdcard/") || s.startsWith("http://") || s.startsWith("https://")) out.add(s)
        }
        when (tool) {
            "file_read", "file_stat", "file_write", "file_append", "file_edit", "file_remove", "file_mkdir" -> add(args?.optString("path"))
            "file_list", "file_search" -> add(args?.optString("dir"))
            "file_move", "file_copy" -> {
                add(args?.optString("from"))
                add(args?.optString("to"))
            }
            "web_fetch" -> {
                add(args?.optString("url"))
                val ua = args?.optJSONArray("urls")
                if (ua != null) {
                    for (i in 0 until ua.length()) add(ua.optString(i))
                }
            }
            "file_batch" -> {
                val ops = args?.optJSONArray("ops")
                if (ops != null) {
                    for (i in 0 until ops.length()) {
                        val o = ops.optJSONObject(i) ?: continue
                        add(o.optString("path"))
                        add(o.optString("from"))
                        add(o.optString("to"))
                    }
                }
            }
        }
    }

    private fun readSummary(dir: File): RunSummary {
        val meta = metaOf(dir)
        return RunSummary(
            id = dir.name,
            dir = dir,
            task = meta.task,
            done = meta.done,
            steps = meta.steps ?: 0,
            result = meta.result,
            modified = dir.lastModified()
        )
    }

    private val THINKING_LEVELS = listOf("default", "none", "auto", "minimal", "low", "medium", "high", "xhigh", "max")

    private fun readJson(file: File): JSONObject? {
        return try {
            if (!file.exists()) null else JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }
}
