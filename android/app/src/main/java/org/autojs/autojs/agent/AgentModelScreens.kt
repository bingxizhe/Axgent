package org.autojs.autojs.agent

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ModelTestResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long = 0,
    val contextWindow: Int = 0
)

private const val ONE_PIXEL_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

private val modelGreen = Color(0xFF2E7D32)
private val modelAmber = Color(0xFFEF6C00)

private fun endpointBase(base: String): String = base.trim().trimEnd('/')

private fun chatEndpoint(base: String): String {
    val b = endpointBase(base)
    return when {
        b.endsWith("/chat/completions") -> b
        Regex(""".*/v\d+$""").matches(b) -> "$b/chat/completions"
        else -> "$b/v1/chat/completions"
    }
}

private fun modelsEndpoint(base: String): String {
    var b = endpointBase(base)
    if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions")
    return when {
        b.endsWith("/models") -> b
        Regex(""".*/v\d+$""").matches(b) -> "$b/models"
        else -> "$b/v1/models"
    }
}

private fun readResponse(conn: HttpURLConnection): String {
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    if (stream == null) return ""
    return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
}

private fun isValidApiHost(value: String): Boolean {
    val v = value.trim()
    if (!v.startsWith("http://") && !v.startsWith("https://")) return false
    return try {
        val u = URL(v)
        u.host?.isNotBlank() == true
    } catch (_: Exception) {
        false
    }
}

private fun protocolLabel(protocol: String): String = when (protocol) {
    "openai" -> "OpenAI 兼容"
    "responses" -> "OpenAI Responses"
    "anthropic" -> "Anthropic Messages"
    "gemini" -> "Gemini"
    else -> "自定义"
}

private fun relativeCheckTime(ts: Long): String {
    if (ts <= 0) return "未检测"
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "刚刚检测"
        d < 3_600_000 -> "${d / 60_000} 分钟前检测"
        d < 86_400_000 -> "${d / 3_600_000} 小时前检测"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

data class RemoteModel(val id: String, val name: String = "", val contextWindow: Int = 0)
data class ModelDiscoveryResult(val models: List<RemoteModel> = emptyList(), val error: String? = null)

/** 解析各家 /models 的上下文字段；OpenRouter/Gemini/Anthropic 代理字段名不同。 */
private fun contextWindowOf(o: JSONObject): Int {
    val keys = listOf(
        "context_length", "context_window", "max_context_length", "max_context_window",
        "max_input_tokens", "inputTokenLimit", "input_token_limit", "max_input_length",
        "max_model_len"
    )
    for (k in keys) {
        val n = o.optInt(k, 0)
        if (n > 0) return n
    }
    for (nest in listOf("limits", "meta", "capabilities")) {
        val child = o.optJSONObject(nest) ?: continue
        for (k in keys) {
            val n = child.optInt(k, 0)
            if (n > 0) return n
        }
    }
    return 0
}

/** 从服务商 /models 拉取指定模型的上下文窗口；失败返回 0，不影响连通性检测。 */
private fun fetchContextWindow(baseUrl: String, apiKey: String, protocol: String, model: String): Int {
    return try {
        discoverModels(baseUrl, apiKey, protocol).models.firstOrNull { it.id == model.trim() }?.contextWindow ?: 0
    } catch (_: Exception) {
        0
    }
}

private fun responsesEndpoint(base: String): String {
    val b = endpointBase(base)
    return when {
        b.endsWith("/responses") -> b
        Regex(""".*/v\d+$""").matches(b) -> "$b/responses"
        else -> "$b/v1/responses"
    }
}

private fun anthropicEndpoint(base: String, suffix: String): String {
    val b = endpointBase(base)
    return when {
        b.endsWith("/$suffix") -> b
        Regex(""".*/v\d+$""").matches(b) -> "$b/$suffix"
        else -> "$b/v1/$suffix"
    }
}

private fun geminiBase(base: String): String {
    val b = endpointBase(base)
    return if (Regex(""".*/v\d+(beta)?$""").matches(b)) b else "$b/v1beta"
}

private fun authHeaders(protocol: String, apiKey: String): Map<String, String> {
    val key = apiKey.trim()
    return when (protocol) {
        "anthropic" -> mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "x-api-key" to key,
            "anthropic-version" to "2023-06-01"
        )
        "gemini" -> mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "x-goog-api-key" to key
        )
        else -> mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "Authorization" to "Bearer $key"
        )
    }
}

private fun openPost(url: String, headers: Map<String, String>, body: String): HttpURLConnection {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 12000
    conn.readTimeout = 25000
    conn.doOutput = true
    headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
    conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
    return conn
}

private fun successIfStream(conn: HttpURLConnection, markers: List<String>, started: Long, model: String): ModelTestResult {
    val code = conn.responseCode
    if (code !in 200..299) {
        val err = readResponse(conn).replace(Regex("\\s+"), " ").take(500)
        conn.disconnect()
        return ModelTestResult(false, "HTTP $code：${err.ifBlank { "无错误详情" }}")
    }
    var sawData = false
    var matched = false
    try {
        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line: String?
            var lines = 0
            while (reader.readLine().also { line = it } != null && lines < 60) {
                lines++
                val s = line ?: continue
                if (!s.startsWith("data:")) continue
                sawData = true
                if (markers.any { s.contains(it) }) {
                    matched = true
                    break
                }
            }
        }
    } catch (_: Exception) {
    }
    conn.disconnect()
    if (!sawData || !matched) {
        return ModelTestResult(false, "接口返回 200，但没有收到有效流式事件（请确认协议与地址匹配）")
    }
    val ms = System.currentTimeMillis() - started
    return ModelTestResult(true, "连接成功 · $model · ${ms}ms", ms)
}

/** 按协议探测：openai chat / OpenAI Responses / Anthropic Messages / Gemini generateContent。 */
fun testModelConnection(
    baseUrl: String,
    apiKey: String,
    model: String,
    vision: Boolean,
    protocol: String = "openai"
): ModelTestResult {
    val started = System.currentTimeMillis()
    if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
        return ModelTestResult(false, "baseUrl、apiKey、model 不能为空")
    }
    return try {
        val result = when (protocol) {
            "anthropic" -> {
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", "ping"))
                if (vision) {
                    content.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "base64").put("media_type", "image/png").put("data", ONE_PIXEL_PNG)
                        )
                    )
                }
                val body = JSONObject()
                    .put("model", model.trim())
                    .put("max_tokens", 16)
                    .put("stream", true)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .toString()
                openPost(anthropicEndpoint(baseUrl, "messages"), authHeaders(protocol, apiKey), body)
                    .let { successIfStream(it, listOf("content_block", "message_start"), started, model.trim()) }
            }
            "gemini" -> {
                val parts = JSONArray().put(JSONObject().put("text", "ping"))
                if (vision) {
                    parts.put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", "image/png").put("data", ONE_PIXEL_PNG)
                        )
                    )
                }
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                    .toString()
                val url = "${geminiBase(baseUrl)}/models/${model.trim()}:streamGenerateContent?alt=sse"
                openPost(url, authHeaders(protocol, apiKey), body)
                    .let { successIfStream(it, listOf("candidates"), started, model.trim()) }
            }
            "responses" -> {
                val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", "ping"))
                if (vision) {
                    content.put(
                        JSONObject().put("type", "input_image")
                            .put("image_url", "data:image/png;base64,$ONE_PIXEL_PNG")
                    )
                }
                val body = JSONObject()
                    .put("model", model.trim())
                    .put("stream", true)
                    .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .toString()
                openPost(responsesEndpoint(baseUrl), authHeaders(protocol, apiKey), body)
                    .let { successIfStream(it, listOf("response."), started, model.trim()) }
            }
            else -> testOpenAiChat(baseUrl, apiKey, model, vision, started)
        }
        val ctx = fetchContextWindow(baseUrl, apiKey, protocol, model)
        if (ctx > 0) result.copy(contextWindow = ctx) else result
    } catch (e: Exception) {
        ModelTestResult(false, "连接失败：${e.message ?: e.toString()}")
    }
}

private fun testOpenAiChat(baseUrl: String, apiKey: String, model: String, vision: Boolean, started: Long): ModelTestResult {
    val auth = "Bearer ${apiKey.trim()}"
    try {
        val mc = URL(modelsEndpoint(baseUrl)).openConnection() as HttpURLConnection
        mc.requestMethod = "GET"
        mc.connectTimeout = 10000
        mc.readTimeout = 10000
        mc.setRequestProperty("Accept", "application/json")
        mc.setRequestProperty("Authorization", auth)
        val code = mc.responseCode
        if (code == 401 || code == 403) {
            mc.disconnect()
            return ModelTestResult(false, "API Key 无效或被拒绝（HTTP $code）")
        }
        mc.disconnect()
    } catch (_: Exception) {
    }

    fun attempt(rawImage: Boolean): ModelTestResult {
        val messages = JSONArray()
        if (vision) {
            val imageUrl = if (rawImage) ONE_PIXEL_PNG else "data:image/png;base64,$ONE_PIXEL_PNG"
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", "ping"))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageUrl)))
            messages.put(JSONObject().put("role", "user").put("content", content))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", "ping"))
        }
        val body = JSONObject()
            .put("model", model.trim())
            .put("messages", messages)
            .put("max_tokens", 8)
            .put("stream", true)
            .toString()
        return try {
            val conn = openPost(chatEndpoint(baseUrl), authHeaders("openai", apiKey), body)
            successIfStream(conn, listOf("\"choices\"", "[DONE]"), started, model.trim())
        } catch (e: Exception) {
            ModelTestResult(false, "连接失败：${e.message ?: e.toString()}")
        }
    }

    val first = attempt(false)
    if (!vision || first.ok) return first
    val raw = attempt(true)
    if (raw.ok) return raw.copy(message = raw.message + "（raw base64）")
    return ModelTestResult(false, "data URI 失败：${first.message}；raw base64 失败：${raw.message}")
}

/** 按协议获取服务商的可用模型列表，用于添加模型时自动展示可选列表。 */
fun discoverModels(baseUrl: String, apiKey: String, protocol: String): ModelDiscoveryResult {
    if (baseUrl.isBlank() || apiKey.isBlank()) return ModelDiscoveryResult(error = "请先填写 API 地址与 API 密钥")

    fun attempt(url: String, headers: Map<String, String>): List<RemoteModel> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = readResponse(conn).replace(Regex("\\s+"), " ").take(300)
            conn.disconnect()
            throw IllegalStateException("HTTP $code：${err.ifBlank { "无法获取模型列表" }}")
        }
        val text = readResponse(conn)
        conn.disconnect()
        val root = JSONObject(text)
        val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        val out = mutableListOf<RemoteModel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rawId = o.optString("id", "").ifBlank { o.optString("name", "") }
            if (rawId.isBlank()) continue
            if (protocol == "gemini") {
                val id = rawId.removePrefix("models/")
                val methods = o.optJSONArray("supportedGenerationMethods")
                if (methods != null && (0 until methods.length()).none {
                        methods.optString(it, "") == "generateContent"
                    }) continue
                out.add(RemoteModel(id, o.optString("displayName", id), contextWindowOf(o)))
            } else {
                out.add(RemoteModel(rawId, o.optString("display_name", rawId), contextWindowOf(o)))
            }
        }
        return out.distinctBy { it.id }.sortedBy { it.id }
    }

    val headers = when (protocol) {
        "anthropic" -> mapOf("Accept" to "application/json", "x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01")
        "gemini" -> mapOf("Accept" to "application/json", "x-goog-api-key" to apiKey.trim())
        else -> mapOf("Accept" to "application/json", "Authorization" to "Bearer ${apiKey.trim()}")
    }
    val url = when (protocol) {
        "anthropic" -> anthropicEndpoint(baseUrl, "models")
        "gemini" -> "${geminiBase(baseUrl)}/models"
        else -> modelsEndpoint(baseUrl)
    }
    return try {
        val list = attempt(url, headers)
        if (list.isEmpty()) ModelDiscoveryResult(error = "服务商未返回可用模型")
        else ModelDiscoveryResult(models = list)
    } catch (e: Exception) {
        // 部分 Anthropic 兼容代理不提供 /v1/models，但同一主机仍暴露 OpenAI /v1/models。
        if (protocol == "anthropic") {
            val origin = endpointBase(baseUrl).replace(Regex("""/anthropic$"""), "")
            try {
                val list = attempt(
                    modelsEndpoint(origin),
                    mapOf("Accept" to "application/json", "Authorization" to "Bearer ${apiKey.trim()}")
                )
                if (list.isNotEmpty()) return ModelDiscoveryResult(models = list)
            } catch (_: Exception) {
            }
        }
        ModelDiscoveryResult(error = "获取模型列表失败：${e.message ?: e.toString()}")
    }
}



// ---------------------------------------------------------------- 模型管理

@Composable
fun ModelManagerScreen(onBack: () -> Unit, onAddModel: (Boolean) -> Unit) {
    val cfg = AgentStore.config
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // 进入模型管理时刷新过期的活动模型状态（10 分钟内不重复请求）。
    LaunchedEffect(Unit) {
        if (refreshing) return@LaunchedEffect
        val targets = listOfNotNull(
            cfg.chatModels.firstOrNull { it.id == cfg.activeModelId }?.let { it to false },
            cfg.visionModels.firstOrNull { it.id == cfg.activeVisionModelId }?.let { it to true }
        ).filter { (m, _) -> System.currentTimeMillis() - m.lastCheckedAt > 10 * 60_000L }
        if (targets.isEmpty()) return@LaunchedEffect
        refreshing = true
        for ((profile, vision) in targets) {
            val protocol = cfg.providers.firstOrNull { it.id == profile.providerId }?.protocol ?: profile.protocol
            val result = withContext(Dispatchers.IO) {
                testModelConnection(profile.baseUrl, profile.apiKey, profile.model, vision, protocol)
            }
            AgentStore.updateModelCheck(
                profile.id, vision, result.ok, result.message, result.latencyMs, result.contextWindow
            )
        }
        refreshing = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "模型",
            subtitle = "服务商：${cfg.providers.size} 个 · 对话模型：${cfg.chatModels.size} 个 · 视觉模型：${cfg.visionModels.size} 个",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ModelSection(
                title = "对话模型",
                models = cfg.chatModels,
                providers = cfg.providers,
                vision = false,
                activeId = cfg.activeModelId,
                emptyText = "暂无对话模型，点击添加模型开始使用",
                onAdd = { onAddModel(false) },
                onSelect = { AgentStore.selectModel(it) },
                onDelete = { AgentStore.deleteModel(it, false) }
            )
            ModelSection(
                title = "视觉模型",
                models = cfg.visionModels,
                providers = cfg.providers,
                vision = true,
                activeId = cfg.activeVisionModelId,
                emptyText = "暂无视觉模型；视觉查看会使用主模型或 OCR",
                onAdd = { onAddModel(true) },
                onSelect = { AgentStore.selectVisionModel(it) },
                onDelete = { AgentStore.deleteModel(it, true) }
            )
            Text(
                "对话模型和视觉模型共用同一套服务商连接；模型库可随时切换当前模型。",
                style = MaterialTheme.typography.bodySmall,
                color = AxTheme.colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ModelSection(
    title: String,
    models: List<ModelProfile>,
    providers: List<ProviderProfile>,
    vision: Boolean,
    activeId: String,
    emptyText: String,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val enabledProviderIds = providers.filter { it.enabled }.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AxTheme.colors.text)
            Spacer(Modifier.weight(1f))
            AxPill("＋ 添加", onAdd)
        }
        if (models.isEmpty()) {
            AxGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                tier = AxGlassTier.Card,
                shape = RoundedCornerShape(AxRadius.lg)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = AxTheme.colors.textFaint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AxTheme.colors.textMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    AxPill("添加模型", onAdd, primary = true)
                }
            }
        } else {
            models.forEach { profile ->
                ModelRow(
                    profile = profile,
                    active = profile.id == activeId,
                    vision = vision,
                    available = profile.providerId.isBlank() || profile.providerId in enabledProviderIds,
                    onSelect = { onSelect(profile.id) },
                    onDelete = { onDelete(profile.id) }
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    profile: ModelProfile,
    active: Boolean,
    vision: Boolean,
    available: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    val colors = AxTheme.colors
    AxGlassSurface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = available, onClick = onSelect),
        tier = AxGlassTier.Card,
        shape = RoundedCornerShape(AxRadius.lg)
    ) {
        Row(
            Modifier.padding(start = 13.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(if (active) colors.primaryContainer else colors.toolContainers[7], RoundedCornerShape(AxRadius.sm)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (active) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    contentDescription = null,
                    tint = if (active) colors.onPrimaryContainer else colors.onToolContainers[7],
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.model,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false)
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text("当前", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                }
                Text(
                    profile.providerName.ifBlank { profile.baseUrl },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AxStatusDot(
                        when {
                            !available -> colors.warning
                            profile.verified -> colors.success
                            else -> colors.danger
                        },
                        6.dp
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (!available) "服务商已停用" else buildString {
                            append(if (profile.verified) "可用" else "不可用")
                            if (profile.lastCheckedAt > 0) {
                                append(" · ")
                                append(relativeCheckTime(profile.lastCheckedAt))
                                if (profile.lastLatencyMs > 0) append(" · ${profile.lastLatencyMs}ms")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !available -> colors.warning
                            profile.verified -> colors.success
                            else -> colors.danger
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (profile.contextWindow > 0) {
                    Text(
                        "上下文 ${AgentModelContext.label(profile.contextWindow)} · " +
                            (if (profile.contextSource == "provider") "服务商提供" else "估算"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
                Spacer(Modifier.width(4.dp))
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = AxLang.t("检测模型"),
                    tint = colors.textFaint,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            testing = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    testModelConnection(profile.baseUrl, profile.apiKey, profile.model, vision, profile.protocol)
                                }
                                AgentStore.updateModelCheck(
                                    profile.id, vision, r.ok, r.message, r.latencyMs, r.contextWindow
                                )
                                testing = false
                                Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(8.dp)
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = AxLang.t("删除模型"),
                tint = colors.textGhost,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- 模型配置页

private data class FormState(
    val presetId: String = "",
    val providerId: String = "",
    val custom: Boolean = false,
    val protocol: String = "openai",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val description: String = ""
)

@Composable
fun ModelConfigScreen(initialVision: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val cfg = AgentStore.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var form by remember { mutableStateOf<FormState?>(null) }
    var purposeVision by rememberSaveable { mutableStateOf(initialVision) }
    var checking by remember { mutableStateOf(false) }
    var checkProgress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddProvider by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var baseTouched by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var remoteModels by remember { mutableStateOf<List<RemoteModel>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var modelQuery by remember { mutableStateOf("") }
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fallbackUsed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }

    fun selectPreset(preset: ProviderPreset) {
        error = null
        baseTouched = false
        remoteModels = emptyList()
        discoveryError = null
        selectedModelIds = emptySet()
        modelQuery = ""
        fallbackUsed = false
        form = FormState(
            presetId = preset.id,
            protocol = preset.protocol,
            name = preset.name,
            baseUrl = preset.baseUrl,
            description = preset.description
        )
    }

    fun selectProvider(provider: ProviderProfile) {
        error = null
        baseTouched = false
        remoteModels = emptyList()
        discoveryError = null
        selectedModelIds = emptySet()
        modelQuery = ""
        fallbackUsed = false
        form = FormState(
            presetId = provider.presetId,
            providerId = provider.id,
            custom = provider.custom,
            protocol = provider.protocol,
            name = provider.name,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey
        )
    }

    val presets = remember(query) { ProviderPresets.search(query) }
    val existing = remember(query, cfg.providers) {
        val q = query.trim().lowercase()
        cfg.providers.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.baseUrl.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AxPageHeader(
            title = "模型服务",
            subtitle = "选择服务商后填写连接信息；对话与视觉模型共用同一套服务商配置。",
            onBack = onBack
        )

        if (form == null) {
            Box(Modifier.padding(horizontal = AxSpace.gutter)) {
                AxSearchField(query, { query = it }, "搜索模型平台")
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (existing.isNotEmpty()) {
                        item {
                            Text(
                                "已添加",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(existing, key = { "p-${it.id}" }) { p ->
                            ProviderListItem(
                                name = p.name,
                                subtitle = p.baseUrl.ifBlank { p.protocol },
                                selected = false,
                                badge = if (p.enabled) "已添加" else "已停用",
                                onClick = { selectProvider(p) },
                                enabled = p.enabled,
                                onToggleEnabled = { AgentStore.setProviderEnabled(p.id, it) },
                                onDelete = { pendingDeleteProvider = p }
                            )
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                    }
                    item {
                        Text(
                            "内置服务商",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (presets.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的服务商",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }
                    items(presets, key = { "s-${it.id}" }) { preset ->
                        ProviderListItem(
                            name = preset.name,
                            subtitle = preset.baseUrl.ifBlank { preset.description },
                            selected = false,
                            badge = protocolLabel(preset.protocol),
                            onClick = { selectPreset(preset) }
                        )
                    }
                }
            }
        } else {
            val f = form!!
            val visibleModels = remember(remoteModels, modelQuery) {
                val q = modelQuery.trim().lowercase()
                if (q.isEmpty()) remoteModels
                else remoteModels.filter { it.id.lowercase().contains(q) || it.name.lowercase().contains(q) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderAvatar(f.name, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            protocolLabel(f.protocol),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { form = null; error = null }) { Text("更换服务商") }
                }

                OutlinedTextField(
                    value = f.name,
                    onValueChange = { form = f.copy(name = it) },
                    label = { Text("服务商名称") },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                )

                if (f.protocol != "openai") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = modelAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, modelAmber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = modelAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "当前协议：${when (f.protocol) {
                                    "responses" -> "OpenAI Responses"
                                    "anthropic" -> "Anthropic Messages"
                                    "gemini" -> "Gemini generateContent"
                                    else -> f.protocol
                                }}。请确认 API 地址与该协议匹配。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = f.baseUrl,
                    onValueChange = { form = f.copy(baseUrl = it); baseTouched = true },
                    label = { Text("API 地址") },
                    placeholder = { Text("Base URL：https://example.com") },
                    singleLine = true,
                    isError = baseTouched && !isValidApiHost(f.baseUrl),
                    supportingText = {
                        when {
                            f.baseUrl.isBlank() -> Text("请输入 Base URL")
                            baseTouched && !isValidApiHost(f.baseUrl) -> Text("请输入有效的 HTTP 或 HTTPS 地址")
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = f.apiKey,
                    onValueChange = { form = f.copy(apiKey = it) },
                    label = { Text("API 密钥") },
                    placeholder = { Text("输入 API 密钥") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                )

                LaunchedEffect(f.baseUrl, f.apiKey, f.protocol, f.presetId, retryTick) {
                    if (f.apiKey.isBlank() || !isValidApiHost(f.baseUrl)) {
                        remoteModels = emptyList()
                        discoveryError = null
                        selectedModelIds = emptySet()
                        return@LaunchedEffect
                    }
                    delay(700)
                    discovering = true
                    discoveryError = null
                    selectedModelIds = emptySet()
                    val result = withContext(Dispatchers.IO) {
                        discoverModels(f.baseUrl.trim(), f.apiKey.trim(), f.protocol)
                    }
                    val presetModels = withContext(Dispatchers.IO) {
                        ProviderModelCatalog.models(context, f.presetId)
                    }
                    if (result.models.isNotEmpty()) {
                        remoteModels = result.models
                        fallbackUsed = false
                    } else {
                        remoteModels = presetModels.map { RemoteModel(it) }
                        fallbackUsed = presetModels.isNotEmpty()
                        discoveryError = result.error
                    }
                    discovering = false
                }

                Text("模型列表", style = MaterialTheme.typography.titleSmall)
                if (discovering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在获取模型列表…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (discoveryError != null && remoteModels.isEmpty()) {
                    Text(
                        discoveryError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { retryTick++ }) { Text("重试") }
                } else if (discoveryError != null) {
                    Text(
                        discoveryError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { retryTick++ }) { Text("重新获取") }
                }

                if (remoteModels.isNotEmpty()) {
                    OutlinedTextField(
                        value = modelQuery,
                        onValueChange = { modelQuery = it },
                        placeholder = { Text("搜索模型 ID 或名称") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已选 ${selectedModelIds.size} / ${remoteModels.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = !checking,
                            onClick = {
                                selectedModelIds =
                                    if (visibleModels.all { it.id in selectedModelIds }) {
                                        selectedModelIds - visibleModels.map { it.id }.toSet()
                                    } else {
                                        selectedModelIds + visibleModels.map { it.id }.toSet()
                                    }
                            }
                        ) {
                            Text(if (visibleModels.all { it.id in selectedModelIds }) "取消全选" else "全选")
                        }
                    }
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(visibleModels, key = { it.id }) { m ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !checking) {
                                        selectedModelIds = if (m.id in selectedModelIds) {
                                            selectedModelIds - m.id
                                        } else {
                                            selectedModelIds + m.id
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = m.id in selectedModelIds,
                                    onCheckedChange = null,
                                    enabled = !checking
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (m.name.isNotBlank() && m.name != m.id) {
                                        Text(
                                            m.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (m.contextWindow > 0) {
                                        Text(
                                            "上下文 ${AgentModelContext.label(m.contextWindow)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        if (fallbackUsed) "无法从服务商获取列表，已展示内置预设模型。" else "模型列表来自服务商接口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("模型用途", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !purposeVision,
                        onClick = { purposeVision = false },
                        label = { Text("对话") },
                        enabled = !checking
                    )
                    FilterChip(
                        selected = purposeVision,
                        onClick = { purposeVision = true },
                        label = { Text("视觉") },
                        enabled = !checking
                    )
                }

                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            checkProgress.ifBlank { "正在检测模型可用性…" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Text(
                    "检测会向所选模型发送真实请求，可能产生少量费用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (f.providerId.isNotBlank()) {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            error = null
                            if (!isValidApiHost(f.baseUrl)) {
                                baseTouched = true
                                error = if (f.baseUrl.isBlank()) "请输入 Base URL" else "请输入有效的 HTTP 或 HTTPS 地址"
                                return@TextButton
                            }
                            val existing = cfg.providers.firstOrNull { it.id == f.providerId }
                            if (existing == null) {
                                error = "服务商已不存在，请重新选择"
                                return@TextButton
                            }
                            AgentStore.updateProvider(
                                existing.copy(
                                    name = f.name.trim().ifBlank { existing.name },
                                    baseUrl = f.baseUrl.trim(),
                                    apiKey = f.apiKey.trim(),
                                    protocol = f.protocol
                                )
                            )
                            Toast.makeText(context, "服务商配置已保存", Toast.LENGTH_SHORT).show()
                            form = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("仅保存服务商配置（不添加模型）")
                    }
                }

                Button(
                    enabled = !checking && selectedModelIds.isNotEmpty(),
                    onClick = {
                        error = null
                        val key = f.apiKey.trim()
                        when {
                            !isValidApiHost(f.baseUrl) -> {
                                baseTouched = true
                                error = if (f.baseUrl.isBlank()) "请输入 Base URL" else "请输入有效的 HTTP 或 HTTPS 地址"
                            }
                            key.isBlank() -> error = "API 密钥不能为空"
                            selectedModelIds.isEmpty() -> error = "请选择至少一个模型"
                        }
                        if (error != null) return@Button
                        checking = true
                        scope.launch {
                            val providerName = f.name.ifBlank { f.baseUrl }
                            val providerId = f.providerId.ifBlank {
                                cfg.providers.firstOrNull { it.presetId == f.presetId && f.presetId.isNotBlank() }?.id
                                    ?: "pv-${System.currentTimeMillis()}-${(0..999).random()}"
                            }
                            val provider = ProviderProfile(
                                id = providerId,
                                name = providerName,
                                baseUrl = f.baseUrl.trim(),
                                apiKey = key,
                                protocol = f.protocol,
                                presetId = f.presetId,
                                custom = f.custom || f.presetId.isBlank()
                            )
                            if (cfg.providers.any { it.id == providerId }) {
                                AgentStore.updateProvider(provider)
                            } else {
                                AgentStore.addProvider(provider)
                            }
                            val targets = remoteModels.filter { it.id in selectedModelIds }
                            var okCount = 0
                            var index = 0
                            for (m in targets) {
                                index++
                                checkProgress = "正在检测 $index/${targets.size}：${m.id}"
                                val result = withContext(Dispatchers.IO) {
                                    testModelConnection(
                                        provider.baseUrl,
                                        provider.apiKey,
                                        m.id,
                                        purposeVision,
                                        provider.protocol
                                    )
                                }
                                val now = System.currentTimeMillis()
                                val providerCtx = if (m.contextWindow > 0) m.contextWindow else result.contextWindow
                                val ctx = if (providerCtx > 0) providerCtx
                                else AgentModelContext.estimate(m.id, provider.protocol, provider.presetId)
                                AgentStore.addModel(
                                    ModelProfile(
                                        id = "m-${now}-${(0..999).random()}",
                                        name = m.name.ifBlank { m.id },
                                        baseUrl = provider.baseUrl,
                                        apiKey = provider.apiKey,
                                        model = m.id,
                                        verified = result.ok,
                                        providerId = provider.id,
                                        providerName = provider.name,
                                        protocol = provider.protocol,
                                        lastCheckedAt = now,
                                        lastLatencyMs = result.latencyMs,
                                        lastMessage = result.message,
                                        contextWindow = ctx,
                                        contextSource = if (providerCtx > 0) "provider" else "estimated"
                                    ),
                                    purposeVision
                                )
                                if (result.ok) okCount++
                            }
                            checking = false
                            checkProgress = ""
                            Toast.makeText(
                                context,
                                "已添加 ${targets.size} 个模型（可用 $okCount）",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加所选模型${if (selectedModelIds.isEmpty()) "" else "（${selectedModelIds.size}）"}")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Surface(tonalElevation = 2.dp) {
            Button(
                onClick = { showAddProvider = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加服务商")
            }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(
            onDismiss = { showAddProvider = false },
            onCreated = { provider ->
                showAddProvider = false
                selectProvider(provider)
                Toast.makeText(context, "已添加服务商", Toast.LENGTH_SHORT).show()
            }
        )
    }

    pendingDeleteProvider?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProvider = null },
            title = { Text("删除服务商？") },
            text = { Text("将同时删除该服务商下的对话与视觉模型，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AgentStore.deleteProvider(p.id)
                        if (form?.providerId == p.id) form = null
                        pendingDeleteProvider = null
                        Toast.makeText(context, "已删除 ${p.name}", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProvider = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProviderListItem(
    name: String,
    subtitle: String,
    selected: Boolean,
    badge: String,
    onClick: () -> Unit,
    enabled: Boolean? = null,
    onToggleEnabled: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(name, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false)
                    )
                    if (badge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onToggleEnabled != null) {
                Switch(
                    checked = enabled ?: true,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = AxLang.t("删除服务商"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val color = remember(name) {
        val palette = listOf(
            Color(0xFF5B6CFF), Color(0xFF7B4DFF), Color(0xFF00897B), Color(0xFFEF6C00),
            Color(0xFFD81B60), Color(0xFF00838F), Color(0xFF6D4C41), Color(0xFF3949AB)
        )
        palette[Math.abs(name.hashCode()) % palette.size]
    }
    val initial = name.trim().take(1).uppercase()
    Box(
        Modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial.ifBlank { "A" }, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onCreated: (ProviderProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf("openai") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var presetId by rememberSaveable { mutableStateOf("") }
    var showPresetPicker by remember { mutableStateOf(false) }
    var presetQuery by remember { mutableStateOf("") }
    var showNameError by remember { mutableStateOf(false) }
    var showUrlError by remember { mutableStateOf(false) }

    fun submit() {
        showNameError = name.trim().isEmpty()
        showUrlError = !isValidApiHost(baseUrl)
        if (showNameError || showUrlError) return
        val provider = ProviderProfile(
            id = "pv-${System.currentTimeMillis()}-${(0..999).random()}",
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            protocol = protocol,
            presetId = presetId,
            custom = true
        )
        AgentStore.addProvider(provider)
        onCreated(provider)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加提供商") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "从预设创建（可选）",
                    style = MaterialTheme.typography.labelMedium
                )
                Box {
                    OutlinedTextField(
                        value = ProviderPresets.find(presetId)?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("从 Provider 预设创建…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { showPresetPicker = true })
                }

                Text("提供商名称", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32); showNameError = false },
                    placeholder = { Text("例如 OpenAI") },
                    singleLine = true,
                    isError = showNameError,
                    supportingText = { if (showNameError) Text("请输入提供商名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("提供商类型", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("openai" to "OpenAI", "responses" to "Responses").forEach { (value, label) ->
                            FilterChip(
                                selected = protocol == value,
                                onClick = { protocol = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("anthropic" to "Anthropic", "gemini" to "Gemini").forEach { (value, label) ->
                            FilterChip(
                                selected = protocol == value,
                                onClick = { protocol = value },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Text("API 地址", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; showUrlError = false },
                    placeholder = { Text("Base URL：https://example.com") },
                    singleLine = true,
                    isError = showUrlError,
                    supportingText = {
                        if (showUrlError) {
                            Text(if (baseUrl.isBlank()) "请输入 Base URL" else "请输入有效的 HTTP 或 HTTPS 地址")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("API 密钥", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("输入 API 密钥") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showPresetPicker) {
        AlertDialog(
            onDismissRequest = { showPresetPicker = false },
            title = { Text("从预设创建") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = presetQuery,
                        onValueChange = { presetQuery = it },
                        placeholder = { Text("搜索 Provider 预设") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item {
                            TextButton(
                                onClick = {
                                    presetId = ""
                                    showPresetPicker = false
                                }
                            ) { Text("不使用预设") }
                        }
                        items(ProviderPresets.search(presetQuery), key = { it.id }) { preset ->
                            ProviderListItem(
                                name = preset.name,
                                subtitle = preset.baseUrl.ifBlank { preset.description },
                                selected = presetId == preset.id,
                                badge = protocolLabel(preset.protocol),
                                onClick = {
                                    presetId = preset.id
                                    name = preset.name
                                    baseUrl = preset.baseUrl
                                    protocol = preset.protocol
                                    showPresetPicker = false
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetPicker = false }) { Text("取消") }
            }
        )
    }
}
