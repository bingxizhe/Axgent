package org.autojs.autojs.agent

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun AgentApp(openPreview: Boolean = false) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(readPermissionState(context)) }
    var resumed by remember { mutableStateOf(false) }
    // 页面栈：对话页恒为根。顶部导航（侧栏/抽屉）把栈重置成 [chat, 目标]，
    // 页内下钻（设置 → 子页、模型管理 → 新建连接）入栈；返回统一出栈。
    var stack by rememberSaveable {
        mutableStateOf(listOf(if (AgentStore.config.onboarded) "chat" else "onboarding"))
    }
    val screen = stack.last()
    fun navigate(target: String) {
        if (target != stack.last()) stack = stack + target
    }
    fun openTop(target: String) {
        stack = if (target == "chat") listOf("chat") else listOf("chat", target)
    }
    fun goBack() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    var modelConfigVision by rememberSaveable { mutableStateOf(false) }
    var showCapabilities by rememberSaveable { mutableStateOf(false) }
    var composerMenu by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<AgentStore.ConversationSummary>>(emptyList()) }
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(screen) {
        if (screen != "chat") composerMenu = null
    }

    LaunchedEffect(Unit) {
        if (openPreview && AgentStore.config.onboarded) {
            AgentPreviewOverlay.show(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    permissions = readPermissionState(context)
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        var lastPermissionCheck = 0L
        while (true) {
            withContext(Dispatchers.IO) { AgentStore.refresh() }
            val now = System.currentTimeMillis()
            if (!permissions.accessibility && now - lastPermissionCheck > 2000) {
                lastPermissionCheck = now
                permissions = withContext(Dispatchers.IO) { readPermissionState(context) }
            }
            val running = AgentStore.status == AgentStatus.RUNNING ||
                AgentStore.status == AgentStatus.PAUSED ||
                AgentStore.status == AgentStatus.WAITING
            delay(if (running) 200 else 1000)
        }
    }

    // 会话列表：侧栏 / 抽屉使用；宽屏与历史页每 3 秒刷新一次。
    LaunchedEffect(resumed, screen) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            sessions = withContext(Dispatchers.IO) {
                runCatching { AgentStore.conversationSummaries() }.getOrDefault(emptyList())
            }
            delay(3000)
        }
    }

    // 排队消息：本轮结束后自动作为新任务发送。
    val runningNow = AgentStore.status == AgentStatus.RUNNING ||
        AgentStore.status == AgentStatus.PAUSED ||
        AgentStore.status == AgentStatus.WAITING
    LaunchedEffect(AgentStore.status, AgentStore.queuedMessage, AgentStore.pendingTask) {
        val q = AgentStore.queuedMessage
        if (q != null && !runningNow && AgentStore.pendingTask == null && screen == "chat") {
            AgentStore.clearQueuedMessage()
            AgentStore.startTask(q)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    // 非根页面统一出栈；对话页（根）不拦截，保留系统默认的退出行为。
    BackHandler(enabled = !drawerState.isOpen && stack.size > 1) {
        goBack()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 横屏手机（约 800dp）也要进入侧栏常驻布局；只有真正的窄竖屏走抽屉。
        val wide = maxWidth >= 720.dp
        val short = maxHeight < 600.dp
        val sideWidth = if (maxWidth >= 1000.dp) 288.dp else 248.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (sidebarVisible) {
                    AxSidebar(
                        width = sideWidth,
                        compactHeight = short,
                        sessions = sessions,
                        currentConversation = AgentStore.conversationId,
                        screen = screen,
                        onNewChat = {
                            AgentStore.newConversation()
                            openTop("chat")
                        },
                        onOpenSession = { id ->
                            AgentStore.openConversation(id)
                            openTop("chat")
                        },
                        onSelect = { openTop(it) },
                        onOpenSettings = { openTop("settings") }
                    )
                    Box(
                        Modifier
                            .width(0.5.dp)
                            .fillMaxHeight()
                            .background(AxTheme.colors.rule.copy(alpha = 0.5f))
                    )
                }
                AxContent(
                    modifier = Modifier.weight(1f),
                    screen = screen,
                    sessions = sessions,
                    permissions = permissions,
                    showMenu = true,
                    onMenu = { sidebarVisible = !sidebarVisible },
                    onScreen = { navigate(it) },
                    onBack = { goBack() },
                    onHome = { openTop("chat") },
                    onPermissions = { permissions = it },
                    onNewChat = {
                        AgentStore.newConversation()
                        openTop("chat")
                    },
                    onCapabilities = { showCapabilities = true },
                    composerMenu = composerMenu,
                    onComposerMenu = { composerMenu = it },
                    modelConfigVision = modelConfigVision,
                    onModelConfigVision = { modelConfigVision = it }
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = AxTheme.colors.scrim,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = AxTheme.colors.sheet.fill,
                        drawerShape = RoundedCornerShape(topEnd = AxRadius.xl, bottomEnd = AxRadius.xl),
                        modifier = Modifier.width(304.dp)
                    ) {
                        AxNavigationPanel(
                            sessions = sessions,
                            currentConversation = AgentStore.conversationId,
                            screen = screen,
                            onNewChat = {
                                AgentStore.newConversation()
                                openTop("chat")
                                scope.launch { drawerState.close() }
                            },
                            onOpenSession = { id ->
                                AgentStore.openConversation(id)
                                openTop("chat")
                                scope.launch { drawerState.close() }
                            },
                            onSelect = { target ->
                                openTop(target)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            ) {
                AxContent(
                    modifier = Modifier.fillMaxSize(),
                    screen = screen,
                    sessions = sessions,
                    permissions = permissions,
                    showMenu = true,
                    onMenu = { scope.launch { drawerState.open() } },
                    onScreen = { navigate(it) },
                    onBack = { goBack() },
                    onHome = { openTop("chat") },
                    onPermissions = { permissions = it },
                    onNewChat = {
                        AgentStore.newConversation()
                        openTop("chat")
                    },
                    onCapabilities = { showCapabilities = true },
                    composerMenu = composerMenu,
                    onComposerMenu = { composerMenu = it },
                    modelConfigVision = modelConfigVision,
                    onModelConfigVision = { modelConfigVision = it }
                )
            }
        }
    }

    if (showCapabilities) {
        CapabilitiesDialog(onDismiss = { showCapabilities = false })
    }
}

@Composable
private fun AxContent(
    modifier: Modifier,
    screen: String,
    sessions: List<AgentStore.ConversationSummary>,
    permissions: PermissionState,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onScreen: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPermissions: (PermissionState) -> Unit,
    onNewChat: () -> Unit,
    onCapabilities: () -> Unit,
    composerMenu: String?,
    onComposerMenu: (String?) -> Unit,
    modelConfigVision: Boolean,
    onModelConfigVision: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (screen == "chat") {
            AxChatTopBar(
                showMenu = showMenu,
                onMenu = onMenu,
                conversation = sessions.firstOrNull { it.id == AgentStore.conversationId },
                onNewChat = onNewChat,
                onCapabilities = onCapabilities,
                onModelMenu = { onComposerMenu(if (composerMenu == "model") null else "model") }
            )
        }
        if (screen == "chat" && permissions.missing.isNotEmpty()) {
            PermissionBanner(permissions.missing) {
                if ("无障碍" in permissions.missing) {
                    Thread {
                        val ok = AgentStore.ensureAccessibilityByRoot()
                        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                            onPermissions(readPermissionState(context))
                        }
                        if (!ok) onScreen("onboarding")
                    }.start()
                } else {
                    onScreen("onboarding")
                }
            }
        }
        AgentStore.vdError?.let { msg ->
            VdBanner(msg) {
                if (msg.contains("root")) openKernelSu(context) else retryVdRuntime(context)
            }
        }
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(AxMotion.standard)) + slideInVertically(tween(AxMotion.standard)) { it / 28 })
                    .togetherWith(fadeOut(tween(AxMotion.quick)))
            },
            label = "screen"
        ) { target ->
            Box(Modifier.fillMaxSize()) {
            when (target) {
                "onboarding" -> OnboardingScreen {
                    AgentStore.setOnboarded(true)
                    onPermissions(readPermissionState(context))
                    onHome()
                }
                "history" -> HistoryScreen(onOpen = onHome, onMenu = if (showMenu) onMenu else null)
                "schedule" -> ScheduleScreen(onMenu = if (showMenu) onMenu else null)
                "models" -> ModelManagerScreen(
                    onBack = onBack,
                    onAddModel = { vision ->
                        onModelConfigVision(vision)
                        onScreen("model_config")
                    }
                )
                "model_config" -> ModelConfigScreen(
                    initialVision = modelConfigVision,
                    onBack = onBack,
                    onDone = onBack
                )
                "settings" -> SettingsScreen(
                    onOpenModels = { onScreen("models") },
                    onOpenSearch = { onScreen("settings_search") },
                    onOpenSecurity = { onScreen("settings_security") },
                    onOpenSandbox = { onScreen("settings_sandbox") },
                    onOpenSkills = { onScreen("skills") },
                    onOpenDiagnostics = { onScreen("settings_diagnostics") },
                    onOpenAbout = { onScreen("settings_about") },
                    onOpenAppearance = { onScreen("settings_appearance") },
                    onOpenMenu = if (showMenu) onMenu else null
                )
                "settings_appearance" -> AppearanceScreen(onBack = onBack)
                "settings_search" -> SearchSettingsScreen(onBack = onBack)
                "settings_security" -> SecuritySettingsScreen(onBack = onBack)
                "settings_sandbox" -> SandboxSettingsScreen(onBack = onBack)
                "settings_diagnostics" -> DiagnosticsScreen(onBack = onBack)
                "settings_about" -> AboutScreen(onBack = onBack)
                "skills" -> SkillManagerScreen(onBack = onBack)
                else -> ChatScreen()
            }
            if (screen == "chat" && composerMenu != null) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { onComposerMenu(null) }
                            }
                    )
                    // 模型菜单从顶部标题栏的模型按钮展开（右边缘与按钮对齐）；
                    // 思考强度仍贴输入框，避免菜单离触发点太远。
                    val menuModifier = if (composerMenu == "model") {
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 84.dp, top = 4.dp)
                            .widthIn(max = 250.dp)
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = AxSpace.floatInset, vertical = 6.dp)
                    }
                    ComposerMenu(
                        menu = composerMenu,
                        onMenuChange = onComposerMenu,
                        onOpenModels = {
                            onComposerMenu(null)
                            onScreen("models")
                        },
                        modifier = menuModifier
                    )
                }
            }
            }
        }
        if (screen == "chat") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = AxSpace.readingWidth).fillMaxWidth()) {
                    ComposerBar(menu = composerMenu, onMenuChange = onComposerMenu)
                }
            }
        }
    }
}

@Composable
private fun AxChatTopBar(
    showMenu: Boolean,
    onMenu: () -> Unit,
    conversation: AgentStore.ConversationSummary?,
    onNewChat: () -> Unit,
    onCapabilities: () -> Unit,
    onModelMenu: () -> Unit
) {
    val colors = AxTheme.colors
    val firstUser = AgentStore.messages.firstOrNull { it.role == ChatMessage.Role.USER }?.text
    val rawTitle = conversation?.title?.takeUnless { it == "(无任务文本)" }
    val title = rawTitle ?: AgentStore.pendingTask ?: firstUser ?: "新对话"
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .widthIn(max = AxSpace.readingWidth)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showMenu) {
                AxCircleButton(Icons.Filled.Menu, "菜单", onMenu, size = 34.dp)
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(colors.pill.fill)
                    .border(0.5.dp, colors.pill.hairline, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            AxPill(
                AgentStore.config.model.ifBlank { "选择模型" },
                onModelMenu,
                modifier = Modifier.widthIn(max = 92.dp)
            )
            AxCircleButton(Icons.Filled.Add, "新对话", onNewChat, size = 32.dp)
            AxCircleButton(Icons.Filled.MoreVert, "能力", onCapabilities, size = 32.dp)
        }
    }
}

@Composable
private fun AxNavigationPanel(
    sessions: List<AgentStore.ConversationSummary>,
    currentConversation: String,
    screen: String,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Axgent", style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.weight(1f))
            StatusDot(AgentStore.status)
        }
        Text(
            AgentStore.config.model.ifBlank { "未配置模型" },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 12.dp)
        )
        AxPill("＋ 新对话", onNewChat, primary = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (sessions.isNotEmpty()) {
            Text(
                "最近会话",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textFaint,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )
            sessions.take(6).forEach { s ->
                AxSessionRow(s, s.id == currentConversation, onClick = { onOpenSession(s.id) })
            }
            Spacer(Modifier.height(6.dp))
        }
        AxNavRow("全部会话", Icons.Filled.List, screen == "history") { onSelect("history") }
        AxNavRow("技能", Icons.Filled.Star, screen == "skills") { onSelect("skills") }
        AxNavRow("定时任务", Icons.Filled.Refresh, screen == "schedule") { onSelect("schedule") }
        AxNavRow("设置", Icons.Filled.Settings, screen.startsWith("settings") || screen == "models" || screen == "model_config") {
            onSelect("settings")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "v1 · Compose · 内核 main.js",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textGhost,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun AxSidebar(
    width: Dp,
    compactHeight: Boolean,
    sessions: List<AgentStore.ConversationSummary>,
    currentConversation: String,
    screen: String,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Axgent", style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.weight(1f))
            StatusDot(AgentStore.status)
        }
        Text(
            AgentStore.config.model.ifBlank { "未配置模型" },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AxPill("＋ 新对话", onNewChat, primary = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (sessions.isNotEmpty()) {
            var filter by rememberSaveable { mutableStateOf("all") }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AxPill("全部", { filter = "all" }, selected = filter == "all")
                AxPill("运行中", { filter = "running" }, selected = filter == "running")
                AxPill("已完成", { filter = "done" }, selected = filter == "done")
            }
            Spacer(Modifier.height(8.dp))
            val filtered = sessions.filter {
                when (filter) {
                    "running" -> it.running
                    "done" -> it.done
                    else -> true
                }
            }
            var lastGroup = ""
            LazyColumn(Modifier.weight(1f)) {
                filtered.forEach { s ->
                    val group = dateGroup(s.lastModified)
                    if (group != lastGroup) {
                        lastGroup = group
                        item(key = "g-${group}-${s.id}") {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textGhost,
                                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = s.id) {
                        AxSessionRow(s, s.id == currentConversation, onClick = { onOpenSession(s.id) })
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.rule.copy(alpha = 0.5f)))
        Spacer(Modifier.height(8.dp))
        if (compactHeight) {
            // 横屏手机高度只有 ~360dp：底部导航收成一行图标，把空间留给会话列表。
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AxNavIcon(Icons.Filled.List, "全部会话", screen == "history") { onSelect("history") }
                AxNavIcon(Icons.Filled.Star, "技能", screen == "skills") { onSelect("skills") }
                AxNavIcon(Icons.Filled.Refresh, "定时任务", screen == "schedule") { onSelect("schedule") }
                AxNavIcon(
                    Icons.Filled.Settings, "设置",
                    screen.startsWith("settings") || screen == "models" || screen == "model_config"
                ) { onOpenSettings() }
            }
        } else {
            AxNavRow("全部会话", Icons.Filled.List, screen == "history") { onSelect("history") }
            AxNavRow("技能", Icons.Filled.Star, screen == "skills") { onSelect("skills") }
            AxNavRow("定时任务", Icons.Filled.Refresh, screen == "schedule") { onSelect("schedule") }
            AxNavRow(
                "设置", Icons.Filled.Settings,
                screen.startsWith("settings") || screen == "models" || screen == "model_config"
            ) {
                onOpenSettings()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "v1 · Compose · 内核 main.js",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textGhost,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun AxNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AxTheme.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) colors.primary else colors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AxNavRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.textMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.primary else colors.text
        )
    }
}

@Composable
private fun AxSessionRow(session: AgentStore.ConversationSummary, active: Boolean, onClick: () -> Unit) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(if (active) colors.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(
            session.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) colors.primary else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
            if (session.running) {
                AxStatusDot(colors.primary, 5.dp)
                Spacer(Modifier.width(4.dp))
                Text("运行中", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            } else {
                Text(
                    "${session.runCount} 轮 · ${relativeTime(session.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textGhost,
                    maxLines = 1
                )
            }
        }
    }
}

private fun dateGroup(ts: Long): String {
    if (ts <= 0L) return "更早"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val dayNow = now.get(Calendar.YEAR) * 366 + now.get(Calendar.DAY_OF_YEAR)
    val dayThen = then.get(Calendar.YEAR) * 366 + then.get(Calendar.DAY_OF_YEAR)
    return when {
        dayThen >= dayNow -> "今天"
        dayThen >= dayNow - 1 -> "昨天"
        dayThen >= dayNow - 7 -> "本周"
        else -> "更早"
    }
}

