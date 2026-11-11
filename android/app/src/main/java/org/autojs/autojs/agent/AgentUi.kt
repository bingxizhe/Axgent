package org.autojs.autojs.agent

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val ThinkingOptions = listOf(
    "default" to "默认",
    "none" to "关闭",
    "auto" to "自动",
    "minimal" to "轻度",
    "low" to "低",
    "medium" to "中",
    "high" to "高",
    "xhigh" to "极高",
    "max" to "最大"
)

@Composable
fun StatusDot(status: AgentStatus) {
    val colors = AxTheme.colors
    val color = when (status) {
        AgentStatus.RUNNING -> colors.primary
        AgentStatus.PAUSED, AgentStatus.WAITING -> colors.warning
        AgentStatus.FAILED -> colors.danger
        AgentStatus.DONE -> colors.success
        else -> colors.textGhost
    }
    AxStatusDot(color)
}

@Composable
fun ChatScreen() {
    val messages = AgentStore.messages
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    val busy = AgentStore.status == AgentStatus.RUNNING ||
        AgentStore.status == AgentStatus.PAUSED ||
        AgentStore.status == AgentStatus.WAITING
    if (messages.isEmpty() && !busy && AgentStore.pendingTask == null && !AgentStore.liveActive) {
        EmptyState()
        return
    }

    val turns = remember(messages.size, messages.lastOrNull()?.id) { buildTurns(messages) }
    val foldTurns = AgentStore.config.foldCompletedTurns

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atBottom = last != null &&
                last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 24
            atBottom && !listState.isScrollInProgress
        }.collect { atBottom -> if (atBottom) follow = true }
    }

    LaunchedEffect(turns.size, AgentStore.pendingTask) {
        if (!follow) return@LaunchedEffect
        delay(60)
        listState.scrollToBottomIfNeeded()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = AxSpace.readingWidth)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            follow = false
                        }
                    },
                contentPadding = PaddingValues(
                    start = AxSpace.gutter,
                    end = AxSpace.gutter,
                    top = 10.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(AxSpace.inTurn)
            ) {
                val completed = if (foldTurns && turns.size > 1) turns.dropLast(1) else emptyList()
                val visibleTurns = if (completed.isEmpty()) turns else listOf(turns.last())
                items(completed, key = { "fold-${it.id}" }) { turn ->
                    TurnSummaryRow(turn)
                }
                items(visibleTurns, key = { it.id }) { turn ->
                    TurnBlock(turn, showUser = true)
                }
                AgentStore.pendingTask?.let { t ->
                    item(key = "pending") { UserBubble(t, pending = true) }
                }
                if (!AgentStore.liveActive && AgentStore.status == AgentStatus.RUNNING) {
                    item(key = "typing") { TypingIndicator() }
                }
            }
            if (AgentStore.liveActive &&
                (AgentStore.liveText.isNotEmpty() || AgentStore.liveReasoning.isNotEmpty() || AgentStore.liveRetry.isNotEmpty())
            ) {
                LiveCard(
                    AgentStore.liveText,
                    AgentStore.liveReasoning,
                    AgentStore.liveRetry,
                    modifier = Modifier.padding(horizontal = AxSpace.gutter, vertical = 6.dp)
                )
            }
        }
    }
}

private suspend fun LazyListState.scrollToBottomIfNeeded() {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    val atBottom = last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset + 24
    if (!atBottom && info.totalItemsCount > 0) {
        scrollToItem(info.totalItemsCount - 1, 100_000)
    }
}

private data class Turn(val id: String, val msgs: List<ChatMessage>) {
    val firstUser: String get() = msgs.firstOrNull { it.role == ChatMessage.Role.USER }?.text ?: ""
    val stepCount: Int get() = msgs.count { it.role == ChatMessage.Role.STEP }
    val failedCount: Int get() = msgs.count { it.role == ChatMessage.Role.STEP && it.step?.ok == false }
    val finished: Boolean get() = msgs.any { it.role == ChatMessage.Role.FINAL }
}

private fun buildTurns(messages: List<ChatMessage>): List<Turn> {
    val turns = mutableListOf<MutableList<ChatMessage>>()
    var current: MutableList<ChatMessage>? = null
    messages.forEach { m ->
        if (m.role == ChatMessage.Role.USER || current == null) {
            current = mutableListOf<ChatMessage>().also { turns.add(it) }
        }
        current!!.add(m)
    }
    return turns.mapIndexed { i, list -> Turn("turn-$i-${list.firstOrNull()?.id ?: i}", list.toList()) }
}

@Composable
private fun TurnBlock(turn: Turn, showUser: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AxSpace.inTurn)) {
        val steps = mutableListOf<ChatMessage>()
        @Composable
        fun flushSteps() {
            if (steps.isEmpty()) return
            val copy = steps.toList()
            steps.clear()
            if (copy.size == 1) StepCard(copy[0].id, copy[0].step)
            else ProcessGroup("grp-${copy.first().id}", copy, running = AgentStore.status == AgentStatus.RUNNING)
        }
        turn.msgs.forEach { m ->
            when (m.role) {
                ChatMessage.Role.USER -> {
                    flushSteps()
                    if (!showUser) return@forEach
                    UserBubble(m.text)
                }
                ChatMessage.Role.STEP -> steps.add(m)
                ChatMessage.Role.SAY -> {
                    flushSteps()
                    SayText(m.text)
                }
                ChatMessage.Role.FINAL -> {
                    flushSteps()
                    FinalCard(
                        id = m.id,
                        text = m.text,
                        sources = m.sources,
                        promptTokens = m.promptTokens,
                        contextWindow = m.contextWindow,
                        stepCount = turn.stepCount
                    )
                }
                ChatMessage.Role.NOTICE -> {
                    flushSteps()
                    NoticeCard(m.text)
                }
                ChatMessage.Role.COMPACTION -> {
                    flushSteps()
                    CompactionCard(m.text)
                }
                ChatMessage.Role.RETRY -> {
                    flushSteps()
                    RetryCard(m.text)
                }
            }
        }
        flushSteps()
        if (turn.finished && turn.stepCount > 0) {
            DeliverablesCard(turn)
        }
    }
}

/** 本轮写出的文件：DSH 的「交付了 N 个文件」卡。 */
@Composable
private fun DeliverablesCard(turn: Turn) {
    val colors = AxTheme.colors
    val files = remember(turn.msgs) {
        val out = LinkedHashSet<String>()
        turn.msgs.filter { it.role == ChatMessage.Role.STEP }.forEach { m ->
            val step = m.step ?: return@forEach
            if (step.tool !in setOf("file_write", "file_append", "file_edit", "file_replace", "file_copy", "file_move")) return@forEach
            val path = try {
                if (step.args.isBlank()) "" else org.json.JSONObject(step.args).optString("path")
            } catch (_: Exception) {
                ""
            }
            if (path.isNotBlank()) out.add(path)
        }
        out.toList()
    }
    if (files.isEmpty()) return
    AxGlassSurface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        tier = AxGlassTier.Card,
        shape = RoundedCornerShape(AxRadius.lg)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "交付了 ${files.size} 个文件",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted
            )
            files.take(8).forEach { path ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(AxRadius.xs))
                            .background(colors.toolContainers[2]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            path.substringAfterLast('.', "file").take(3).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onToolContainers[2]
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            path.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (files.size > 8) {
                Text(
                    "还有 ${files.size - 8} 个文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textGhost,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** 已完成轮次折叠成一行；点开展开完整对话。 */
@Composable
private fun TurnSummaryRow(turn: Turn) {
    var expanded by rememberSaveable(turn.id) { mutableStateOf(false) }
    val colors = AxTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (turn.failedCount > 0) colors.warning else colors.textFaint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append("完成")
                    if (turn.stepCount > 0) append(" · ${turn.stepCount} 次工具调用")
                    val msgCount = turn.msgs.count { it.role == ChatMessage.Role.SAY || it.role == ChatMessage.Role.FINAL }
                    if (msgCount > 0) append(" · $msgCount 条消息")
                    if (turn.failedCount > 0) append(" · ${turn.failedCount} 步失败")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textGhost,
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(AxSpace.inTurn)
            ) {
                TurnBlock(turn, showUser = true)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.rule.copy(alpha = 0.6f))
    }
}

@Composable
fun EmptyState() {
    val templates = listOf(
        "查一下 Android 版本和机型，写进 notes.md",
        "整理通知栏的前 3 条消息，写进 msg.md",
        "把 Download/phone-agent 里的文件按类型分类",
        "从桌面找到并打开哔哩哔哩"
    )
    val colors = AxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = AxSpace.readingWidth)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("今天要做什么？", style = MaterialTheme.typography.displaySmall, color = colors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "直接说任务，Axgent 会自己看屏幕、点按钮、读写文件。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        Spacer(Modifier.height(22.dp))
        templates.forEach { t ->
            Box(Modifier.padding(vertical = 3.dp)) {
                AxPill(t, { AgentStore.startTask(t) }, modifier = Modifier.widthIn(max = 420.dp))
            }
        }
    }
}

@Composable
fun UserBubble(text: String, pending: Boolean = false) {
    val colors = AxTheme.colors
    val scale = LocalChatTextScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(AxRadius.lg))
                .background(colors.userBubble)
                .border(0.5.dp, colors.rule.copy(alpha = 0.35f), RoundedCornerShape(AxRadius.lg))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * scale).sp),
                color = colors.onUserBubble.copy(alpha = if (pending) 0.65f else 1f)
            )
        }
    }
}

@Composable
fun SayText(text: String) {
    if (text.isBlank()) return
    MarkdownText(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)
    )
}

@Composable
fun StepCard(id: String, step: StepItem?) {
    if (step == null) return
    ToolStepCard(id, step)
}

private sealed class ChatRow {
    abstract val key: String
    data class One(val msg: ChatMessage) : ChatRow() {
        override val key: String = msg.id
    }
    data class Steps(val id: String, val msgs: List<ChatMessage>) : ChatRow() {
        override val key: String = id
    }
}

@Composable
fun ProcessGroup(groupId: String, msgs: List<ChatMessage>, running: Boolean = false) {
    val failed = msgs.count { it.step?.ok == false }
    var expanded by rememberSaveable("$groupId-$running") {
        mutableStateOf(running && msgs.size <= 6)
    }
    val colors = AxTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (failed > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = when {
                    failed > 0 -> colors.warning
                    running -> colors.primary
                    else -> colors.textFaint
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append(if (running) "处理中" else "已完成")
                    append(" · ${msgs.size} 步")
                    if (failed > 0) append(" · $failed 步失败")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textGhost,
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                msgs.forEach { StepCard(it.id, it.step) }
            }
        }
    }
}

@Composable
fun FinalCard(
    id: String,
    text: String,
    sources: List<String> = emptyList(),
    promptTokens: Int = 0,
    contextWindow: Int = 0,
    stepCount: Int = 0
) {
    val colors = AxTheme.colors
    Column(Modifier.fillMaxWidth()) {
        MarkdownText(text, Modifier.fillMaxWidth())
        if (sources.isNotEmpty()) {
            FinalSources(id, sources)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val meta = buildString {
                if (stepCount > 0) append("$stepCount 步")
                if (promptTokens > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatTokens(promptTokens))
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint
                )
            }
            Spacer(Modifier.weight(1f))
            if (promptTokens > 0 && contextWindow > 0) {
                ContextUsageBadge(promptTokens, contextWindow)
            }
        }
    }
}

private fun formatTokens(n: Int): String =
    if (n >= 1000) String.format(java.util.Locale.US, "%.1fk tokens", n / 1000f) else "$n tokens"

/** 对话尾部的上下文使用率：小圆环 + 百分比，右下角低对比度。 */
@Composable
private fun ContextUsageBadge(promptTokens: Int, contextWindow: Int) {
    val ratio = (promptTokens.toFloat() / contextWindow).coerceIn(0f, 1f)
    val percent = ratio * 100f
    val label = when {
        percent >= 99.5f -> "100%"
        percent < 1f -> "<1%"
        else -> "${percent.roundToInt()}%"
    }
    val tint = AxTheme.colors.textFaint
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .size(13.dp)
                .semantics { contentDescription = AxLang.t("上下文使用率 $label") }
        ) {
            val stroke = 1.4.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            drawCircle(color = tint.copy(alpha = 0.28f), radius = radius, style = Stroke(stroke))
            if (ratio > 0f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    style = Stroke(stroke)
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun FinalSources(id: String, sources: List<String>) {
    val colors = AxTheme.colors
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.List, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "引用来源 · ${sources.size}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textGhost,
                modifier = Modifier.size(14.dp)
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(top = 4.dp, start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                sources.forEach { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LiveCard(
    text: String,
    reasoning: String,
    retry: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        if (retry.isNotBlank()) RetryCard(retry)
        if (reasoning.isNotBlank()) {
            ReasoningBlock(reasoning, streaming = text.isEmpty(), key = "live-reasoning")
        }
        if (text.isNotBlank()) {
            MarkdownText(text, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CompactionCard(text: String) {
    val colors = AxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
fun RetryCard(text: String) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(colors.warningContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onWarningContainer)
    }
}

@Composable
fun NoticeCard(text: String) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(colors.infoContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = colors.info, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onInfoContainer)
    }
}

private fun thinkingLabel(level: String): String =
    ThinkingOptions.firstOrNull { it.first == level }?.second ?: "默认"

@Composable
private fun ComposerChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AxTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.pill.fill)
            .border(0.5.dp, colors.pill.hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.textGhost,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun InlineMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.primary else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun ComposerMenu(
    menu: String?,
    onMenuChange: (String?) -> Unit,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (menu == null) return
    val cfg = AgentStore.config
    val modelMenu = menu == "model"
    AxGlassSurface(
        modifier = modifier.fillMaxWidth(),
        tier = AxGlassTier.Sheet,
        shape = RoundedCornerShape(AxRadius.lg),
        shadow = 10.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (modelMenu) "选择模型" else "思考强度",
                style = MaterialTheme.typography.labelMedium,
                color = AxTheme.colors.textFaint,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
            if (modelMenu) {
                if (cfg.chatModels.isEmpty()) {
                    InlineMenuItem("暂无模型", false) {}
                } else {
                    cfg.chatModels.forEach { p ->
                        InlineMenuItem(p.model, p.id == cfg.activeModelId) {
                            AgentStore.selectModel(p.id)
                            onMenuChange(null)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = AxTheme.colors.rule)
                InlineMenuItem("新建模型连接", false) {
                    onMenuChange(null)
                    onOpenModels()
                }
            } else {
                ThinkingOptions.forEach { (value, label) ->
                    InlineMenuItem(label, cfg.thinking == value) {
                        AgentStore.setThinking(value)
                        onMenuChange(null)
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerBar(
    menu: String? = null,
    onMenuChange: (String?) -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf("") }
    val modelMenu = menu == "model"
    val thinkMenu = menu == "think"
    BackHandler(enabled = modelMenu || thinkMenu) { onMenuChange(null) }
    val status = AgentStore.status
    val running = status == AgentStatus.RUNNING || status == AgentStatus.PAUSED || status == AgentStatus.WAITING
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = AgentStore.config
    val colors = AxTheme.colors
    val needsA11y = (cfg.capabilities.screenRead || cfg.capabilities.gui || cfg.capabilities.vision) && !cfg.virtualDisplay
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val a = withContext(Dispatchers.IO) { AgentStore.attach(uri) }
            if (a == null) {
                Toast.makeText(context, AgentStore.lastError ?: "附件读取失败", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "已附加：${a.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = AxSpace.floatInset, end = AxSpace.floatInset, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AgentStore.pendingApproval?.let { ap -> ApprovalCard(ap) }
        AgentStore.pendingQuestion?.let { q -> QuestionCard(q) }
        if (running && (AgentStore.planSteps.isNotEmpty() || AgentStore.todos.isNotEmpty())) {
            PlanTodoPanel()
        }
        if (running || status == AgentStatus.FAILED) StatusRow()

        AgentStore.queuedMessage?.let { q ->
            QueuedBanner(
                text = q,
                onSendNow = { AgentStore.sendQueuedNow() },
                onCancel = { AgentStore.clearQueuedMessage() }
            )
        }

        AxGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = AxGlassTier.Chrome,
            shape = RoundedCornerShape(AxRadius.xl),
            shadow = 8.dp
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                AgentStore.pendingAttachment?.let { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(AxRadius.sm))
                            .background(colors.pill.fill)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (a.isImage) "图片：" else "附件：") + a.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = AxLang.t("移除附件"),
                            tint = colors.textGhost,
                            modifier = Modifier.size(16.dp).clickable { AgentStore.clearAttachment() }
                        )
                    }
                }
                // 单行时在 48dp 输入区内垂直居中，让文字和光标离顶边更远；
                // 多行时输入区随内容增高，文字仍从顶部自然排列。
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            if (running) "给 Axgent 发消息…（排队，本轮结束后发送）" else "给 Axgent 发消息…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textGhost
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.primary),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!running) {
                        AxCircleButton(
                            Icons.Filled.Add,
                            "附件",
                            { attachmentLauncher.launch(arrayOf("*/*")) },
                            size = 34.dp,
                            container = colors.pill.fill,
                            tint = colors.textMuted
                        )
                    } else {
                        AxPill(if (status == AgentStatus.PAUSED) "继续" else "暂停", {
                            if (status == AgentStatus.PAUSED) {
                                AgentStore.resumeTask()
                                Toast.makeText(context, "已继续", Toast.LENGTH_SHORT).show()
                            } else {
                                AgentStore.pauseTask()
                                Toast.makeText(context, "已暂停，虚拟屏保持", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                    Spacer(Modifier.width(6.dp))
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 模型选择已移到顶部标题栏右侧，这里只保留思考强度。
                        ComposerChip("思考 ${thinkingLabel(cfg.thinking)}") {
                            onMenuChange(if (thinkMenu) null else "think")
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    if (running) {
                        AxCircleButton(
                            Icons.Filled.Close,
                            "停止",
                            {
                                AgentStore.clearQueuedMessage()
                                AgentStore.stop()
                                Toast.makeText(context, "已结束", Toast.LENGTH_SHORT).show()
                            },
                            size = 38.dp,
                            container = colors.dangerContainer,
                            tint = colors.danger
                        )
                    }
                    AxCircleButton(
                        Icons.Filled.Send,
                        if (running) "排队" else "发送",
                        {
                            val t = text.trim()
                            if (t.isEmpty()) {
                                Toast.makeText(context, "内容为空，无法发送", Toast.LENGTH_SHORT).show()
                            } else if (running) {
                                AgentStore.queueMessage(t)
                                text = ""
                                keyboard?.hide()
                                focus.clearFocus()
                                Toast.makeText(context, "已排队，本轮结束后发送", Toast.LENGTH_SHORT).show()
                            } else if (needsA11y && !AgentStore.hasAccessibility()) {
                                scope.launch {
                                    val fixed = withContext(Dispatchers.IO) { AgentStore.ensureAccessibilityByRoot() }
                                    if (fixed) {
                                        Toast.makeText(context, "已通过 Shizuku/root 开启无障碍", Toast.LENGTH_SHORT).show()
                                        AgentStore.startTask(t)
                                        text = ""
                                        keyboard?.hide()
                                        focus.clearFocus()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "无障碍未开启，且 Shizuku/root 均不可用；请手动开启 Axgent 的无障碍服务",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        AgentStore.openAccessibilitySettings()
                                    }
                                }
                            } else {
                                AgentStore.startTask(t)
                                text = ""
                                keyboard?.hide()
                                focus.clearFocus()
                            }
                        },
                        size = 38.dp,
                        enabled = text.isNotBlank(),
                        container = if (text.isNotBlank()) colors.primary else colors.pill.fill,
                        tint = if (text.isNotBlank()) colors.onPrimary else colors.textGhost
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedBanner(text: String, onSendNow: () -> Unit, onCancel: () -> Unit) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.warningContainer.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onWarningContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "排队中 · 本轮结束后发送",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning
            )
            Spacer(Modifier.weight(1f))
            Text(
                "立即发送",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier.clickable(onClick = onSendNow).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                "撤回",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun CapabilitiesDialog(onDismiss: () -> Unit) {
    val cfg = AgentStore.config
    val caps = cfg.capabilities
    val privilege = cfg.privilege
    var pendingRootEnable by remember { mutableStateOf(false) }
    val colors = AxTheme.colors
    AxDialog(
        onDismiss = onDismiss,
        title = "能力",
        confirmText = "完成",
        dismissText = "关闭",
        onConfirm = onDismiss
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "控制下一次任务的工具与观察能力；运行中的任务不改变。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            CapabilityRow(
                title = "Agent 无障碍权限",
                subtitle = "允许 Agent 使用无障碍观察与操作界面；全局无障碍关闭时置灰",
                checked = caps.a11y,
                enabled = privilege.accessibility,
                onCheckedChange = { AgentStore.setCapability("a11y", it) }
            )
            CapabilityRow(
                title = "Agent Shizuku 权限",
                subtitle = "允许 Agent 通过 Shizuku 执行 shell；全局 Shizuku 关闭时置灰",
                checked = caps.shizuku,
                enabled = privilege.shizuku,
                onCheckedChange = { AgentStore.setCapability("shizuku", it) }
            )
            CapabilityRow(
                title = "Agent Root权限",
                subtitle = if (privilege.root) {
                    "允许 root_shell 等工具以 root 执行；关闭时在工具执行层硬拦截，不依赖提示词"
                } else {
                    "全局 Root 权限未开启，需先在「运行与安全 → 权限分级」中开启"
                },
                checked = caps.root,
                enabled = privilege.root,
                onCheckedChange = { enabled ->
                    if (enabled) pendingRootEnable = true else AgentStore.setCapability("root", false)
                }
            )
            if (caps.root) {
                Text(
                    "Root 权限已开启：模型可直接执行任意 root 命令，请只在可信任务中开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = colors.rule)
            CapabilityRow("屏幕读取", "控件树、OCR、等待文字；关闭后 Agent 看不到界面", caps.screenRead) {
                AgentStore.setCapability("screenRead", it)
            }
            CapabilityRow("文件读取", "读取、列出、搜索、查看文件信息", caps.fileRead) {
                AgentStore.setCapability("fileRead", it)
            }
            CapabilityRow("主模型视觉", "允许主模型直接读图；视觉模型不可用时作为兜底", caps.mainVision) {
                AgentStore.setCapability("mainVision", it)
            }
            CapabilityRow("视觉模型视觉", "配置了视觉模型时优先使用；关闭后视觉只走主模型", caps.visionModelVision) {
                AgentStore.setCapability("visionModelVision", it)
            }
            if (!caps.mainVision && !caps.visionModelVision) {
                Text(
                    "两项都关闭时视觉能力关闭：look 与自动看屏不可用；OCR 仍归屏幕读取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            CapabilityRow("Web", "网页搜索与正文抓取", caps.web) { AgentStore.setCapability("web", it) }
            CapabilityRow("GUI 操作", "点击、输入、滑动、返回、桌面、启动 App", caps.gui) {
                AgentStore.setCapability("gui", it)
            }
            CapabilityRow("文件编辑", "写入、追加、替换、建目录、复制", caps.fileEdit) {
                AgentStore.setCapability("fileEdit", it)
            }
            CapabilityRow("沙箱内高风险操作", "删除、移动、批量；仅限沙箱根目录，默认开启", caps.sandboxHighRisk, caps.fileEdit) {
                AgentStore.setCapability("sandboxHighRisk", it)
            }
            CapabilityRow("沙箱外文件操作", "读写、删除、列目录、搜索沙箱外路径；默认关闭，代码强制校验", caps.outsideSandbox) {
                AgentStore.setCapability("outsideSandbox", it)
            }
            CapabilityRow("设备与通知", "设备信息、应用列表、读取通知", caps.device) {
                AgentStore.setCapability("device", it)
            }
            CapabilityRow("剪贴板", "读取与写入剪贴板", caps.clipboard) {
                AgentStore.setCapability("clipboard", it)
            }
            CapabilityRow("规划与待办", "计划、清单、关键事实记录", caps.plan) {
                AgentStore.setCapability("plan", it)
            }
            if (!caps.screenRead) {
                Text(
                    "屏幕读取已关闭：GUI 操作会变成盲点，建议只用于文件/Web/设备任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (caps.outsideSandbox) {
                Text(
                    "沙箱外文件操作已开启：路径不再受沙箱 roots 保护，请确认你真的要允许。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (pendingRootEnable) {
        AxDialog(
            onDismiss = { pendingRootEnable = false },
            title = "开启 Agent Root权限？",
            confirmText = "开启",
            danger = true,
            onConfirm = {
                pendingRootEnable = false
                AgentStore.setCapability("root", true)
            }
        ) {
            Text(
                "开启后，模型可以执行任意 root 系统命令（包括修改系统设置、删除文件、安装卸载应用）。" +
                    "每次执行会写入 root-audit.jsonl，命令超过 30 秒会被终止。请仅在可信任务中使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.text else colors.textGhost
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun ApprovalCard(ap: ApprovalRequest) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.warningContainer.copy(alpha = 0.72f))
            .border(0.5.dp, colors.warning.copy(alpha = 0.55f), RoundedCornerShape(AxRadius.md))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("需要你的确认", style = MaterialTheme.typography.labelLarge, color = colors.onWarningContainer)
            Spacer(Modifier.weight(1f))
            Text("第 ${ap.step} 步", style = MaterialTheme.typography.labelSmall, color = colors.textFaint)
        }
        Text(
            "工具：${ap.tool}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (ap.args.isNotBlank()) {
            Text(
                ap.args.take(600),
                style = axMono(color = colors.textMuted),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "拒绝",
                style = MaterialTheme.typography.labelMedium,
                color = colors.danger,
                modifier = Modifier.clickable { AgentStore.decideApproval(false) }.padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Text(
                "允许",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier.clickable { AgentStore.decideApproval(true) }.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/** ask_user 的提问卡：选项按钮 + 自由文本。 */
@Composable
fun QuestionCard(q: AgentQuestion) {
    var text by rememberSaveable { mutableStateOf("") }
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.infoContainer.copy(alpha = 0.65f))
            .border(0.5.dp, colors.info.copy(alpha = 0.45f), RoundedCornerShape(AxRadius.md))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.info, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Axgent 需要你的回答", style = MaterialTheme.typography.labelLarge, color = colors.onInfoContainer)
        }
        Text(
            q.question,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (q.options.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                q.options.forEach { opt ->
                    AxPill(opt, { AgentStore.answerQuestion(opt) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AxTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "输入回答…",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "回答",
                style = MaterialTheme.typography.labelMedium,
                color = if (text.isNotBlank()) colors.primary else colors.textGhost,
                modifier = Modifier
                    .clickable(enabled = text.isNotBlank()) {
                        AgentStore.answerQuestion(text)
                        text = ""
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun PlanTodoPanel() {
    val colors = AxTheme.colors
    val done = AgentStore.todos.count { it.status == "completed" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.tertiaryContainer.copy(alpha = 0.55f))
            .border(0.5.dp, colors.tertiary.copy(alpha = 0.35f), RoundedCornerShape(AxRadius.md))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("计划", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
            if (AgentStore.todos.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "$done / ${AgentStore.todos.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
        if (AgentStore.planSteps.isNotEmpty()) {
            Text(
                AgentStore.planSteps.joinToString(" → "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        val current = AgentStore.todos.firstOrNull { it.status == "in_progress" }
            ?: AgentStore.todos.firstOrNull { it.status != "completed" }
        if (current != null) {
            Text(
                current.content,
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun StatusRow() {
    val status = AgentStore.status
    val label = when (status) {
        AgentStatus.RUNNING -> "第 ${AgentStore.totalSteps} 步 · ${AgentStore.currentTool} · ${AgentStore.elapsedSec}s"
        AgentStatus.PAUSED -> if (AgentStore.config.virtualDisplay) "已暂停，虚拟屏保持；点「继续」恢复" else "已暂停，长按音量下继续"
        AgentStatus.WAITING -> AgentStore.pendingApproval?.let { "等待确认：${it.tool}" } ?: "等待人工接管"
        AgentStatus.FAILED -> AgentStore.lastError ?: "任务失败"
        AgentStatus.DONE -> "已完成"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AxTheme.colors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

