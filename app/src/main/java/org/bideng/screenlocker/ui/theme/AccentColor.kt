package org.bideng.screenlocker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 可选的主题强调色。
 *
 * 一个颜色同时作用于两处:
 * 1. 悬浮锁屏按钮的底色 —— [LockOverlayService][org.bideng.screenlocker.overlay.LockOverlayService]
 *    直接读取 [argbInt];
 * 2. 应用界面的 Material 3 配色 —— 由 [accentLightColorScheme] / [accentDarkColorScheme] 推导。
 *
 * [key] 是持久化用的稳定标识,一经发布不要改名(改名会让用户已保存的设置回落默认值)。
 */
enum class AccentColor(
    val key: String,
    val argb: Long,
) {
    /** 品牌紫,与旧版本的悬浮窗底色一致。 */
    PURPLE("purple", 0xFF7E57C2),
    BLUE("blue", 0xFF1E88E5),
    TEAL("teal", 0xFF00897B),
    GREEN("green", 0xFF43A047),
    ORANGE("orange", 0xFFFB8C00),
    RED("red", 0xFFE53935),
    PINK("pink", 0xFFD81B60),
    INDIGO("indigo", 0xFF3949AB),
    BROWN("brown", 0xFF6D4C41),
    ;

    /** Compose 侧使用的颜色。 */
    val color: Color get() = Color(argb)

    /** 悬浮窗 `GradientDrawable.setColor` 需要的 ARGB 整型。 */
    val argbInt: Int get() = argb.toInt()

    companion object {
        /** 默认强调色。 */
        val DEFAULT: AccentColor = PURPLE

        /** 已保存的标识 -> 枚举;无法识别时回落到 [DEFAULT]。 */
        fun fromKey(key: String?): AccentColor =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
