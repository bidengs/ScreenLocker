package org.bideng.screenlocker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 应用主题。
 *
 * 配色由用户选择的强调色 [accent] 推导(见 [accentLightColorScheme] / [accentDarkColorScheme]),
 * 该颜色同时是悬浮锁屏按钮的底色 —— 界面与悬浮窗始终同色系。
 * 改了强调色后无需重建 Activity,重组即可生效。
 *
 * [dynamicColor] 默认关闭:Android 12+ 的动态取色跟随壁纸变化,
 * 会让界面配色与悬浮窗按钮脱节,破坏上面这条一致性。需要时仍可显式传入 true。
 */
@Composable
fun ScreenLockerTheme(
    accent: AccentColor = AccentColor.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // 色调板推导有计算量,按 (accent, darkTheme, dynamicColor) 缓存
    val colorScheme = remember(accent, darkTheme, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> accentDarkColorScheme(accent)
            else -> accentLightColorScheme(accent)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
