package org.autojs.autojs.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染：标题 / 粗体 / 斜体 / 行内代码 / 代码块 /
 * 无序列表 / 有序列表 / 引用 / 表格 / 链接。不引入第三方依赖。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val lines = remember(markdown) { markdown.replace("\r\n", "\n").split("\n") }
    Column(modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            when {
                line.isBlank() -> {
                    Spacer(Modifier.height(6.dp)); i++
                }
                line.trimStart().startsWith("```") -> {
                    val lang = line.trim().removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.append(lines[i]).append('\n'); i++
                    }
                    if (i < lines.size) i++
                    CodeBlock(code.toString().trimEnd('\n'), lang)
                }
                line.trimStart().startsWith("#") -> {
                    val t = line.trimStart()
                    var level = 0
                    while (level < t.length && t[level] == '#') level++
                    Heading(level, t.substring(level).trim())
                    i++
                }
                line.trimStart().startsWith(">") -> {
                    Quote(line.trimStart().removePrefix(">").trim())
                    i++
                }
                isBullet(line) -> {
                    Bullet(line); i++
                }
                isNumbered(line) -> {
                    Numbered(line); i++
                }
                isTableStart(lines, i) -> {
                    val rows = mutableListOf<String>()
                    while (i < lines.size && lines[i].contains("|")) {
                        rows.add(lines[i]); i++
                    }
                    Table(rows)
                }
                else -> {
                    val para = StringBuilder()
                    while (i < lines.size) {
                        val l = lines[i]
                        if (l.isBlank() || isBlockStart(lines, i)) break
                        if (para.isNotEmpty()) para.append(' ')
                        para.append(l.trim()); i++
                    }
                    if (para.isEmpty()) i++ else InlineText(para.toString())
                }
            }
        }
    }
}

/** 聊天正文：16sp / 1.65，跟随外观里的“聊天字号”倍率。 */
@Composable
private fun mdBody() = MaterialTheme.typography.bodyLarge.copy(
    fontSize = (16 * LocalChatTextScale.current).sp,
    lineHeight = (26 * LocalChatTextScale.current).sp
)

@Composable
private fun Heading(level: Int, text: String) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        mdInline(text),
        style = style.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun InlineText(text: String) {
    Text(mdInline(text), style = mdBody(), color = AxTheme.colors.text)
}

@Composable
private fun Bullet(line: String) {
    val indent = line.length - line.trimStart().length
    Row(Modifier.padding(start = (4 + indent * 8).dp, top = 2.dp)) {
        Text("•", style = mdBody(), color = AxTheme.colors.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            mdInline(line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("+ ")),
            style = mdBody(),
            color = AxTheme.colors.text
        )
    }
}

@Composable
private fun Numbered(line: String) {
    val t = line.trimStart()
    val dot = t.indexOf('.')
    val prefix = if (dot in 0..3) t.substring(0, dot + 1) else "1."
    val content = if (dot in 0..3) t.substring(dot + 1).trim() else t
    Row(Modifier.padding(start = 4.dp, top = 2.dp)) {
        Text(prefix, style = mdBody(), color = AxTheme.colors.primary)
        Spacer(Modifier.width(6.dp))
        Text(mdInline(content), style = mdBody(), color = AxTheme.colors.text)
    }
}

@Composable
private fun Quote(text: String) {
    Row(
        Modifier.padding(vertical = 2.dp)
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(AxTheme.colors.surfaceLow.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("▎", color = AxTheme.colors.primary, style = mdBody())
        Spacer(Modifier.width(6.dp))
        Text(
            mdInline(text),
            style = mdBody(),
            color = AxTheme.colors.textMuted
        )
    }
}

@Composable
private fun CodeBlock(code: String, lang: String) {
    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.codeWell)
            .border(0.5.dp, colors.codeWellBorder, RoundedCornerShape(AxRadius.md))
            .padding(10.dp)
    ) {
        if (lang.isNotBlank()) {
            Text(
                lang,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.text,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun Table(rows: List<String>) {
    val parsed = remember(rows) { rows.map { parseCells(it) }.filter { it.isNotEmpty() } }
    if (parsed.isEmpty()) return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(AxTheme.colors.surfaceLow.copy(alpha = 0.75f))
            .border(0.5.dp, AxTheme.colors.rule.copy(alpha = 0.7f), RoundedCornerShape(AxRadius.md))
            .padding(6.dp)
    ) {
        parsed.forEachIndexed { idx, cells ->
            if (isSeparator(cells)) return@forEachIndexed
            val header = idx == 0
            Row(Modifier.fillMaxWidth()) {
                cells.forEach { cell ->
                    Text(
                        mdInline(cell),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 3.dp)
                    )
                }
            }
            if (header) {
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

private fun parseCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun isSeparator(cells: List<String>): Boolean =
    cells.isNotEmpty() && cells.all { it.matches(Regex("^:?-{1,}:?$")) }

private fun isBullet(line: String): Boolean {
    val t = line.trimStart()
    return t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")
}

private fun isNumbered(line: String): Boolean = Regex("^\\s*\\d+\\.\\s+").containsMatchIn(line)

private fun isTableStart(lines: List<String>, i: Int): Boolean {
    if (!lines[i].contains("|") || i + 1 >= lines.size) return false
    val sep = lines[i + 1].trim()
    if (!sep.contains("|")) return false
    return sep.replace("|", "").replace(":", "").replace("-", "").replace(" ", "").isEmpty()
}

private fun isBlockStart(lines: List<String>, i: Int): Boolean {
    val t = lines[i].trimStart()
    return t.startsWith("```") || t.startsWith("#") || t.startsWith(">") ||
        isBullet(lines[i]) || isNumbered(lines[i]) || isTableStart(lines, i)
}

/**
 * 行内格式：**粗体**、*斜体*、`代码`、[文字](链接)。
 */
@Composable
private fun mdInline(text: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, codeBg, linkColor) {
        buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) { append(text.substring(i)); i = text.length }
                    else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { append(text.substring(i)); i = text.length }
                    else {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg
                            )
                        ) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    }
                }
                text.startsWith("[", i) -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                        val end = text.indexOf(')', close + 2)
                        if (end > 0) {
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) { append(text.substring(i + 1, close)) }
                            i = end + 1
                            continue
                        }
                    }
                    append('['); i++
                }
                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val mark = text[i]
                    val end = text.indexOf(mark, i + 1)
                    if (end < 0) { append(mark); i++ }
                    else {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
    }
}