package org.autojs.autojs.agent

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ToolKind {
    GUI, READ, WRITE, EDIT, DELETE, MOVE, COPY, BATCH, SEARCH,
    RUN, PLAN, TODO, VISION, NOTE, TASK, NOTIFY, WEB, OTHER
}

fun toolKind(tool: String): ToolKind = when {
    tool == "gui_launchApp" -> ToolKind.RUN
    tool.startsWith("gui_") -> ToolKind.GUI
    tool == "file_read" || tool == "file_list" || tool == "file_stat" -> ToolKind.READ
    tool == "file_search" -> ToolKind.SEARCH
    tool == "file_write" || tool == "file_append" -> ToolKind.WRITE
    tool == "file_edit" -> ToolKind.EDIT
    tool == "file_remove" -> ToolKind.DELETE
    tool == "file_move" -> ToolKind.MOVE
    tool == "file_copy" -> ToolKind.COPY
    tool == "file_batch" -> ToolKind.BATCH
    tool == "plan_set" -> ToolKind.PLAN
    tool == "todo_write" -> ToolKind.TODO
    tool == "task_note" -> ToolKind.NOTE
    tool == "task_done" -> ToolKind.TASK
    tool == "look" || tool == "shot" -> ToolKind.VISION
    tool == "web_search" || tool == "web_fetch" -> ToolKind.WEB
    tool.startsWith("notify") -> ToolKind.NOTIFY
    else -> ToolKind.OTHER
}

fun toolTitle(kind: ToolKind, tool: String = ""): String = when (kind) {
    ToolKind.GUI -> "操作界面"
    ToolKind.READ -> "读取文件"
    ToolKind.WRITE -> "写入文件"
    ToolKind.EDIT -> "编辑文件"
    ToolKind.DELETE -> "删除文件"
    ToolKind.MOVE -> "移动文件"
    ToolKind.COPY -> "复制文件"
    ToolKind.BATCH -> "批量文件操作"
    ToolKind.SEARCH -> "搜索文件"
    ToolKind.RUN -> "启动应用"
    ToolKind.PLAN -> "制定计划"
    ToolKind.TODO -> "更新清单"
    ToolKind.VISION -> if (tool == "shot") "截屏" else "识别界面"
    ToolKind.NOTE -> "记录事实"
    ToolKind.TASK -> "完成任务"
    ToolKind.NOTIFY -> "通知操作"
    ToolKind.WEB -> if (tool == "web_fetch") "读取网页" else "网页搜索"
    ToolKind.OTHER -> tool.ifBlank { "工具调用" }
}

fun toolIcon(kind: ToolKind): ImageVector = when (kind) {
    ToolKind.GUI -> Icons.Filled.Build
    ToolKind.READ -> Icons.Filled.List
    ToolKind.WRITE -> Icons.Filled.Edit
    ToolKind.EDIT -> Icons.Filled.Edit
    ToolKind.DELETE -> Icons.Filled.Delete
    ToolKind.MOVE -> Icons.Filled.ArrowForward
    ToolKind.COPY -> Icons.Filled.Share
    ToolKind.BATCH -> Icons.Filled.List
    ToolKind.SEARCH -> Icons.Filled.Search
    ToolKind.RUN -> Icons.Filled.PlayArrow
    ToolKind.PLAN, ToolKind.TODO -> Icons.Filled.CheckCircle
    ToolKind.VISION -> Icons.Filled.Face
    ToolKind.NOTE -> Icons.Filled.Info
    ToolKind.TASK -> Icons.Filled.CheckCircle
    ToolKind.NOTIFY -> Icons.Filled.Notifications
    ToolKind.WEB -> Icons.Filled.Search
    ToolKind.OTHER -> Icons.Filled.Build
}

private fun toolToneIndex(kind: ToolKind): Int = when (kind) {
    ToolKind.GUI, ToolKind.RUN -> 0
    ToolKind.READ, ToolKind.VISION -> 1
    ToolKind.WRITE, ToolKind.EDIT, ToolKind.MOVE, ToolKind.COPY -> 2
    ToolKind.SEARCH, ToolKind.BATCH -> 3
    ToolKind.WEB -> 4
    ToolKind.TASK, ToolKind.NOTIFY -> 5
    ToolKind.PLAN, ToolKind.TODO -> 6
    else -> 7
}

@Composable
fun toolAccent(kind: ToolKind): Color {
    val colors = AxTheme.colors
    return colors.onToolContainers[toolToneIndex(kind) % colors.onToolContainers.size]
}

@Composable
fun toolContainer(kind: ToolKind): Color {
    val colors = AxTheme.colors
    return colors.toolContainers[toolToneIndex(kind) % colors.toolContainers.size]
}

@Composable
fun ReasoningBlock(text: String, streaming: Boolean, key: String) {
    if (text.isBlank()) return
    // 流式思考同样只展示尾部，避免长推理文本反复排版导致掉帧；最终步骤卡片保留全文。
    val shownText = if (streaming && text.length > 800) "…\n" + text.takeLast(800) else text
    val colors = AxTheme.colors
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (streaming) "思考中…" else "思考过程",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${text.length} 字",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textGhost
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textGhost,
                modifier = Modifier.size(15.dp)
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(AxRadius.sm))
                    .background(colors.surfaceLow.copy(alpha = 0.6f))
                    .verticalScroll(rememberScrollState())
                    .padding(9.dp)
            ) {
                Text(
                    shownText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        } else {
            Text(
                shownText.replace("\n", " ").take(100),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textGhost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolStepCard(id: String, step: StepItem) {
    val kind = toolKind(step.tool)
    val colors = AxTheme.colors
    val accent = toolAccent(kind)
    val container = toolContainer(kind)
    val args = remember(step.args) {
        try {
            if (step.args.isBlank()) null else JSONObject(step.args)
        } catch (e: Exception) {
            null
        }
    }
    val summary = remember(kind, step.tool, step.args, step.preview) {
        when (kind) {
            ToolKind.GUI -> guiSummary(step, args)
            ToolKind.READ -> fileName(args?.optString("path").orEmpty())
            ToolKind.WRITE -> fileName(args?.optString("path").orEmpty()) +
                if (step.preview.startsWith("bytes=")) " · ${step.preview}" else ""
            ToolKind.EDIT -> fileName(args?.optString("path").orEmpty()) +
                if (step.preview.startsWith("replaced=")) " · ${step.preview}" else ""
            ToolKind.DELETE -> fileName(args?.optString("path").orEmpty())
            ToolKind.MOVE, ToolKind.COPY ->
                fileName(args?.optString("from").orEmpty()) + " → " + fileName(args?.optString("to").orEmpty())
            ToolKind.BATCH -> {
                val n = args?.optJSONArray("ops")?.length() ?: 0
                "$n 项" + if (step.preview.isNotBlank()) " · ${step.preview}" else ""
            }
            ToolKind.SEARCH -> "目录 " + fileName(args?.optString("dir").orEmpty()) +
                " · " + args?.optString("keyword").orEmpty()
            ToolKind.RUN -> "pkg=" + (args?.optString("package").orEmpty().ifBlank { args?.optString("pkg").orEmpty() })
            ToolKind.PLAN -> (args?.optJSONArray("steps")?.length() ?: 0).toString() + " 步计划"
            ToolKind.TODO -> (args?.optJSONArray("todos")?.length() ?: 0).toString() + " 项待办"
            ToolKind.VISION -> if (step.tool == "shot") "截屏" else "查看当前界面"
            ToolKind.NOTE -> step.preview.ifBlank { args?.optString("text").orEmpty() }
            ToolKind.TASK -> step.preview.ifBlank { args?.optString("summary").orEmpty() }
            ToolKind.NOTIFY -> args?.toString().orEmpty().take(100)
            ToolKind.WEB -> if (step.tool == "web_fetch") {
                val ua = args?.optJSONArray("urls")
                if (ua != null && ua.length() > 0) "${ua.length()} 个网页" else args?.optString("url").orEmpty()
            } else "搜索：" + args?.optString("query").orEmpty()
            ToolKind.OTHER -> ""
        }
    }
    val hasDetails = step.err.isNotBlank() || step.args.isNotBlank() || step.preview.isNotBlank() || step.result.isNotBlank()
    var expanded by rememberSaveable(id) { mutableStateOf(!step.ok && hasDetails) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = hasDetails) { expanded = !expanded }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(AxRadius.xs))
                    .background(if (step.ok) container else colors.dangerContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    toolIcon(kind),
                    contentDescription = null,
                    tint = if (step.ok) accent else colors.danger,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                toolTitle(kind, step.tool),
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                maxLines = 1
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!step.ok) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            if (hasDetails) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textGhost,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 2.dp, bottom = 8.dp)
            ) {
                if (step.reasoning.isNotBlank()) {
                    ReasoningBlock(step.reasoning, false, "reason-$id")
                    Spacer(Modifier.height(4.dp))
                }
                ToolDetails(kind, step, args)
            }
        }
    }
}

@Composable
private fun ToolSummary(kind: ToolKind, step: StepItem, args: JSONObject?) {
    val summary = when (kind) {
        ToolKind.GUI -> guiSummary(step, args)
        ToolKind.READ -> fileName(args?.optString("path").orEmpty())
        ToolKind.WRITE -> fileName(args?.optString("path").orEmpty()) +
            if (step.preview.startsWith("bytes=")) " · ${step.preview}" else ""
        ToolKind.EDIT -> fileName(args?.optString("path").orEmpty()) +
            if (step.preview.startsWith("replaced=")) " · ${step.preview}" else ""
        ToolKind.DELETE -> fileName(args?.optString("path").orEmpty())
        ToolKind.MOVE, ToolKind.COPY ->
            fileName(args?.optString("from").orEmpty()) + " → " + fileName(args?.optString("to").orEmpty())
        ToolKind.BATCH -> {
            val n = args?.optJSONArray("ops")?.length() ?: 0
            "$n 项" + if (step.preview.isNotBlank()) " · ${step.preview}" else ""
        }
        ToolKind.SEARCH -> "目录 " + fileName(args?.optString("dir").orEmpty()) +
            " · " + args?.optString("keyword").orEmpty()
        ToolKind.RUN -> "pkg=" + (args?.optString("package").orEmpty().ifBlank { args?.optString("pkg").orEmpty() })
        ToolKind.PLAN -> (args?.optJSONArray("steps")?.length() ?: 0).toString() + " 步计划"
        ToolKind.TODO -> (args?.optJSONArray("todos")?.length() ?: 0).toString() + " 项待办"
        ToolKind.VISION -> if (step.tool == "shot") "截屏" else "查看当前界面"
        ToolKind.NOTE -> step.preview.ifBlank { args?.optString("text").orEmpty() }
        ToolKind.TASK -> step.preview.ifBlank { args?.optString("summary").orEmpty() }
        ToolKind.NOTIFY -> args?.toString().orEmpty().take(100)
        ToolKind.WEB -> if (step.tool == "web_fetch") {
            val ua = args?.optJSONArray("urls")
            if (ua != null && ua.length() > 0) "${ua.length()} 个网页" else args?.optString("url").orEmpty()
        } else "搜索：" + args?.optString("query").orEmpty()
        ToolKind.OTHER -> ""
    }
    if (summary.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun guiSummary(step: StepItem, args: JSONObject?): String {
    if (args == null) return ""
    val i = args.optInt("i", -1)
    return when (step.tool) {
        "gui_tap" -> if (i >= 0) "点击节点 #$i" else "点击"
        "gui_tapXY" -> "点击 (${args.optInt("x")}, ${args.optInt("y")})"
        "gui_setText" -> "输入「" + args.optString("text").take(40) + "」"
        "gui_key" -> "按键 " + args.optString("key")
        "gui_swipe" -> "滑动"
        "gui_longPress" -> if (i >= 0) "长按节点 #$i" else "长按"
        "gui_launchApp" -> "pkg=" + args.optString("package")
        else -> if (i >= 0) "节点 #$i" else ""
    }
}

@Composable
private fun ToolDetails(kind: ToolKind, step: StepItem, args: JSONObject?) {
    if (step.err.isNotBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Text(
                step.err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
    when (kind) {
        ToolKind.READ -> {
            CopyablePath(args?.optString("path").orEmpty())
            if (step.preview.isNotBlank()) CodeView(step.preview, langOf(args?.optString("path").orEmpty()), 220.dp)
        }
        ToolKind.WRITE -> {
            CopyablePath(args?.optString("path").orEmpty())
            val content = args?.optString("content").orEmpty()
            if (content.isNotBlank()) CodeView(content, langOf(args?.optString("path").orEmpty()), 260.dp)
            if (step.preview.isNotBlank() && !step.preview.startsWith("bytes=")) {
                Text(step.preview, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        ToolKind.EDIT -> {
            CopyablePath(args?.optString("path").orEmpty())
            DiffView(args?.optString("old").orEmpty(), args?.optString("new").orEmpty())
        }
        ToolKind.DELETE -> {
            CopyablePath(args?.optString("path").orEmpty())
            if (step.preview.startsWith("movedTo=")) {
                Text(
                    "已移入回收站：${step.preview.removePrefix("movedTo=")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        ToolKind.MOVE, ToolKind.COPY -> {
            CopyablePath(args?.optString("from").orEmpty())
            Text("→", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 2.dp))
            CopyablePath(args?.optString("to").orEmpty())
        }
        ToolKind.BATCH -> BatchList(args?.optJSONArray("ops"), step.preview)
        ToolKind.SEARCH -> {
            Text(
                "目录：${args?.optString("dir").orEmpty()}  关键词：${args?.optString("keyword").orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (step.preview.isNotBlank()) CodeView(step.preview, "paths", 200.dp)
        }
        ToolKind.RUN -> {
            CopyablePath(args?.optString("package").orEmpty().ifBlank { args?.optString("pkg").orEmpty() })
            if (step.preview.isNotBlank()) CodeView(step.preview, "text", 160.dp)
        }
        ToolKind.PLAN -> PlanList(args?.optJSONArray("steps"))
        ToolKind.TODO -> TodoList(args?.optJSONArray("todos"))
        ToolKind.VISION -> if (step.tool == "shot") ShotImage(step.preview) else CodeView(step.preview.ifBlank { "（无视觉结果）" }, "text", 220.dp)
        ToolKind.WEB -> {
            if (step.tool == "web_fetch") CopyablePath(args?.optString("url").orEmpty())
            CodeView(step.preview.ifBlank { "（无网页结果）" }, "text", 260.dp)
        }
        ToolKind.NOTE, ToolKind.TASK -> Text(
            step.preview.ifBlank { args?.toString().orEmpty() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ToolKind.GUI -> {
            ArgsTable(args)
            if (step.preview.isNotBlank()) Text(step.preview, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        ToolKind.NOTIFY, ToolKind.OTHER -> ArgsTable(args)
    }
    if (step.result.isNotBlank()) {
        Column(Modifier.padding(top = 8.dp)) {
            Text(
                "工具返回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            CodeView(step.result, "json", 200.dp)
        }
    }
}

@Composable
private fun ShotImage(preview: String) {
    val path = remember(preview) {
        Regex("""path=([^\s]+)""").find(preview)?.groupValues?.get(1)
            ?: Regex("""(/sdcard/[^\s"]+\.png)""").find(preview)?.value
    }
    if (!path.isNullOrBlank() && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = "截图",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF101010))
        )
        Text(
            path,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    } else {
        CodeView(preview.ifBlank { "（截图文件不存在）" }, "text", 120.dp)
    }
}

@Composable
private fun CopyablePath(path: String) {
    if (path.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                path,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(path))
                    Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun CodeView(text: String, lang: String, maxHeight: Dp = 220.dp) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.codeWell)
            .border(0.5.dp, colors.codeWellBorder, RoundedCornerShape(AxRadius.md))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.codeWellHeader)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lang.ifBlank { "text" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
                modifier = Modifier.weight(1f)
            )
            Text(
                "复制",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
            )
        }
        Box(
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text.ifBlank { "(空)" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.text
            )
        }
    }
}

@Composable
private fun DiffView(oldText: String, newText: String) {
    if (oldText.isBlank() && newText.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
            Text("修改内容", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val red = Color(0xFFFF7B72)
            val green = Color(0xFF7EE787)
            oldText.lines().take(8).forEach {
                Text("- $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = red)
            }
            newText.lines().take(8).forEach {
                Text("+ $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = green)
            }
        }
    }
}

@Composable
private fun BatchList(ops: JSONArray?, preview: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            if (preview.isNotBlank()) {
                Text(preview, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ops == null) return@Column
            val total = ops.length()
            val shown = minOf(total, 15)
            for (i in 0 until shown) {
                val o = ops.optJSONObject(i) ?: continue
                val op = o.optString("op").lowercase()
                val kind = toolKind("file_" + if (op == "delete") "remove" else op)
                val accent = toolAccent(kind)
                val target = if (op == "move" || op == "copy") {
                    fileName(o.optString("from")) + " → " + fileName(o.optString("to"))
                } else {
                    fileName(o.optString("path"))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(toolIcon(kind), null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        opName(op),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.width(42.dp)
                    )
                    Text(
                        target,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (op == "write" || op == "append") {
                        val c = o.optString("content").replace("\n", " ").take(24)
                        if (c.isNotBlank()) {
                            Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp))
                        }
                    }
                }
            }
            if (total > shown) {
                Text("还有 ${total - shown} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun opName(op: String): String = when (op) {
    "write" -> "写入"
    "append" -> "追加"
    "remove", "delete" -> "删除"
    "mkdir" -> "建目录"
    "move" -> "移动"
    "copy" -> "复制"
    else -> op
}

@Composable
private fun PlanList(steps: JSONArray?) {
    if (steps == null || steps.length() == 0) return
    Column(Modifier.padding(top = 4.dp)) {
        for (i in 0 until steps.length()) {
            Text(
                "${i + 1}. ${steps.optString(i)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TodoList(todos: JSONArray?) {
    if (todos == null || todos.length() == 0) return
    Column(Modifier.padding(top = 4.dp)) {
        for (i in 0 until todos.length()) {
            val o = todos.optJSONObject(i) ?: continue
            val status = o.optString("status", "pending")
            val mark = when (status) {
                "completed" -> "✓"
                "in_progress" -> "◐"
                else -> "○"
            }
            val color = when (status) {
                "completed" -> MaterialTheme.colorScheme.onSurfaceVariant
                "in_progress" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(Modifier.padding(top = 2.dp)) {
                Text(mark, style = MaterialTheme.typography.labelSmall, color = color)
                Spacer(Modifier.width(6.dp))
                Text(o.optString("content"), style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

@Composable
private fun ArgsTable(args: JSONObject?) {
    if (args == null || args.length() == 0) return
    val keys = args.keys()
    var count = 0
    Column(Modifier.padding(top = 4.dp)) {
        while (keys.hasNext() && count < 10) {
            val k = keys.next()
            if (k == "thought" || k == "expect") continue
            val v = args.opt(k)?.toString().orEmpty()
            if (v.isBlank()) continue
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    k,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    v,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            count++
        }
    }
}

private fun fileName(path: String): String {
    if (path.isBlank()) return ""
    return path.substringAfterLast('/')
}

private fun langOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "js" -> "javascript"
    "ts" -> "typescript"
    "py" -> "python"
    "json" -> "json"
    "md" -> "markdown"
    "sh" -> "shell"
    "kt" -> "kotlin"
    "java" -> "java"
    "html" -> "html"
    "css" -> "css"
    "txt" -> "text"
    else -> "text"
}