package org.bideng.screenlocker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.pow

/**
 * 由选定的强调色推导整套 Material 3 配色。
 *
 * 这里没有引入 `material-color-utilities`(会多一个依赖),而是用「按亮度反解」的方式近似:
 * 每个色调值先换算成目标相对亮度(MD3 的 tone 与 CIE L* 一致),
 * 再据此把色板种子调暗或提亮,使成品的亮度与 MD3 色调一一对应。
 *
 * 关键点是**不能用 HSL 明度直接当色调** —— 人眼对青、绿、黄更敏感,
 * 那种做法会把 tone 40 算成刺眼的亮青(白字对比度只有 2:1),
 * 且 HSL 在满饱和度下亮度对明度并非单调,二分都收敛不了。
 *
 * 现在两个方向都是单调且精确的:
 * - 比种子暗 → 在**线性 RGB**空间等比缩放,亮度按同一比例变化;
 * - 比种子亮 → 在 **sRGB** 空间向白色插值,亮度随插值系数单调递增。
 *
 * 实测:9 种强调色下,主色与其前景对比度均为 6.4:1、容器对 13.3:1、
 * 表面文字对 16.7:1;深色主题对应为 7.7:1 / 7.2:1 / 13.3:1,色相之间高度一致。
 */

/** 次要色饱和度倍率:比主色淡,负责弱强调。 */
private const val SECONDARY_CHROMA = 0.34f

/** 第三色饱和度倍率:与主色拉开层次。 */
private const val TERTIARY_CHROMA = 0.55f

/** 第三色色相偏移(度)。 */
private const val TERTIARY_HUE_SHIFT = 60f

/** 中性色饱和度倍率:极低,只留下一点色相倾向。 */
private const val NEUTRAL_CHROMA = 0.05f

/** 中性变体饱和度倍率:略高于中性色,用于描边与次要文字。 */
private const val NEUTRAL_VARIANT_CHROMA = 0.12f

/** 求提亮系数时的二分次数,20 次已到浮点精度极限。 */
private const val BINARY_SEARCH_STEPS = 20

fun accentLightColorScheme(accent: AccentColor): ColorScheme {
    val primary = TonalPalette(accent, 1f)
    val secondary = TonalPalette(accent, SECONDARY_CHROMA)
    val tertiary = TonalPalette(accent, TERTIARY_CHROMA, TERTIARY_HUE_SHIFT)
    val neutral = TonalPalette(accent, NEUTRAL_CHROMA)
    val neutralVariant = TonalPalette(accent, NEUTRAL_VARIANT_CHROMA)

    return lightColorScheme(
        primary = primary.tone(40),
        onPrimary = primary.tone(100),
        primaryContainer = primary.tone(90),
        onPrimaryContainer = primary.tone(10),
        inversePrimary = primary.tone(80),

        secondary = secondary.tone(40),
        onSecondary = secondary.tone(100),
        secondaryContainer = secondary.tone(90),
        onSecondaryContainer = secondary.tone(10),

        tertiary = tertiary.tone(40),
        onTertiary = tertiary.tone(100),
        tertiaryContainer = tertiary.tone(90),
        onTertiaryContainer = tertiary.tone(10),

        error = Error40,
        onError = Color.White,
        errorContainer = Error90,
        onErrorContainer = Error10,

        background = neutral.tone(99),
        onBackground = neutral.tone(10),
        surface = neutral.tone(99),
        onSurface = neutral.tone(10),
        surfaceVariant = neutralVariant.tone(90),
        onSurfaceVariant = neutralVariant.tone(30),
        surfaceTint = primary.tone(40),

        inverseSurface = neutral.tone(20),
        inverseOnSurface = neutral.tone(95),
        outline = neutralVariant.tone(50),
        outlineVariant = neutralVariant.tone(80),

        surfaceDim = neutral.tone(87),
        surfaceBright = neutral.tone(98),
        surfaceContainerLowest = neutral.tone(100),
        surfaceContainerLow = neutral.tone(96),
        surfaceContainer = neutral.tone(94),
        surfaceContainerHigh = neutral.tone(92),
        surfaceContainerHighest = neutral.tone(90),
    )
}

fun accentDarkColorScheme(accent: AccentColor): ColorScheme {
    val primary = TonalPalette(accent, 1f)
    val secondary = TonalPalette(accent, SECONDARY_CHROMA)
    val tertiary = TonalPalette(accent, TERTIARY_CHROMA, TERTIARY_HUE_SHIFT)
    val neutral = TonalPalette(accent, NEUTRAL_CHROMA)
    val neutralVariant = TonalPalette(accent, NEUTRAL_VARIANT_CHROMA)

    return darkColorScheme(
        primary = primary.tone(80),
        onPrimary = primary.tone(20),
        primaryContainer = primary.tone(30),
        onPrimaryContainer = primary.tone(90),
        inversePrimary = primary.tone(40),

        secondary = secondary.tone(80),
        onSecondary = secondary.tone(20),
        secondaryContainer = secondary.tone(30),
        onSecondaryContainer = secondary.tone(90),

        tertiary = tertiary.tone(80),
        onTertiary = tertiary.tone(20),
        tertiaryContainer = tertiary.tone(30),
        onTertiaryContainer = tertiary.tone(90),

        error = Error80,
        onError = Error20,
        errorContainer = Error30,
        onErrorContainer = Error90,

        background = neutral.tone(10),
        onBackground = neutral.tone(90),
        surface = neutral.tone(10),
        onSurface = neutral.tone(90),
        surfaceVariant = neutralVariant.tone(30),
        onSurfaceVariant = neutralVariant.tone(80),
        surfaceTint = primary.tone(80),

        inverseSurface = neutral.tone(90),
        inverseOnSurface = neutral.tone(20),
        outline = neutralVariant.tone(60),
        outlineVariant = neutralVariant.tone(30),

        surfaceDim = neutral.tone(6),
        surfaceBright = neutral.tone(24),
        surfaceContainerLowest = neutral.tone(4),
        surfaceContainerLow = neutral.tone(10),
        surfaceContainer = neutral.tone(12),
        surfaceContainerHigh = neutral.tone(17),
        surfaceContainerHighest = neutral.tone(22),
    )
}

/**
 * 一条色调板:色相取自强调色(可带偏移),饱和度按角色缩放,
 * 之后任意色调都能取到亮度精确对应的颜色。
 */
private class TonalPalette(
    accent: AccentColor,
    chroma: Float,
    hueShift: Float = 0f,
) {
    /** 色板种子:只做色相偏移与饱和度缩放,明度沿用强调色自身。 */
    private val seed: Color = run {
        val hsl = accent.color.toHsl()
        Color(
            ColorUtils.HSLToColor(
                floatArrayOf(
                    (hsl[0] + hueShift + 360f) % 360f,
                    (hsl[1] * chroma).coerceIn(0f, 1f),
                    hsl[2],
                )
            )
        )
    }

    private val seedLuminance: Double = seed.luminance().toDouble()

    /** 取出 MD3 色调 [value](0 = 纯黑,100 = 纯白)对应的颜色。 */
    fun tone(value: Int): Color {
        val target = targetLuminance(value)
        return if (target <= seedLuminance) {
            shade(if (seedLuminance <= 0.0) 0.0 else target / seedLuminance)
        } else {
            tint(target)
        }
    }

    /** 等比压暗:线性 RGB 整体缩放,亮度按同一比例变化,色相与通道比例不变。 */
    private fun shade(factor: Double): Color = Color(
        delinearize(linearize(seed.red) * factor),
        delinearize(linearize(seed.green) * factor),
        delinearize(linearize(seed.blue) * factor),
    )

    /** 向白色插值提亮:亮度随系数单调递增,二分即可精确命中目标亮度。 */
    private fun tint(target: Double): Color {
        var low = 0f
        var high = 1f
        repeat(BINARY_SEARCH_STEPS) {
            val mid = (low + high) / 2f
            if (lerpToWhite(mid).luminance() < target) low = mid else high = mid
        }
        return lerpToWhite((low + high) / 2f)
    }

    private fun lerpToWhite(amount: Float): Color = Color(
        seed.red + (1f - seed.red) * amount,
        seed.green + (1f - seed.green) * amount,
        seed.blue + (1f - seed.blue) * amount,
    )
}

/** 取出 HSL 三元组(色相 0..360,饱和度/明度 0..1)。 */
private fun Color.toHsl(): FloatArray = FloatArray(3).also {
    ColorUtils.colorToHSL(toArgb(), it)
}

/** MD3 色调值 -> 目标相对亮度:色调与 CIE L* 对齐,L* 再按 CIE 公式换算成相对亮度。 */
private fun targetLuminance(tone: Int): Double {
    val t = (tone.coerceIn(0, 100) + 16.0) / 116.0
    return if (t > 6.0 / 29.0) {
        t * t * t
    } else {
        3.0 * (6.0 / 29.0) * (6.0 / 29.0) * (t - 4.0 / 29.0)
    }
}

private fun linearize(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun delinearize(value: Double): Float {
    val c = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055
    return c.coerceIn(0.0, 1.0).toFloat()
}
