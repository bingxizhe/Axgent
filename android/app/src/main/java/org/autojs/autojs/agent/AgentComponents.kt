package org.autojs.autojs.agent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 玻璃层级。chrome / sheet 有投影，card / pill 只有发丝边。 */
enum class AxGlassTier { Chrome, Sheet, Card, Pill }

@Composable
fun axGlass(tier: AxGlassTier): AxGlass = when (tier) {
    AxGlassTier.Chrome -> AxTheme.colors.chrome
    AxGlassTier.Sheet -> AxTheme.colors.sheet
    AxGlassTier.Card -> AxTheme.colors.card
    AxGlassTier.Pill -> AxTheme.colors.pill
}

/** 一块玻璃：半透明填充 + 顶边高光 + 发丝描边 + 只落在形状外的柔和阴影。 */
@Composable
fun AxGlassSurface(
    modifier: Modifier = Modifier,
    tier: AxGlassTier = AxGlassTier.Card,
    shape: Shape = RoundedCornerShape(AxRadius.md),
    shadow: Dp = if (tier == AxGlassTier.Chrome || tier == AxGlassTier.Sheet) 8.dp else 0.dp,
    content: @Composable () -> Unit
) {
    val style = axGlass(tier)
    Box(
        modifier
            .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
            .clip(shape)
            .background(style.fill)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to style.rim,
                        0.45f to Color.Transparent,
                        endY = size.height * 0.45f
                    )
                )
            }
            .border(BorderStroke(0.5.dp, style.hairline), shape)
    ) {
        content()
    }
}

@Composable
fun AxCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AxRadius.md),
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    AxGlassSurface(modifier = modifier.fillMaxWidth(), tier = AxGlassTier.Card, shape = shape) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** 顶栏 / 底栏上的圆形玻璃按钮。 */
@Composable
fun AxCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    tint: Color = AxTheme.colors.text,
    container: Color = AxTheme.colors.pill.fill,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(AxMotion.press), label = "press")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(container)
            .border(0.5.dp, AxTheme.colors.pill.hairline, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription?.let { AxLang.t(it) },
            tint = if (enabled) tint else AxTheme.colors.textGhost,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** 胶囊按钮：玻璃底或主色底。 */
@Composable
fun AxPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = AxTheme.colors
    val bg = when {
        primary -> colors.primary
        selected -> colors.primaryContainer
        else -> colors.pill.fill
    }
    val fg = when {
        primary -> colors.onPrimary
        selected -> colors.onPrimaryContainer
        else -> colors.text
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(0.5.dp, if (selected || primary) Color.Transparent else colors.pill.hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 设置分组：标题 + 一张玻璃卡，行间发丝线。 */
@Composable
fun AxSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = AxTheme.colors.textFaint,
                modifier = Modifier.padding(start = 6.dp, bottom = 7.dp)
            )
        }
        AxGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = AxGlassTier.Card,
            shape = RoundedCornerShape(AxRadius.lg)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun AxDivider(indent: Dp = 52.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = indent),
        thickness = 0.5.dp,
        color = AxTheme.colors.rule.copy(alpha = if (AxTheme.colors.dark) 0.55f else 0.7f)
    )
}

/** 设置行：色块图标 + 标题/副标题 + 值 + 箭头。 */
@Composable
fun AxSettingsTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    tintIndex: Int = 0,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = AxTheme.colors
    val tint = colors.onToolContainers[tintIndex % colors.onToolContainers.size]
    val container = colors.toolContainers[tintIndex % colors.toolContainers.size]
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(AxRadius.sm))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) trailing()
        else if (showChevron) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textGhost,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AxSwitchTile(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    tintIndex: Int = 0,
    enabled: Boolean = true
) {
    val colors = AxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(AxRadius.sm))
                    .background(colors.toolContainers[tintIndex % colors.toolContainers.size]),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = colors.onToolContainers[tintIndex % colors.onToolContainers.size],
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceHigh,
                uncheckedBorderColor = colors.rule
            )
        )
    }
}

/** 分段胶囊：DSH 的 PillSegment。 */
@Composable
fun AxSegmentRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            AxPill(label, { onSelect(value) }, selected = selected == value)
        }
    }
}

/** 搜索胶囊。 */
@Composable
fun AxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    val colors = AxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.pill.fill)
            .border(0.5.dp, colors.pill.hairline, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 页面头部：返回箭头 + 大标题 + 说明。 */
@Composable
fun AxPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AxTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = AxSpace.gutter, vertical = 8.dp)) {
        if (onBack != null || onMenu != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack ?: onMenu ?: {}),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (onBack != null) Icons.Filled.KeyboardArrowLeft else Icons.Filled.Menu,
                    contentDescription = if (onBack != null) "返回" else "菜单",
                    tint = colors.text,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.text)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 主按钮。 */
@Composable
fun AxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AxTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AxRadius.md))
            .background(if (enabled) colors.primary else colors.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onPrimary else colors.textGhost
        )
    }
}

/** 次要按钮。 */
@Composable
fun AxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val colors = AxTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(AxRadius.md))
            .background(colors.pill.fill)
            .border(0.5.dp, colors.pill.hairline, RoundedCornerShape(AxRadius.md))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> colors.textGhost
                danger -> colors.danger
                else -> colors.text
            }
        )
    }
}

/** 输入框：玻璃底、无 M3 下划线。 */
@Composable
fun AxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    val colors = AxTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AxRadius.md))
                .background(colors.surfaceLow.copy(alpha = if (colors.dark) 0.55f else 0.75f))
                .border(0.5.dp, colors.rule, RoundedCornerShape(AxRadius.md))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.textGhost)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = if (enabled) colors.text else colors.textGhost
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 玻璃对话框。 */
@Composable
fun AxDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmText: String = "确定",
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "取消",
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = AxTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AxRadius.xl))
                    .background(colors.sheet.fill)
                    .border(0.5.dp, colors.sheet.hairline, RoundedCornerShape(AxRadius.xl))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Spacer(Modifier.height(12.dp))
                    content()
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AxSecondaryButton(dismissText, onDismiss)
                        if (onConfirm != null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(AxRadius.md))
                                    .background(
                                        when {
                                            !confirmEnabled -> colors.surfaceHigh
                                            danger -> colors.danger
                                            else -> colors.primary
                                        }
                                    )
                                    .clickable(enabled = confirmEnabled, onClick = onConfirm)
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    confirmText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        !confirmEnabled -> colors.textGhost
                                        danger -> colors.onDanger
                                        else -> colors.onPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AxEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val colors = AxTheme.colors
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = colors.text)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/** 等宽：参数、路径、命令、代码。 */
fun axMono(size: Int = 12, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Normal): TextStyle {
    return TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        color = color,
        fontWeight = weight,
        lineHeight = (size * 1.5).sp
    )
}

@Composable
fun AxMonoText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = AxTheme.colors.textMuted,
    weight: FontWeight = FontWeight.Normal
) {
    Text(
        text,
        style = axMono(color = color, weight = weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** 小圆点状态。 */
@Composable
fun AxStatusDot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
