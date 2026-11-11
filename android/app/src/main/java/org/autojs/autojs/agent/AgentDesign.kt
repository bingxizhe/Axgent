package org.autojs.autojs.agent

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Axgent 视觉体系：DSH Go「雾蓝玻璃 / Mist Glass」在 Compose 中的等价实现。
 *
 * 规则来自 dsh-Go/app/lib/ui/theme：
 * - 70% 中性面（渐变 + 玻璃）、25% 文字与细线、≤5% 强调色；
 * - 浅色晨雾蓝纸面（#EBF1FE → #F6F7FC），深色深海石板（#1D222C → #1B1C23）；
 * - 强调色只出现在主按钮、发送键、选中态、链接、进度；
 * - 圆角五档 6/10/14/20/28 + 胶囊，连续圆角；
 * - 间距 2/4/8/12/16/20/24/32，页面留白 16，阅读列 720。
 */
object AxRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
}

object AxSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val gutter = 16.dp
    val inTurn = 8.dp
    val betweenTurns = 24.dp
    val readingWidth = 720.dp
    val floatInset = 12.dp
}

/** 动效 token：反馈 ≤160ms，结构 240–340ms，环境 2.4s。 */
object AxMotion {
    const val press = 90
    const val quick = 160
    const val standard = 240
    const val collapse = 280
    const val emphasized = 340
    const val sheetIn = 380
    const val sheetOut = 260
    const val slow = 480
    const val breath = 2400
}

/** DSH 六套配色。数值逐项取自 accent.dart。 */
@Immutable
data class AxAccent(
    val id: String,
    val label: String,
    val seed: Color,
    val pressed: Color,
    val container: Color,
    val onContainer: Color,
    val bubble: Color,
    val glow: Color,
    val seedDark: Color,
    val pressedDark: Color,
    val onSeedDark: Color,
    val containerDark: Color,
    val onContainerDark: Color,
    val bubbleDark: Color,
    val glowDark: Color,
    val tintHue: Double,
    val tintChroma: Double,
    val haze: Color,
    val hazeDark: Color,
) {
    val onSeed: Color get() = Color.White
    fun seedFor(dark: Boolean) = if (dark) seedDark else seed
    fun pressedFor(dark: Boolean) = if (dark) pressedDark else pressed
    fun containerFor(dark: Boolean) = if (dark) containerDark else container
    fun onContainerFor(dark: Boolean) = if (dark) onContainerDark else onContainer
    fun bubbleFor(dark: Boolean) = if (dark) bubbleDark else bubble
    fun glowFor(dark: Boolean) = if (dark) glowDark else glow
    fun hazeFor(dark: Boolean) = if (dark) hazeDark else haze

    companion object {
        val mist = AxAccent(
            id = "mist", label = "雾蓝",
            seed = Color(0xFF4B67B4), pressed = Color(0xFF3D569D),
            container = Color(0xFFDEE8FF), onContainer = Color(0xFF213262),
            bubble = Color(0xFFD9E4FD), glow = Color(0xFFABC3FC),
            seedDark = Color(0xFF97B1F0), pressedDark = Color(0xFF859EDC),
            onSeedDark = Color(0xFF121C34), containerDark = Color(0xFF2E3C5F),
            onContainerDark = Color(0xFFD8E1F6), bubbleDark = Color(0xFF293144),
            glowDark = Color(0xFF51679D),
            tintHue = 266.0, tintChroma = 1.0,
            haze = Color(0xFFD5CBEB), hazeDark = Color(0xFF514765),
        )
        val daiqing = AxAccent(
            id = "daiqing", label = "黛青",
            seed = Color(0xFF236185), pressed = Color(0xFF145071),
            container = Color(0xFFDBEAF5), onContainer = Color(0xFF123950),
            bubble = Color(0xFFD6E7F3), glow = Color(0xFFA2CAE5),
            seedDark = Color(0xFF8CB9D7), pressedDark = Color(0xFF79A6C4),
            onSeedDark = Color(0xFF0D1F2B), containerDark = Color(0xFF274152),
            onContainerDark = Color(0xFFD5E3ED), bubbleDark = Color(0xFF26333C),
            glowDark = Color(0xFF466E88),
            tintHue = 238.0, tintChroma = 0.9,
            haze = Color(0xFFC7D0EC), hazeDark = Color(0xFF444C66),
        )
        val dusk = AxAccent(
            id = "dusk", label = "暮紫",
            seed = Color(0xFF726291), pressed = Color(0xFF61517D),
            container = Color(0xFFEAE5F4), onContainer = Color(0xFF392F4C),
            bubble = Color(0xFFE6E1F1), glow = Color(0xFFC8BDE1),
            seedDark = Color(0xFFB8ABD2), pressedDark = Color(0xFFA598BF),
            onSeedDark = Color(0xFF1F1A29), containerDark = Color(0xFF40394F),
            onContainerDark = Color(0xFFE2DEEC), bubbleDark = Color(0xFF332F3B),
            glowDark = Color(0xFF6D6284),
            tintHue = 295.0, tintChroma = 0.85,
            haze = Color(0xFFE6C7D7), hazeDark = Color(0xFF5F4353),
        )
        val turquoise = AxAccent(
            id = "turquoise", label = "松石",
            seed = Color(0xFF34766F), pressed = Color(0xFF296760),
            container = Color(0xFFDBECE9), onContainer = Color(0xFF123D39),
            bubble = Color(0xFFD6E8E6), glow = Color(0xFFA3CEC8),
            seedDark = Color(0xFF8DBEB7), pressedDark = Color(0xFF7BABA4),
            onSeedDark = Color(0xFF0D211F), containerDark = Color(0xFF274340),
            onContainerDark = Color(0xFFD6E5E2), bubbleDark = Color(0xFF263533),
            glowDark = Color(0xFF47726D),
            tintHue = 190.0, tintChroma = 0.7,
            haze = Color(0xFFB9D7E3), hazeDark = Color(0xFF34525E),
        )
        val amber = AxAccent(
            id = "amber", label = "琥珀",
            seed = Color(0xFF8D6636), pressed = Color(0xFF7A5528),
            container = Color(0xFFF2E6D9), onContainer = Color(0xFF483011),
            bubble = Color(0xFFEEE2D3), glow = Color(0xFFDCBE9D),
            seedDark = Color(0xFFCCAC87), pressedDark = Color(0xFFB99975),
            onSeedDark = Color(0xFF271A0C), containerDark = Color(0xFF4C3A25),
            onContainerDark = Color(0xFFEADFD3), bubbleDark = Color(0xFF392F24),
            glowDark = Color(0xFF7F6343),
            tintHue = 75.0, tintChroma = 0.75,
            haze = Color(0xFFEAC8C1), hazeDark = Color(0xFF64443D),
        )
        val graphite = AxAccent(
            id = "graphite", label = "石墨",
            seed = Color(0xFF2C2E32), pressed = Color(0xFF1D1F23),
            container = Color(0xFFE3E6EC), onContainer = Color(0xFF2C2E32),
            bubble = Color(0xFFE0E3E8), glow = Color(0xFFBBBEC3),
            seedDark = Color(0xFFE5E8ED), pressedDark = Color(0xFFCED1D6),
            onSeedDark = Color(0xFF191B1E), containerDark = Color(0xFF3B3D41),
            onContainerDark = Color(0xFFDEE1E7), bubbleDark = Color(0xFF2E3034),
            glowDark = Color(0xFF616368),
            tintHue = 262.0, tintChroma = 0.25,
            haze = Color(0xFFCED1D6), hazeDark = Color(0xFF4A4D52),
        )

        val presets = listOf(mist, daiqing, dusk, turquoise, amber, graphite)

        fun preset(id: String?): AxAccent = presets.firstOrNull { it.id == id } ?: mist

        /** 任意取色推出一套配色：明度限 0.30–0.60，彩度封顶，深色提亮 0.234。 */
        fun derive(pick: Color, id: String = "custom", label: String = "自定义"): AxAccent {
            val o = Oklch.fromColor(pick)
            val c = min(o.c, 0.13)
            val h = o.h
            val k = min(1.0, c / 0.1255)
            val tintChroma = 0.8 * k
            fun tone(l: Double, ratio: Double) = Oklch(l, c * ratio, h).toColor()
            val lightL = o.l.coerceIn(0.30, 0.60)
            val neutralsHigh = AxNeutrals.forAccent(h, tintChroma).mistHighest
            val seed = guard(Oklch(lightL, c, h), dark = false, surface = neutralsHigh, onAccent = Color.White)
            val onSeedDark = tone(0.231, 0.39)
            val seedDark = guard(
                Oklch(min(0.93, lightL + 0.234), c * 0.76, h),
                dark = true,
                surface = AxNeutrals.forAccent(h, tintChroma).nightHighest,
                onAccent = onSeedDark,
            )
            val sl = Oklch.fromColor(seed).l
            val sdl = Oklch.fromColor(seedDark).l
            return AxAccent(
                id = id, label = label,
                seed = seed,
                pressed = tone(sl - 0.06, 0.95),
                container = tone(0.93, 0.26),
                onContainer = tone(0.33, 0.70),
                bubble = tone(0.918, 0.29),
                glow = tone(0.819, 0.68),
                seedDark = seedDark,
                pressedDark = tone(sdl - 0.06, 0.76),
                onSeedDark = onSeedDark,
                containerDark = tone(0.361, 0.51),
                onContainerDark = tone(0.909, 0.24),
                bubbleDark = tone(0.314, 0.29),
                glowDark = tone(0.52, 0.72),
                tintHue = h, tintChroma = tintChroma,
                haze = Oklch(0.86, 0.045 * k, h + 33).toColor(),
                hazeDark = Oklch(0.42, 0.05 * k, h + 33).toColor(),
            )
        }

        private fun guard(start: Oklch, dark: Boolean, surface: Color, onAccent: Color): Color {
            fun ok(c: Color) = contrastRatio(c, surface) >= 3.0 && contrastRatio(onAccent, c) >= 4.5
            val first = start.toColor()
            if (ok(first)) return first
            var pass = if (dark) 1.0 else 0.0
            var fail = start.l
            repeat(24) {
                val mid = (pass + fail) / 2
                if (ok(start.copy(l = mid).toColor())) pass = mid else fail = mid
            }
            return start.copy(l = pass).toColor()
        }
    }
}

/** OKLCH → sRGB。明度不变、只降彩度处理超出色域。 */
@Immutable
data class Oklch(val l: Double, val c: Double, val h: Double) {

    fun toColor(): Color {
        val ll = l.coerceIn(0.0, 1.0)
        var cc = max(0.0, c)
        var rgb = linear(ll, cc, h)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = cc
            repeat(24) {
                val mid = (lo + hi) / 2
                if (inGamut(linear(ll, mid, h))) lo = mid else hi = mid
            }
            cc = lo
            rgb = linear(ll, cc, h)
        }
        fun channel(v: Double) = (gamma(v.coerceIn(0.0, 1.0)) * 255).toInt().coerceIn(0, 255)
        return Color(
            red = channel(rgb.first),
            green = channel(rgb.second),
            blue = channel(rgb.third),
            alpha = 255
        )
    }

    companion object {
        fun fromColor(color: Color): Oklch {
            val r = toLinear(color.red.toDouble())
            val g = toLinear(color.green.toDouble())
            val b = toLinear(color.blue.toDouble())
            val lp = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val mp = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val sp = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val l = 0.2104542553 * lp + 0.7936177850 * mp - 0.0040720468 * sp
            val a = 1.9779984951 * lp - 2.4285922050 * mp + 0.4505937099 * sp
            val bb = 0.0259040371 * lp + 0.7827717662 * mp - 0.8086757660 * sp
            val c = sqrt(a * a + bb * bb)
            val h = (atan2(bb, a) * 180 / Math.PI + 360) % 360
            return Oklch(l, c, h)
        }

        private fun linear(l: Double, c: Double, h: Double): Triple<Double, Double, Double> {
            val rad = h * Math.PI / 180
            val a = c * cos(rad)
            val b = c * sin(rad)
            val lp = l + 0.3963377774 * a + 0.2158037573 * b
            val mp = l - 0.1055613458 * a - 0.0638541728 * b
            val sp = l - 0.0894841775 * a - 1.2914855480 * b
            val l3 = lp * lp * lp
            val m3 = mp * mp * mp
            val s3 = sp * sp * sp
            return Triple(
                4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
                -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
                -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
            )
        }

        private fun inGamut(rgb: Triple<Double, Double, Double>): Boolean {
            fun ok(v: Double) = v >= -1e-4 && v <= 1 + 1e-4
            return ok(rgb.first) && ok(rgb.second) && ok(rgb.third)
        }

        private fun cbrt(v: Double): Double = if (v < 0) -(-v).pow(1.0 / 3.0) else v.pow(1.0 / 3.0)
        private fun toLinear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        private fun gamma(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    }
}

/** 把颜色换到另一种色调：明度不变、色相平移、彩度缩放。 */
fun retintColor(color: Color, hueShift: Double, chromaScale: Double): Color {
    if (hueShift == 0.0 && chromaScale == 1.0) return color
    val o = Oklch.fromColor(color)
    return o.copy(c = o.c * chromaScale, h = (o.h + hueShift + 360) % 360).toColor().copy(alpha = color.alpha)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

private fun Color.relativeLuminance(): Double {
    fun lin(v: Float): Double =
        if (v <= 0.04045f) v.toDouble() / 12.92 else ((v + 0.055f) / 1.055f).toDouble().pow(2.4)
    return 0.2126 * lin(red) + 0.7152 * lin(green) + 0.0722 * lin(blue)
}

/** 一套配色的中性色阶，明度取自 palette.dart，色调随 accent 转。 */
@Immutable
data class AxNeutrals(
    val mistTop: Color, val mistBottom: Color, val mist: Color,
    val mistLowest: Color, val mistLow: Color, val mistContainer: Color,
    val mistHigh: Color, val mistHighest: Color,
    val ink: Color, val inkMuted: Color, val inkFaint: Color, val inkGhost: Color,
    val outlineLight: Color, val ruleLight: Color, val shadowInk: Color,
    val slate: Color, val slateContainer: Color, val inverseSurface: Color, val onInverseSurface: Color,
    val scrim: Color,
    val codeWell: Color, val codeWellHeader: Color, val codeWellBorder: Color, val lineNumber: Color, val comment: Color,
    val nightTop: Color, val nightBottom: Color, val night: Color,
    val nightLowest: Color, val nightLow: Color, val nightContainer: Color,
    val nightHigh: Color, val nightHighest: Color,
    val paper: Color, val paperMuted: Color, val paperFaint: Color, val paperGhost: Color,
    val outlineDark: Color, val ruleDark: Color, val shadowNight: Color,
    val slateLift: Color, val onSlateLift: Color,
    val smoke: Color, val sheetDark: Color,
    val codeWellDark: Color, val codeWellHeaderDark: Color, val codeWellBorderDark: Color,
    val lineNumberDark: Color, val commentDark: Color,
) {
    companion object {
        private const val BASE_HUE = 266.0
        private val defaults = AxNeutrals(
            mistTop = Color(0xFFEBF1FE), mistBottom = Color(0xFFF6F7FC), mist = Color(0xFFF0F4FD),
            mistLowest = Color(0xFFFBFCFF), mistLow = Color(0xFFF6F9FE), mistContainer = Color(0xFFEEF1F9),
            mistHigh = Color(0xFFE7EBF4), mistHighest = Color(0xFFDFE4EE),
            ink = Color(0xFF1E232E), inkMuted = Color(0xFF4F5562), inkFaint = Color(0xFF646974),
            inkGhost = Color(0xFFA1A5AC), outlineLight = Color(0xFF7E838D), ruleLight = Color(0xFFDCE0E8),
            shadowInk = Color(0xFF0B152D),
            slate = Color(0xFF545E6F), slateContainer = Color(0xFFE2E6F0),
            inverseSurface = Color(0xFF252A35), onInverseSurface = Color(0xFFECEEF4),
            scrim = Color(0xFF0B111F),
            codeWell = Color(0xFFEDF0F6), codeWellHeader = Color(0xFFE7EBF3), codeWellBorder = Color(0xFFDBDEE5),
            lineNumber = Color(0xFF7D8088), comment = Color(0xFF636975),
            nightTop = Color(0xFF1D222C), nightBottom = Color(0xFF1B1C23), night = Color(0xFF1C1F28),
            nightLowest = Color(0xFF14171E), nightLow = Color(0xFF232730), nightContainer = Color(0xFF272C36),
            nightHigh = Color(0xFF2D323C), nightHighest = Color(0xFF343944),
            paper = Color(0xFFE6E9F0), paperMuted = Color(0xFFB6BBC5), paperFaint = Color(0xFF9A9EA9),
            paperGhost = Color(0xFF656971), outlineDark = Color(0xFF737781), ruleDark = Color(0xFF343841),
            shadowNight = Color(0xFF03060D),
            slateLift = Color(0xFFB3BED2), onSlateLift = Color(0xFF1A2230),
            smoke = Color(0xFF292E39), sheetDark = Color(0xFF232832),
            codeWellDark = Color(0xFF14171E), codeWellHeaderDark = Color(0xFF1A1E26),
            codeWellBorderDark = Color(0xFF2F333A), lineNumberDark = Color(0xFF71747D),
            commentDark = Color(0xFF868C97),
        )

        private val cache = HashMap<String, AxNeutrals>()

        fun forAccent(hue: Double, chroma: Double): AxNeutrals {
            if (hue == BASE_HUE && chroma == 1.0) return defaults
            val key = "$hue/$chroma"
            return cache.getOrPut(key) {
                val shift = hue - BASE_HUE
                fun t(c: Color) = retintColor(c, shift, chroma)
                val d = defaults
                AxNeutrals(
                    mistTop = t(d.mistTop), mistBottom = t(d.mistBottom), mist = t(d.mist),
                    mistLowest = t(d.mistLowest), mistLow = t(d.mistLow), mistContainer = t(d.mistContainer),
                    mistHigh = t(d.mistHigh), mistHighest = t(d.mistHighest),
                    ink = t(d.ink), inkMuted = t(d.inkMuted), inkFaint = t(d.inkFaint), inkGhost = t(d.inkGhost),
                    outlineLight = t(d.outlineLight), ruleLight = t(d.ruleLight), shadowInk = t(d.shadowInk),
                    slate = t(d.slate), slateContainer = t(d.slateContainer),
                    inverseSurface = t(d.inverseSurface), onInverseSurface = t(d.onInverseSurface),
                    scrim = t(d.scrim),
                    codeWell = t(d.codeWell), codeWellHeader = t(d.codeWellHeader), codeWellBorder = t(d.codeWellBorder),
                    lineNumber = t(d.lineNumber), comment = t(d.comment),
                    nightTop = t(d.nightTop), nightBottom = t(d.nightBottom), night = t(d.night),
                    nightLowest = t(d.nightLowest), nightLow = t(d.nightLow), nightContainer = t(d.nightContainer),
                    nightHigh = t(d.nightHigh), nightHighest = t(d.nightHighest),
                    paper = t(d.paper), paperMuted = t(d.paperMuted), paperFaint = t(d.paperFaint),
                    paperGhost = t(d.paperGhost), outlineDark = t(d.outlineDark), ruleDark = t(d.ruleDark),
                    shadowNight = t(d.shadowNight), slateLift = t(d.slateLift), onSlateLift = t(d.onSlateLift),
                    smoke = t(d.smoke), sheetDark = t(d.sheetDark),
                    codeWellDark = t(d.codeWellDark), codeWellHeaderDark = t(d.codeWellHeaderDark),
                    codeWellBorderDark = t(d.codeWellBorderDark), lineNumberDark = t(d.lineNumberDark),
                    commentDark = t(d.commentDark),
                )
            }
        }
    }
}

/** 玻璃材质参数：填充、发丝边、顶边高光、外部阴影。 */
@Immutable
data class AxGlass(
    val fill: Color,
    val hairline: Color,
    val rim: Color,
    val shadow: Color,
)

/** 主题解析后的全部颜色角色。界面代码只从这里取色。 */
@Immutable
data class AxColors(
    val accent: AxAccent,
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val pressed: Color,
    val backdropTop: Color,
    val backdropBottom: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val textGhost: Color,
    val outline: Color,
    val rule: Color,
    val shadow: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val card: AxGlass,
    val chrome: AxGlass,
    val sheet: AxGlass,
    val pill: AxGlass,
    val scrim: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val codeWell: Color,
    val codeWellHeader: Color,
    val codeWellBorder: Color,
    val lineNumber: Color,
    val toolContainers: List<Color>,
    val onToolContainers: List<Color>,
) {
    fun glassCard() = card
    fun glassChrome() = chrome
    fun glassSheet() = sheet
    fun glassPill() = pill
}

private fun AxColors(accent: AxAccent, dark: Boolean): AxColors {
    val n = AxNeutrals.forAccent(accent.tintHue, accent.tintChroma)
    val ink = n.shadowInk
    val white = Color.White
    val black = Color.Black
    return if (!dark) {
        AxColors(
            accent = accent, dark = false,
            primary = accent.seed, onPrimary = accent.onSeed,
            primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
            pressed = accent.pressed,
            backdropTop = n.mistTop, backdropBottom = n.mistBottom,
            glowPrimary = accent.glow.copy(alpha = 0.42f),
            glowSecondary = accent.haze.copy(alpha = 0.38f),
            userBubble = accent.bubble, onUserBubble = n.ink,
            text = n.ink, textMuted = n.inkMuted, textFaint = n.inkFaint, textGhost = n.inkGhost,
            outline = n.outlineLight, rule = n.ruleLight, shadow = n.shadowInk,
            surfaceLowest = n.mistLowest, surfaceLow = n.mistLow, surface = n.mist,
            surfaceHigh = n.mistHigh, surfaceHighest = n.mistHighest,
            card = AxGlass(white.copy(alpha = 0.62f), ink.copy(alpha = 0.06f), white.copy(alpha = 0.70f), ink.copy(alpha = 0.05f)),
            chrome = AxGlass(white.copy(alpha = 0.80f), ink.copy(alpha = 0.08f), white.copy(alpha = 0.85f), ink.copy(alpha = 0.10f)),
            sheet = AxGlass(n.mistLow.copy(alpha = 0.94f), ink.copy(alpha = 0.07f), white.copy(alpha = 0.90f), ink.copy(alpha = 0.12f)),
            pill = AxGlass(white.copy(alpha = 0.72f), ink.copy(alpha = 0.07f), white.copy(alpha = 0.75f), ink.copy(alpha = 0.04f)),
            scrim = n.scrim.copy(alpha = 0.32f),
            success = Color(0xFF337250), onSuccess = Color(0xFFF7FBF9),
            successContainer = Color(0xFFD9F0E1), onSuccessContainer = Color(0xFF153F29),
            warning = Color(0xFF936123), onWarning = Color(0xFFFCFAF6),
            warningContainer = Color(0xFFF9E9CE), onWarningContainer = Color(0xFF55350D),
            danger = Color(0xFFA54743), onDanger = Color(0xFFFDF9F8),
            dangerContainer = Color(0xFFFFE2E0), onDangerContainer = Color(0xFF642724),
            info = Color(0xFF2971A7), infoContainer = Color(0xFFDBECFC), onInfoContainer = Color(0xFF123B5A),
            tertiary = Color(0xFF6B578F), tertiaryContainer = Color(0xFFECE5FB),
            codeWell = n.codeWell, codeWellHeader = n.codeWellHeader, codeWellBorder = n.codeWellBorder,
            lineNumber = n.lineNumber,
            toolContainers = listOf(
                Color(0xFFE6EAF2), Color(0xFFDAECFF), Color(0xFFFAE6CD), Color(0xFFEDE5FE),
                Color(0xFFD7F1E2), Color(0xFFF9E2F0), Color(0xFFD0F1F0), Color(0xFFE7E9EF),
            ),
            onToolContainers = listOf(
                Color(0xFF343D51), Color(0xFF173F65), Color(0xFF543600), Color(0xFF443261),
                Color(0xFF034A2E), Color(0xFF582B4A), Color(0xFF004747), Color(0xFF363D4C),
            ),
        )
    } else {
        AxColors(
            accent = accent, dark = true,
            primary = accent.seedDark, onPrimary = accent.onSeedDark,
            primaryContainer = accent.containerDark, onPrimaryContainer = accent.onContainerDark,
            pressed = accent.pressedDark,
            backdropTop = n.nightTop, backdropBottom = n.nightBottom,
            glowPrimary = accent.glowDark.copy(alpha = 0.28f),
            glowSecondary = accent.hazeDark.copy(alpha = 0.24f),
            userBubble = accent.bubbleDark, onUserBubble = n.paper,
            text = n.paper, textMuted = n.paperMuted, textFaint = n.paperFaint, textGhost = n.paperGhost,
            outline = n.outlineDark, rule = n.ruleDark, shadow = n.shadowNight,
            surfaceLowest = n.nightLowest, surfaceLow = n.nightLow, surface = n.night,
            surfaceHigh = n.nightHigh, surfaceHighest = n.nightHighest,
            card = AxGlass(white.copy(alpha = 0.05f), white.copy(alpha = 0.07f), white.copy(alpha = 0.06f), black.copy(alpha = 0.20f)),
            chrome = AxGlass(n.smoke.copy(alpha = 0.86f), white.copy(alpha = 0.09f), white.copy(alpha = 0.12f), black.copy(alpha = 0.35f)),
            sheet = AxGlass(n.sheetDark.copy(alpha = 0.96f), white.copy(alpha = 0.08f), white.copy(alpha = 0.10f), black.copy(alpha = 0.40f)),
            pill = AxGlass(white.copy(alpha = 0.06f), white.copy(alpha = 0.08f), white.copy(alpha = 0.08f), black.copy(alpha = 0.15f)),
            scrim = black.copy(alpha = 0.48f),
            success = Color(0xFF8BCCA6), onSuccess = Color(0xFF0C2518),
            successContainer = Color(0xFF233F2F), onSuccessContainer = Color(0xFFC9E7D4),
            warning = Color(0xFFE6BD7C), onWarning = Color(0xFF2F1E06),
            warningContainer = Color(0xFF4C371D), onWarningContainer = Color(0xFFF4E2C3),
            danger = Color(0xFFE69089), onDanger = Color(0xFF331513),
            dangerContainer = Color(0xFF542C29), onDangerContainer = Color(0xFFF8D9D7),
            info = Color(0xFF8CBDE8), infoContainer = Color(0xFF253C4F), onInfoContainer = Color(0xFFD1E4F4),
            tertiary = Color(0xFFBEAEDE), tertiaryContainer = Color(0xFF3E3451),
            codeWell = n.codeWellDark, codeWellHeader = n.codeWellHeaderDark, codeWellBorder = n.codeWellBorderDark,
            lineNumber = n.lineNumberDark,
            toolContainers = listOf(
                Color(0xFF303540), Color(0xFF21374E), Color(0xFF443116), Color(0xFF39304B),
                Color(0xFF1D3D2D), Color(0xFF452C3D), Color(0xFF0F3D3D), Color(0xFF32353D),
            ),
            onToolContainers = listOf(
                Color(0xFFD1D8E5), Color(0xFFC0DBF8), Color(0xFFEBD3B4), Color(0xFFDCD1F3),
                Color(0xFFBDE2CD), Color(0xFFEDCCE1), Color(0xFFB3E2E1), Color(0xFFD3D7E1),
            ),
        )
    }
}

val LocalAxColors = staticCompositionLocalOf { AxColors(AxAccent.mist, false) }
val LocalChatTextScale = staticCompositionLocalOf { 1f }

// 旧工具卡仍在引用的固定语义色；新界面优先使用 AxTheme.colors 里的同角色。
val AgentGreen = Color(0xFF337250)
val AgentRed = Color(0xFFA54743)
val AgentAmber = Color(0xFF936123)
val AgentOrange = Color(0xFF8D6636)
val AgentBlue = Color(0xFF2971A7)

object AxTheme {
    val colors: AxColors
        @Composable get() = LocalAxColors.current
}

private fun axTypography(): Typography {
    val even = androidx.compose.ui.text.style.TextAlign.Start
    fun style(size: Int, weight: FontWeight, line: Float) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        textAlign = even,
    )
    return Typography(
        displayLarge = style(36, FontWeight.Light, 43f),
        displayMedium = style(32, FontWeight.Light, 38f),
        displaySmall = style(28, FontWeight.Light, 35f),
        headlineLarge = style(26, FontWeight.SemiBold, 34f),
        headlineMedium = style(22, FontWeight.SemiBold, 29f),
        headlineSmall = style(20, FontWeight.SemiBold, 27f),
        titleLarge = style(18, FontWeight.SemiBold, 25f),
        titleMedium = style(16, FontWeight.SemiBold, 23f),
        titleSmall = style(14, FontWeight.SemiBold, 20f),
        bodyLarge = style(16, FontWeight.Normal, 26f),
        bodyMedium = style(14, FontWeight.Normal, 22f),
        bodySmall = style(12, FontWeight.Normal, 18f),
        labelLarge = style(14, FontWeight.SemiBold, 20f),
        labelMedium = style(12, FontWeight.SemiBold, 16f),
        labelSmall = style(11, FontWeight.Normal, 15f),
    )
}

private fun axShapes(): Shapes = Shapes(
    // M3 的 TextField / Menu / Chip 默认用 extraSmall；DSH 的输入框是 14dp 档。
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(AxRadius.sm),
    small = androidx.compose.foundation.shape.RoundedCornerShape(AxRadius.md),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(AxRadius.md),
    large = androidx.compose.foundation.shape.RoundedCornerShape(AxRadius.lg),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(AxRadius.xl),
)

private val AxTypography = axTypography()
private val AxShapes = axShapes()

/** 全应用主题入口：解析主题模式、配色、壁纸取色，并提供 MaterialTheme。 */
@Composable
fun AxgentTheme(
    themeMode: String,
    accentId: String,
    customHex: String,
    wallpaperAccent: Boolean,
    content: @Composable () -> Unit
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val context = LocalContext.current
    val wallpaperSeed by produceState(initialValue = AxAccent.mist.seed, wallpaperAccent, accentId) {
        if (wallpaperAccent) {
            value = withContext(Dispatchers.IO) { sampleWallpaperSeed(context) }
        }
    }
    val accent = remember(accentId, customHex, wallpaperAccent, wallpaperSeed) {
        when {
            wallpaperAccent -> AxAccent.derive(wallpaperSeed, id = "wallpaper", label = "跟随壁纸")
            accentId == "custom" -> AxAccent.derive(parseHexColor(customHex), id = "custom", label = "自定义")
            else -> AxAccent.preset(accentId)
        }
    }
    val colors = remember(accent, dark) { AxColors(accent, dark) }
    val colorScheme = remember(colors) {
        if (dark) {
            darkColorScheme(
                primary = colors.primary, onPrimary = colors.onPrimary,
                primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onPrimaryContainer,
                secondary = colors.accent.bubbleDark, onSecondary = colors.text,
                secondaryContainer = colors.surfaceHigh, onSecondaryContainer = colors.text,
                tertiary = colors.tertiary, onTertiary = colors.text,
                tertiaryContainer = colors.tertiaryContainer, onTertiaryContainer = colors.text,
                error = colors.danger, onError = colors.onDanger,
                errorContainer = colors.dangerContainer, onErrorContainer = colors.onDangerContainer,
                background = colors.surface, onBackground = colors.text,
                surface = colors.surface, onSurface = colors.text,
                surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textMuted,
                surfaceDim = colors.surfaceLowest, surfaceBright = colors.surfaceHighest,
                surfaceContainerLowest = colors.surfaceLowest,
                surfaceContainerLow = colors.surfaceLow,
                surfaceContainer = colors.surface,
                surfaceContainerHigh = colors.surfaceHigh,
                surfaceContainerHighest = colors.surfaceHighest,
                surfaceTint = Color.Transparent,
                outline = colors.outline, outlineVariant = colors.rule,
                scrim = colors.scrim, inverseSurface = colors.text, inverseOnSurface = colors.surface,
            )
        } else {
            lightColorScheme(
                primary = colors.primary, onPrimary = colors.onPrimary,
                primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onPrimaryContainer,
                secondary = colors.accent.container, onSecondary = colors.text,
                secondaryContainer = colors.surfaceHigh, onSecondaryContainer = colors.text,
                tertiary = colors.tertiary, onTertiary = Color.White,
                tertiaryContainer = colors.tertiaryContainer, onTertiaryContainer = Color(0xFF3B2C54),
                error = colors.danger, onError = colors.onDanger,
                errorContainer = colors.dangerContainer, onErrorContainer = colors.onDangerContainer,
                background = colors.surface, onBackground = colors.text,
                surface = colors.surface, onSurface = colors.text,
                surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textMuted,
                surfaceDim = colors.surfaceHighest, surfaceBright = colors.surfaceLowest,
                surfaceContainerLowest = colors.surfaceLowest,
                surfaceContainerLow = colors.surfaceLow,
                surfaceContainer = colors.surface,
                surfaceContainerHigh = colors.surfaceHigh,
                surfaceContainerHighest = colors.surfaceHighest,
                surfaceTint = Color.Transparent,
                outline = colors.outline, outlineVariant = colors.rule,
                scrim = colors.scrim, inverseSurface = colors.text, inverseOnSurface = colors.surface,
            )
        }
    }
    val view = LocalView.current
    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalAxColors provides colors,
        LocalChatTextScale provides AgentStore.config.chatTextScale
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AxTypography, shapes = AxShapes) {
            content()
        }
    }
}

fun parseHexColor(raw: String): Color {
    val s = raw.trim().removePrefix("#")
    return try {
        when (s.length) {
            6 -> Color(0xFF000000 or s.toLong(16))
            8 -> Color(s.toLong(16))
            else -> AxAccent.mist.seed
        }
    } catch (_: Exception) {
        AxAccent.mist.seed
    }
}

/** 从壁纸取样：缩到 48×48，优先取彩度足够、亮度适中的像素均值。 */
private fun sampleWallpaperSeed(context: android.content.Context): Color {
    return try {
        val manager = WallpaperManager.getInstance(context)
        val drawable = manager.drawable ?: return AxAccent.mist.seed
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        var r = 0.0; var g = 0.0; var b = 0.0; var count = 0
        var ar = 0.0; var ag = 0.0; var ab = 0.0; var acount = 0
        val hsv = FloatArray(3)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = bitmap.getPixel(x, y)
                val a = android.graphics.Color.alpha(p)
                if (a < 200) continue
                android.graphics.Color.colorToHSV(p, hsv)
                ar += android.graphics.Color.red(p)
                ag += android.graphics.Color.green(p)
                ab += android.graphics.Color.blue(p)
                acount++
                if (hsv[1] in 0.18f..0.95f && hsv[2] in 0.2f..0.95f) {
                    r += android.graphics.Color.red(p)
                    g += android.graphics.Color.green(p)
                    b += android.graphics.Color.blue(p)
                    count++
                }
            }
        }
        bitmap.recycle()
        if (count > 24) {
            Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
        } else if (acount > 0) {
            Color((ar / acount).toInt(), (ag / acount).toInt(), (ab / acount).toInt())
        } else {
            AxAccent.mist.seed
        }
    } catch (_: Exception) {
        AxAccent.mist.seed
    }
}

/** 全屏背景：竖向渐变 + 右上主色光晕 + 左下邻近色光晕。 */
@Composable
fun AxBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = AxTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .drawBehind { paintBackdrop(colors) }
    ) {
        content()
    }
}

private fun DrawScope.paintBackdrop(colors: AxColors) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to colors.backdropTop,
            1f to colors.backdropBottom
        )
    )
    val longest = max(size.width, size.height)
    drawCircle(
        brush = Brush.radialGradient(
            0f to colors.glowPrimary,
            1f to colors.glowPrimary.copy(alpha = 0f),
            center = Offset(size.width * 0.92f, 0f),
            radius = longest * 0.62f,
        ),
        radius = longest * 0.62f,
        center = Offset(size.width * 0.92f, 0f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to colors.glowSecondary,
            1f to colors.glowSecondary.copy(alpha = 0f),
            center = Offset(0f, size.height),
            radius = longest * 0.55f,
        ),
        radius = longest * 0.55f,
        center = Offset(0f, size.height),
    )
}
