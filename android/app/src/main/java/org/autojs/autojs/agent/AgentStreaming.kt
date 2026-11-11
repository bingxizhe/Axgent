package org.autojs.autojs.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * StreamingText —— 逐字显示。
 *
 * key 不变时，text 变长会从当前进度继续；key 变化（换了一条消息）则从头开始。
 */
@Composable
fun StreamingText(
    text: String,
    key: Any? = text,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    tickMs: Long = 18L,
    animate: Boolean = true,
    onProgress: (() -> Unit)? = null
) {
    var visible by rememberSaveable(key) { mutableStateOf(if (animate) 0 else text.length) }
    val progress by rememberUpdatedState(onProgress)

    LaunchedEffect(key, text, animate) {
        if (!animate) {
            visible = text.length
            return@LaunchedEffect
        }
        if (visible > text.length) visible = 0
        // 长文本加速，避免几百字要等十几秒
        val step = if (text.length > 240) max(2, text.length / 120) else 1
        var sinceNotify = 0
        while (visible < text.length) {
            delay(tickMs)
            visible = (visible + step).coerceAtMost(text.length)
            sinceNotify++
            if (sinceNotify >= 4 || visible >= text.length) {
                sinceNotify = 0
                progress?.invoke()
            }
        }
    }

    val shown = text.take(visible)
    val typing = visible < text.length
    Text(
        text = if (typing) shown + "▍" else shown,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .background(AgentGreen.copy(alpha = alpha), CircleShape)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "思考中…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
