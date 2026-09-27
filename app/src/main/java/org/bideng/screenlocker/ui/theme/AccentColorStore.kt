package org.bideng.screenlocker.ui.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * 主题强调色的持久化。
 *
 * 与语言偏好(见 `i18n/LocaleManager`)共用同一个 SharedPreferences 文件,
 * 方便以后统一导出/清理用户的界面设置。
 */
object AccentColorStore {

    private const val PREFS_NAME = "screen_locker_settings"
    private const val KEY_ACCENT = "accent_color"

    /** 当前生效的强调色。 */
    fun current(context: Context): AccentColor =
        AccentColor.fromKey(prefs(context).getString(KEY_ACCENT, null))

    /** 保存强调色。界面主题与悬浮窗按钮会在调用方切换后立即跟随。 */
    fun save(context: Context, accent: AccentColor) {
        prefs(context).edit().putString(KEY_ACCENT, accent.key).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
