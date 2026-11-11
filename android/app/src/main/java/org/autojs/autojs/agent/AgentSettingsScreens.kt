package org.autojs.autojs.agent

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autoxjs.R
import org.json.JSONObject
import java.io.File

private data class SettingItem(
    val title: String,
    val subtitle: String,
    val value: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Int,
    val action: () -> Unit
)

private data class SettingGroup(val title: String, val items: List<SettingItem>)

private fun accentLabel(cfg: UiConfig): String = when {
    cfg.wallpaperAccent -> "跟随壁纸"
    cfg.accentId == "custom" -> "自定义"
    else -> AxAccent.preset(cfg.accentId).label
}

@Composable
fun SettingsScreen(
    onOpenModels: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSandbox: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenMenu: (() -> Unit)? = null
) {
    val cfg = AgentStore.config
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var livePromotion by remember { mutableStateOf(AgentTaskService.isPromotionAllowed(context)) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            livePromotion = AgentTaskService.isPromotionAllowed(context)
        }
    }

    val allSkills = remember { AgentSkillStore.list(cfg.disabledSkills) }
    val groups = listOf(
        SettingGroup(
            "AI",
            listOf(
                SettingItem(
                    "模型管理",
                    "对话与视觉模型、服务商连接",
                    "${cfg.chatModels.size} / ${cfg.visionModels.size}",
                    Icons.Filled.Settings, 0, onOpenModels
                ),
                SettingItem(
                    "联网搜索",
                    "搜索源与抓取方式",
                    "${cfg.webProvider} · ${cfg.webMaxResults} 条",
                    Icons.Filled.Search, 0, onOpenSearch
                )
            )
        ),
        SettingGroup(
            "能力",
            listOf(
                SettingItem(
                    "运行与安全",
                    "步数、审批、虚拟屏与续跑",
                    "${cfg.maxSteps} 步 · ${cfg.approvalTools.ifBlank { "全自动" }}",
                    Icons.Filled.Build, 4, onOpenSecurity
                ),
                SettingItem(
                    "技能",
                    "SKILL.md 技能库与启停",
                    "${allSkills.count { it.enabled }} / ${allSkills.size} 启用",
                    Icons.Filled.Star, 4, onOpenSkills
                ),
                SettingItem(
                    "沙箱目录",
                    "文件工具允许操作的根目录",
                    "${cfg.sandboxRoots.size} 个",
                    Icons.Filled.List, 4, onOpenSandbox
                )
            )
        ),
        SettingGroup(
            "外观",
            listOf(
                SettingItem(
                    "外观与主题",
                    "配色、深浅、文字大小、轮次折叠",
                    accentLabel(cfg),
                    Icons.Filled.Edit, 1, onOpenAppearance
                ),
                SettingItem(
                    "流体云实时活动",
                    "退到后台时在顶部胶囊显示当前动作",
                    when {
                        !AgentTaskService.supportsLiveUpdates() -> "系统不支持"
                        livePromotion -> "已开启"
                        else -> "未开启"
                    },
                    Icons.Filled.Notifications, 1,
                    { AgentTaskService.openPromotionSettings(context) }
                )
            )
        ),
        SettingGroup(
            "通用",
            listOf(
                SettingItem(
                    "诊断与日志",
                    "配置、运行目录与日志路径",
                    null,
                    Icons.Filled.Refresh, 5, onOpenDiagnostics
                ),
                SettingItem(
                    "关于 Axgent",
                    "版本、包名与构建信息",
                    null,
                    Icons.Filled.Info, 5, onOpenAbout
                )
            )
        )
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "设置",
            subtitle = "模型、能力与外观；修改后写入 ui-config.json，下次任务生效。",
            onMenu = onOpenMenu
        )
        Box(Modifier.padding(horizontal = AxSpace.gutter)) {
            AxSearchField(query, { query = it }, "搜索设置")
        }
        Spacer(Modifier.height(14.dp))
        val q = query.trim()
        val visibleGroups = if (q.isBlank()) groups else {
            val matches = groups.flatMap { g -> g.items.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) } }
            if (matches.isEmpty()) emptyList() else listOf(SettingGroup("搜索结果", matches))
        }
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (visibleGroups.isEmpty()) {
                Text(
                    "没有匹配的设置项",
                    style = MaterialTheme.typography.bodySmall,
                    color = AxTheme.colors.textMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            visibleGroups.forEach { group ->
                AxSection(group.title) {
                    group.items.forEachIndexed { index, item ->
                        AxSettingsTile(
                            title = item.title,
                            icon = item.icon,
                            onClick = item.action,
                            subtitle = item.subtitle,
                            value = item.value,
                            tintIndex = item.tint
                        )
                        if (index != group.items.lastIndex) AxDivider()
                    }
                }
            }
            Text(
                "API Key 只保存在本机 ui-config.json；不会上传到除所配置模型端点以外的地方。",
                style = MaterialTheme.typography.bodySmall,
                color = AxTheme.colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 外观：三档深浅、六套配色、自定义/壁纸取色、阅读字号与轮次折叠。 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val cfg = AgentStore.config
    val colors = AxTheme.colors
    val context = LocalContext.current
    var customOpen by remember { mutableStateOf(false) }
    var customHex by rememberSaveable { mutableStateOf(cfg.customAccent) }
    val systemDark = isSystemInDarkTheme()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "外观",
            subtitle = "配色只出现在主按钮、发送键、选中态与链接上，其余是中性纸面。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AxSection("深浅") {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemePreviewCard(
                        label = "浅色",
                        selected = cfg.theme == "light",
                        modifier = Modifier.weight(1f),
                        light = true,
                        onClick = { AgentStore.setTheme("light") }
                    )
                    ThemePreviewCard(
                        label = "深色",
                        selected = cfg.theme == "dark",
                        modifier = Modifier.weight(1f),
                        light = false,
                        onClick = { AgentStore.setTheme("dark") }
                    )
                    ThemePreviewCard(
                        label = "跟随系统",
                        selected = cfg.theme == "system",
                        modifier = Modifier.weight(1f),
                        light = !systemDark,
                        onClick = { AgentStore.setTheme("system") }
                    )
                }
            }

            AxSection("配色") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AxAccent.presets.forEach { accent ->
                        AccentSwatch(
                            label = accent.label,
                            color = accent.seed,
                            selected = cfg.accentId == accent.id
                        ) { AgentStore.setAccent(accent.id, cfg.customAccent, false) }
                    }
                    AccentSwatch(
                        label = "自定义",
                        color = parseHexColor(cfg.customAccent),
                        selected = cfg.accentId == "custom"
                    ) { customOpen = true }
                }
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "跟随壁纸取色",
                    subtitle = "从系统壁纸取样，自动推出一套同明度的配色",
                    checked = cfg.wallpaperAccent,
                    onCheckedChange = { on ->
                        if (on) AgentStore.setAccent("wallpaper", cfg.customAccent, true)
                        else AgentStore.setAccent("mist", cfg.customAccent, false)
                    }
                )
            }

            AxSection("阅读") {
                Text(
                    "聊天字号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier.padding(start = 14.dp, top = 11.dp)
                )
                AxSegmentRow(
                    options = listOf(
                        "0.85" to "小",
                        "1.0" to "标准",
                        "1.15" to "大",
                        "1.3" to "特大"
                    ),
                    selected = cfg.chatTextScale.toString(),
                    onSelect = { AgentStore.setChatTextScale(it.toFloat()) }
                )
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "折叠已完成轮次",
                    subtitle = "把上一轮的工具过程收成一行摘要，点开可看完整过程",
                    checked = cfg.foldCompletedTurns,
                    onCheckedChange = { AgentStore.setFoldCompletedTurns(it) }
                )
            }

            AxSection("当前生效") {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(colors.primary, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            accentLabel(cfg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                        Text(
                            when (cfg.theme) {
                                "light" -> "浅色"
                                "dark" -> "深色"
                                else -> "跟随系统"
                            } + " · 字号 ${cfg.chatTextScale}×",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "配色会立即应用并写入 ui-config.json。切换时会全局淡入淡出。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (customOpen) {
        AxDialog(
            onDismiss = { customOpen = false },
            title = "自定义配色",
            confirmText = "应用",
            onConfirm = {
                val hex = customHex.trim().let { if (it.startsWith("#")) it else "#$it" }
                AgentStore.setAccent("custom", hex, false)
                customOpen = false
            }
        ) {
            AxTextField(
                value = customHex,
                onValueChange = { customHex = it.take(9) },
                label = "颜色（#RRGGBB）",
                placeholder = "#4B67B4"
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(parseHexColor(customHex), RoundedCornerShape(AxRadius.sm))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "暗色、文字与玻璃会按同一明度自动推导。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    light: Boolean,
    onClick: () -> Unit
) {
    val colors = AxTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(
                    brush = if (light) Brush.verticalGradient(
                        listOf(Color(0xFFEBF1FE), Color(0xFFF6F7FC))
                    ) else Brush.verticalGradient(
                        listOf(Color(0xFF1D222C), Color(0xFF1B1C23))
                    ),
                    shape = RoundedCornerShape(AxRadius.md)
                )
                .border(
                    if (selected) 1.5.dp else 0.5.dp,
                    if (selected) colors.primary else colors.rule,
                    RoundedCornerShape(AxRadius.md)
                )
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(7.dp)
                        .background(
                            if (light) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f),
                            CircleShape
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(3.dp)
                        .background(
                            if (light) Color(0xFF1E232E).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .background(
                            if (light) Color(0xFF1E232E).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.12f),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.textMuted
        )
    }
}

@Composable
private fun AccentSwatch(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = AxTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(34.dp)
                .background(color, CircleShape)
                .border(
                    if (selected) 2.dp else 0.5.dp,
                    if (selected) colors.text else colors.rule,
                    CircleShape
                )
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.text else colors.textFaint
        )
    }
}

private fun cleanSandboxPath(path: String): String {
    val s = path.trim()
    if (s == "/") return s
    return s.trimEnd('/')
}

private fun riskySandboxPath(path: String): Boolean {
    val s = cleanSandboxPath(path).lowercase()
    return s == "/" || s == "/sdcard" || s == "/storage/emulated/0" ||
        s.contains("/dcim") || s.contains("/pictures") ||
        s.contains("/android/data") || s.contains("/android/obb") ||
        s == "/data" || s.startsWith("/data/") ||
        s == "/system" || s.startsWith("/system/") ||
        s == "/vendor" || s.startsWith("/vendor/")
}

@Composable
fun SandboxSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val initial = AgentStore.config.sandboxRoots
    val roots = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(initial) } }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = AxTheme.colors
    val risky = roots.map { cleanSandboxPath(it) }.filter { riskySandboxPath(it) }.distinct()
    val hasDefaultRoot = roots.map { cleanSandboxPath(it) }.contains("/sdcard/Download/phone-agent")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "沙箱目录",
            subtitle = "文件工具只允许操作这些根目录下的路径；沙箱外路径还需要打开「沙箱外文件操作」。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AxSection("根目录") {
                roots.forEachIndexed { index, path ->
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AxTextField(
                                value = path,
                                onValueChange = { roots[index] = it },
                                label = "沙箱根目录 ${index + 1}",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "删除",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (roots.size > 1) colors.danger else colors.textGhost,
                                modifier = Modifier
                                    .clickable(enabled = roots.size > 1) { roots.removeAt(index) }
                                    .padding(6.dp)
                            )
                        }
                    }
                    if (index != roots.lastIndex) AxDivider(indent = 14.dp)
                }
                Text(
                    "＋ 添加路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable { roots.add("/sdcard/") }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            if (risky.isNotEmpty()) {
                Text(
                    "检测到可能的关键路径：${risky.joinToString(" / ")}；仍可保存，请再次确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger
                )
            }
            if (!hasDefaultRoot) {
                Text(
                    "默认运行目录 /sdcard/Download/phone-agent 已被移除；Agent 的运行产物和文件回读可能需要沙箱外权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            AxPrimaryButton("保存", {
                val cleaned = roots.map { cleanSandboxPath(it) }.filter { it.isNotBlank() }.distinct()
                when {
                    cleaned.isEmpty() -> error = "至少保留一个沙箱根目录"
                    cleaned.any { !it.startsWith("/") } -> error = "沙箱目录必须是绝对路径，例如 /sdcard/Download/phone-agent"
                    cleaned.any { it.contains("..") || it.contains("\n") || it.contains("\r") } -> error = "沙箱目录不能包含 .. 或换行"
                    else -> {
                        AgentStore.updateSandboxRoots(cleaned)
                        error = null
                        Toast.makeText(context, "沙箱目录已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            Text(
                "保存到 ui-config.json 的 sandbox.roots；系统路径（runs、conversations、src）仍禁止写删。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val cfg = AgentStore.config
    val context = LocalContext.current
    var provider by rememberSaveable { mutableStateOf(cfg.webProvider) }
    var maxResults by rememberSaveable { mutableStateOf(cfg.webMaxResults.toString()) }
    var exclude by rememberSaveable { mutableStateOf(cfg.webExcludeDomains) }
    var fetchProvider by rememberSaveable { mutableStateOf(cfg.webFetchProvider) }
    var allowPrivate by rememberSaveable { mutableStateOf(cfg.allowPrivateUrls) }
    val providers = listOf("auto", "deepseek", "zhipu", "searxng", "bing")
    val colors = AxTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "联网搜索",
            subtitle = "auto：DeepSeek Search → Zhipu → SearXNG → Bing。Key 配置保留在 ui-config.json 的 web 段。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AxSection("搜索源") {
                AxSegmentRow(
                    providers.map { it to it },
                    provider,
                    { provider = it }
                )
            }
            AxSection("参数") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AxTextField(
                        value = maxResults,
                        onValueChange = { maxResults = it.filter { c -> c.isDigit() }.take(2) },
                        label = "maxResults（1–10）",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                    AxTextField(
                        value = exclude,
                        onValueChange = { exclude = it },
                        label = "excludeDomains（逗号分隔）",
                        placeholder = "*.example.com,/spam/",
                        minLines = 2,
                        maxLines = 4,
                        singleLine = false
                    )
                }
                AxDivider(indent = 14.dp)
                Text(
                    "fetchProvider",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("auto", "deepseek", "raw").forEach { p ->
                        AxPill(p, { fetchProvider = p }, selected = fetchProvider == p)
                    }
                }
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "允许私网 URL",
                    subtitle = "默认关闭；关闭时 web_fetch 不抓取 localhost/10.x/192.168.x 等地址",
                    checked = allowPrivate,
                    onCheckedChange = { allowPrivate = it }
                )
            }
            AxPrimaryButton("保存", {
                AgentStore.updateWebSettings(
                    provider = provider,
                    maxResults = (maxResults.toIntOrNull() ?: 6).coerceIn(1, 10),
                    excludeDomains = exclude,
                    fetchProvider = fetchProvider,
                    allowPrivateUrls = allowPrivate
                )
                Toast.makeText(context, "联网搜索设置已保存", Toast.LENGTH_SHORT).show()
            })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val cfg = AgentStore.config
    val context = LocalContext.current
    var maxSteps by rememberSaveable { mutableStateOf(cfg.maxSteps.toString()) }
    var maxMinutes by rememberSaveable { mutableStateOf(cfg.maxMinutes.toString()) }
    var approvals by rememberSaveable { mutableStateOf(cfg.approvalTools) }
    var virtualDisplay by rememberSaveable { mutableStateOf(cfg.virtualDisplay) }
    var autoResume by rememberSaveable { mutableStateOf(cfg.autoResume) }
    var maxParallel by rememberSaveable { mutableStateOf(cfg.maxParallelDisplays.toString()) }
    val colors = AxTheme.colors
    val scope = rememberCoroutineScope()
    var privLine by remember { mutableStateOf("无障碍 — · Shizuku — · Root —") }
    var whitelistLine by remember { mutableStateOf("ColorOS 白名单：检测中…") }
    var privBusy by remember { mutableStateOf(false) }

    fun refreshPrivilege() {
        scope.launch {
            val pair = withContext(Dispatchers.IO) {
                val st = runCatching { JSONObject(PrivilegeManager.status()) }.getOrNull()
                val wl = runCatching { JSONObject(PrivilegeManager.whitelistStatus()) }.getOrNull()
                Pair(st, wl)
            }
            val st = pair.first
            val wl = pair.second
            privLine = "无障碍 " + (if (st?.optBoolean("a11y") == true) "已开启" else "未开启") +
                " · Shizuku " + (if (st?.optBoolean("shizuku") == true) "已连接" else "不可用") +
                " · Root " + (if (st?.optBoolean("root") == true) "可用" else "不可用")
            whitelistLine = when {
                wl == null -> "ColorOS 白名单：读取失败"
                wl.optBoolean("inList") -> "ColorOS 白名单：已加入"
                wl.optBoolean("ok") -> "ColorOS 白名单：未加入"
                else -> "ColorOS 白名单：无法读取（" + wl.optString("err") + "）"
            }
        }
    }
    LaunchedEffect(Unit) { refreshPrivilege() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "运行与安全",
            subtitle = "单任务的步数与时长、需要确认的工具、后台虚拟屏与续跑。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AxSection("预算") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AxTextField(
                        value = maxSteps,
                        onValueChange = { maxSteps = it.filter { c -> c.isDigit() }.take(2) },
                        label = "最大步数（1–100）",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                    AxTextField(
                        value = maxMinutes,
                        onValueChange = { maxMinutes = it.filter { c -> c.isDigit() }.take(2) },
                        label = "最长运行分钟（1–60）",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                    AxTextField(
                        value = approvals,
                        onValueChange = { approvals = it },
                        label = "需要确认的工具（逗号分隔）",
                        placeholder = "file_remove,file_batch",
                        minLines = 2,
                        maxLines = 4,
                        singleLine = false
                    )
                    Text(
                        "默认空 = 全自动。确认卡会暂停 Agent，在输入框上方显示允许/拒绝。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
            AxSection("后台虚拟屏") {
                AxSwitchTile(
                    title = "后台虚拟屏（需要 root）",
                    subtitle = if (cfg.privilege.root && cfg.capabilities.root) {
                        "使用 App 内置运行时在 Display > 0 后台跑 App；重装后需重新授权 root。"
                    } else {
                        "全局 Root 或 Agent Root 未开启：任务会按物理屏运行，虚拟屏不会启动。"
                    },
                    checked = virtualDisplay,
                    onCheckedChange = {
                        virtualDisplay = it
                        if (it && !(cfg.privilege.root && cfg.capabilities.root)) {
                            Toast.makeText(context, "Root 权限未开启，虚拟屏将在任务中跳过", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                AxDivider(indent = 14.dp)
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AxTextField(
                        value = maxParallel,
                        onValueChange = { v ->
                            val digits = v.filter { c -> c.isDigit() }.take(1)
                            if (digits.isEmpty() || (digits.toIntOrNull() ?: 0) in 1..5) maxParallel = digits
                        },
                        label = "后台最大虚拟屏数量（1–5）",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        enabled = virtualDisplay
                    )
                    Text(
                        if (virtualDisplay) "Agent 可自主创建最多 $maxParallel 个并行子任务；系统硬上限 6，用户可设不超过 5。"
                        else "需先开启「后台虚拟屏」才能设置；关闭时此项不生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (virtualDisplay) colors.textMuted else colors.textGhost
                    )
                }
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "崩溃/被杀后自动续跑",
                    subtitle = "下次打开 App 时从 checkpoint 续跑一次；用户主动停止不会续跑。",
                    checked = autoResume,
                    onCheckedChange = { autoResume = it }
                )
            }
            AxSection("沙箱与回收站") {
                Text(
                    "文件工具限制在沙箱 roots；删除先进入 runs/.trash。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(14.dp)
                )
            }
            AxSection("权限分级") {
                AxSwitchTile(
                    title = "全局无障碍权限",
                    subtitle = "关闭后 App 与 Agent 都不使用无障碍观察/操作界面",
                    checked = cfg.privilege.accessibility,
                    onCheckedChange = { AgentStore.setPrivilege("accessibility", it) }
                )
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "全局 Shizuku 权限",
                    subtitle = "关闭后任何模块都不得通过 Shizuku 执行 shell",
                    checked = cfg.privilege.shizuku,
                    onCheckedChange = { AgentStore.setPrivilege("shizuku", it) }
                )
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "全局 Root 权限",
                    subtitle = "关闭后任何 su/root 调用都会被硬拦截；开启后 Agent Root 仍需单独开启",
                    checked = cfg.privilege.root,
                    onCheckedChange = { AgentStore.setPrivilege("root", it) }
                )
                AxDivider(indent = 14.dp)
                AxSwitchTile(
                    title = "自动处理弹窗",
                    subtitle = "自动处理 ColorOS 无障碍提示、ANR 等白名单系统弹窗；不会自动授予 Shizuku/Root/无障碍权限",
                    checked = cfg.autoHandlePopup,
                    onCheckedChange = { AgentStore.setAutoHandlePopup(it) }
                )
                AxDivider(indent = 14.dp)
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(privLine, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(whitelistLine, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Text(
                        "操作优先无障碍；需要 shell 时先走 Shizuku，最后才用 root。白名单用于跳过 ColorOS 的「关闭无障碍」提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AxSecondaryButton("刷新", { refreshPrivilege() }, modifier = Modifier.weight(1f))
                        AxSecondaryButton(if (privBusy) "写入中…" else "加入白名单", {
                            if (!privBusy) {
                                privBusy = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        runCatching { JSONObject(PrivilegeManager.ensureWhitelist()) }.getOrNull()
                                    }
                                    privBusy = false
                                    refreshPrivilege()
                                    Toast.makeText(
                                        context,
                                        when {
                                            r == null -> "写入失败"
                                            r.optBoolean("ok") -> "已加入 ColorOS 白名单"
                                            else -> "写入失败：" + r.optString("err")
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                    AxSecondaryButton("请求 Shizuku 授权", {
                        val ok = PrivilegeManager.requestShizukuPermission()
                        Toast.makeText(
                            context,
                            if (ok) "已请求；请在 Shizuku 弹窗中允许" else "Shizuku 未安装或服务未运行",
                            Toast.LENGTH_SHORT
                        ).show()
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            AxPrimaryButton("保存", {
                AgentStore.updateRunSettings(
                    maxSteps = (maxSteps.toIntOrNull() ?: 25).coerceIn(1, 100),
                    maxMinutes = (maxMinutes.toIntOrNull() ?: 10).coerceIn(1, 60),
                    approvalTools = approvals,
                    virtualDisplay = virtualDisplay,
                    autoResume = autoResume,
                    maxParallelDisplays = (maxParallel.toIntOrNull() ?: 2).coerceIn(1, 5)
                )
                Toast.makeText(context, "运行设置已保存", Toast.LENGTH_SHORT).show()
            })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cfg = AgentStore.config
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "诊断与日志",
            subtitle = "排障时优先看 main.log；每次运行都有 steps / stream / checkpoint。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AxSection("当前状态") {
                DiagnosticLine("配置", AgentPaths.configFile.absolutePath)
                AxDivider(indent = 14.dp)
                DiagnosticLine("运行目录", AgentPaths.runsDir.absolutePath)
                AxDivider(indent = 14.dp)
                DiagnosticLine("当前模型", cfg.model.ifBlank { "未配置" })
                AxDivider(indent = 14.dp)
                DiagnosticLine("搜索源", "${cfg.webProvider} · fetch ${cfg.webFetchProvider}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxSecondaryButton("复制配置 JSON", {
                    val text = try {
                        AgentPaths.configFile.readText()
                    } catch (e: Exception) {
                        "读取失败：${e.message}"
                    }
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "配置 JSON 已复制", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
                AxSecondaryButton("复制运行目录", {
                    clipboard.setText(AnnotatedString(AgentPaths.runsDir.absolutePath))
                    Toast.makeText(context, "运行目录已复制", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
            }
            Text(
                "提示：每次运行目录下都有 main.log、steps.jsonl、stream.jsonl、checkpoint.json。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AxTheme.colors.textFaint)
        AxMonoText(value, modifier = Modifier.padding(top = 2.dp), maxLines = 2)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cfg = AgentStore.config
    val colors = AxTheme.colors
    val version = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(title = "关于 Axgent", subtitle = "版本、构建与运行环境", onBack = onBack)
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AxSection {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(AxRadius.md))
                            .background(parseHexColor("#F5F5F8")),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painterResource(R.drawable.ic_axgent_foreground),
                            contentDescription = "Axgent 图标",
                            contentScale = ContentScale.Fit,
                            // 前景按自适应图标的 60% 安全区设计；应用内小尺寸下放大一点，
                            // 避免在 48dp 圆角块里显得过空。
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.28f
                                    scaleY = 1.28f
                                }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Axgent", style = MaterialTheme.typography.titleLarge, color = colors.text)
                        Text(
                            "AutoX.js v7 · Compose · 内核 main.js",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
            AxSection("构建") {
                DiagnosticLine("版本", version)
                AxDivider(indent = 14.dp)
                DiagnosticLine("包名", context.packageName)
                AxDivider(indent = 14.dp)
                DiagnosticLine("当前主题", "${AxAccent.preset(cfg.accentId).label} · ${cfg.theme}")
            }
            AxSection("数据位置") {
                DiagnosticLine("运行数据", AgentPaths.root.absolutePath)
                AxDivider(indent = 14.dp)
                DiagnosticLine("脚本", AgentPaths.scriptFile.absolutePath)
                AxDivider(indent = 14.dp)
                DiagnosticLine("技能库", AgentPaths.skillsLibraryDir.absolutePath)
            }
            Text(
                "APK 内置 agent-src：首次安装或升级时自动部署到脚本目录，设备上的 config/local.js 不会被覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 仅用于未来扩展：把文件路径变成可读的相对名称。 */
internal fun displayFileName(path: String): String = File(path).name.ifBlank { path }
