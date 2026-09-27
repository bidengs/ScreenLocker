package org.bideng.screenlocker.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList

/**
 * 应用内语言切换的核心实现。
 *
 * 语言偏好保存在独立的 SharedPreferences 中;界面层在 `attachBaseContext`
 * 阶段用 [wrap] 替换基础 Context 的 Configuration,使字符串资源、
 * 布局方向(RTL)都按所选语言解析。
 *
 * 由于资源在 Activity 创建时就已加载,切换语言后需要调用
 * `Activity.recreate()` 让界面重新按新语言渲染。
 */
object LocaleManager {

    private const val PREFS_NAME = "screen_locker_settings"
    private const val KEY_LANGUAGE = "app_language"

    /** 当前生效的语言设置(未设置过时为 [AppLanguage.SYSTEM])。 */
    fun current(context: Context): AppLanguage = AppLanguage.fromTag(readTag(context))

    /** 保存语言设置,下次 [wrap] 时生效。 */
    fun save(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    /**
     * 按所选语言包装 [base]。
     *
     * 供 Activity / Service 的 `attachBaseContext` 调用;
     * 选择「跟随系统」时原样返回,不做任何包装。
     */
    fun wrap(base: Context): Context {
        val locale = current(base).locale ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
            // 阿拉伯语等 RTL 语言需同步更新布局方向,Compose 才会镜像布局
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, AppLanguage.SYSTEM_TAG)
            ?: AppLanguage.SYSTEM_TAG
}
