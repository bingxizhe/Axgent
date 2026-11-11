@file:OptIn(ExperimentalFoundationApi::class)

package org.autojs.autojs.agent

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onOpen: () -> Unit, onMenu: (() -> Unit)? = null) {
    var sessions by remember { mutableStateOf<List<AgentStore.ConversationSummary>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val colors = AxTheme.colors
    val context = LocalContext.current
    val current = AgentStore.conversationId

    LaunchedEffect(Unit) {
        while (true) {
            sessions = withContext(Dispatchers.IO) {
                runCatching { AgentStore.conversationSummaries() }.getOrDefault(emptyList())
            }
            delay(2000)
        }
    }

    val filtered = remember(sessions, query) {
        val q = query.trim()
        if (q.isEmpty()) sessions else sessions.filter {
            it.title.contains(q, true)
        }
    }
    val deletable = remember(filtered, current) {
        filtered.filter { it.id != current || AgentStore.status == AgentStatus.IDLE }
    }

    Column(Modifier.fillMaxSize()) {
        AxPageHeader(
            title = "会话",
            subtitle = if (selecting) "已选 ${selected.size} 段会话" else "搜索、切换或批量删除历史会话",
            onMenu = onMenu
        )
        Box(Modifier.padding(horizontal = AxSpace.gutter)) {
            AxSearchField(query, { query = it }, "搜索会话")
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AxSpace.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.clickable {
                        selecting = false
                        selected.clear()
                    }.padding(vertical = 6.dp, horizontal = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (selected.size >= deletable.size && deletable.isNotEmpty()) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    modifier = Modifier.clickable {
                        if (selected.size >= deletable.size && deletable.isNotEmpty()) selected.clear()
                        else {
                            selected.clear()
                            selected.addAll(deletable.map { it.id })
                        }
                    }.padding(vertical = 6.dp, horizontal = 8.dp)
                )
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected.isNotEmpty()) colors.danger else colors.textGhost,
                    modifier = Modifier
                        .clickable(enabled = selected.isNotEmpty()) { confirmDelete = true }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    "管理",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    modifier = Modifier.clickable { selecting = true }.padding(vertical = 6.dp, horizontal = 8.dp)
                )
            }
        }
        if (filtered.isEmpty()) {
            AxEmptyState(
                title = if (sessions.isEmpty()) "还没有会话" else "没有匹配的会话",
                subtitle = if (sessions.isEmpty()) "发送第一个任务后，会话会出现在这里。" else "换个关键词试试。"
            )
        } else {
            var lastGroup = ""
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AxSpace.gutter, end = AxSpace.gutter, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filtered.forEach { s ->
                    val group = sessionGroup(s.lastModified)
                    if (group != lastGroup) {
                        lastGroup = group
                        item(key = "g-$group") {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textGhost,
                                modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = s.id) {
                        SessionCard(
                            session = s,
                            active = s.id == current,
                            selecting = selecting,
                            checked = s.id in selected,
                            canSelect = s.id != current || AgentStore.status == AgentStatus.IDLE,
                            onLongClick = {
                                if (s.id != current || AgentStore.status == AgentStatus.IDLE) {
                                    if (!selecting) {
                                        selecting = true
                                        selected.clear()
                                    }
                                    if (s.id in selected) selected.remove(s.id) else selected.add(s.id)
                                }
                            },
                            onClick = {
                                if (selecting) {
                                    if (s.id in selected) selected.remove(s.id) else if (s.id != current || AgentStore.status == AgentStatus.IDLE) selected.add(s.id)
                                } else {
                                    AgentStore.openConversation(s.id)
                                    onOpen()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AxDialog(
            onDismiss = { confirmDelete = false },
            title = "删除 ${selected.size} 段会话",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                val ok = AgentStore.deleteConversations(selected.toList())
                selected.clear()
                selecting = false
                confirmDelete = false
                if (!ok) Toast.makeText(context, "任务运行中，暂不能删除会话", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(
                "将删除这些会话的全部运行记录与对话内容。此操作不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: AgentStore.ConversationSummary,
    active: Boolean,
    selecting: Boolean,
    checked: Boolean,
    canSelect: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val colors = AxTheme.colors
    AxGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        tier = AxGlassTier.Card,
        shape = RoundedCornerShape(AxRadius.lg)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(
                            if (checked) colors.primary else colors.surfaceHigh,
                            CircleShape
                        )
                        .border(1.dp, if (checked) colors.primary else colors.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.running) {
                        AxStatusDot(colors.primary, 6.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("运行中", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    } else if (session.done) {
                        Text("已完成", style = MaterialTheme.typography.labelSmall, color = colors.success)
                    } else {
                        Text("未完成", style = MaterialTheme.typography.labelSmall, color = colors.textFaint)
                    }
                    Text(
                        "  ·  ${session.runCount} 轮 · ${relativeTime(session.lastModified)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint
                    )
                }
            }
            if (active) {
                Text("当前", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            } else if (!selecting) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = null,
                    tint = colors.textGhost,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun sessionGroup(ts: Long): String {
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

/**
 * 定时任务：任务文本 + 固定间隔（分钟），由 AgentScheduleStore 的闹钟触发。
 * 存储与 Agent 工具共用 schedules.json。
 */
@Composable
fun ScheduleScreen(onMenu: (() -> Unit)? = null) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val schedules = remember(tick) { AgentScheduleStore.load() }
    var showAdd by remember { mutableStateOf(false) }
    var taskText by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf("60") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val colors = AxTheme.colors

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    Column(Modifier.fillMaxSize()) {
        AxPageHeader(
            title = "定时任务",
            subtitle = "到达间隔后自动执行一次任务（沿用当前对话）；同一时间只跑一个任务，冲突时跳过本次。",
            onMenu = onMenu
        )
        if (!AgentScheduleStore.canScheduleExact(context)) {
            Box(Modifier.padding(horizontal = AxSpace.gutter)) {
                Text(
                    "未获得精确闹钟权限，触发时间可能被系统延后。点「新建」时会跳转到授权页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AxSpace.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            AxPill("＋ 新建", {
                if (!AgentScheduleStore.canScheduleExact(context)) {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "请在系统设置中允许「闹钟和提醒」", Toast.LENGTH_LONG).show()
                    }
                }
                showAdd = true
            }, selected = true)
        }
        Spacer(Modifier.height(8.dp))
        if (schedules.isEmpty()) {
            AxEmptyState("还没有定时任务", "新建一个间隔任务，Axgent 会在后台按时执行。")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AxSpace.gutter, end = AxSpace.gutter, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules, key = { it.id }) { s ->
                    AxGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        tier = AxGlassTier.Card,
                        shape = RoundedCornerShape(AxRadius.lg)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                s.task,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (s.enabled) "每 ${s.intervalMinutes} 分钟 · 下次 ${timeLabel(s.nextAt)}"
                                    else "每 ${s.intervalMinutes} 分钟 · 已暂停",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = s.enabled,
                                    onCheckedChange = { checked ->
                                        AgentScheduleStore.setEnabled(context, s.id, checked)
                                        tick++
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onPrimary,
                                        checkedTrackColor = colors.primary
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.danger,
                                    modifier = Modifier.clickable { deleteId = s.id }.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        val minutes = intervalText.trim().toIntOrNull()
        val valid = taskText.trim().isNotEmpty() && minutes != null && minutes >= 1
        AxDialog(
            onDismiss = { showAdd = false },
            title = "新建定时任务",
            confirmText = "创建",
            confirmEnabled = valid,
            onConfirm = {
                val m = minutes ?: return@AxDialog
                AgentScheduleStore.add(context, taskText.trim(), m)
                taskText = ""
                intervalText = "60"
                showAdd = false
                tick++
            }
        ) {
            AxTextField(
                value = taskText,
                onValueChange = { taskText = it },
                label = "任务内容",
                minLines = 3,
                maxLines = 5,
                singleLine = false
            )
            Spacer(Modifier.height(10.dp))
            AxTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                label = "间隔（分钟）",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        }
    }

    val id = deleteId
    if (id != null) {
        AxDialog(
            onDismiss = { deleteId = null },
            title = "删除定时任务",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                AgentScheduleStore.remove(context, id)
                deleteId = null
                tick++
            }
        ) {
            Text(
                "删除后不会影响已经产生的对话记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }
    }
}

private fun timeLabel(ts: Long): String {
    if (ts <= 0L) return ""
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    return String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

fun relativeTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        else -> "${d / 86_400_000} 天前"
    }
}
