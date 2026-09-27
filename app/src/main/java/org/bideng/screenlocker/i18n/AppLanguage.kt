package org.bideng.screenlocker.i18n

import java.util.Locale

/**
 * 应用支持的语言。
 *
 * [tag] 采用 BCP-47 语言标签,与 `res/values-xx` 资源目录一一对应;
 * [nativeName] 是该语言的母语写法 —— 无论当前界面是什么语言,
 * 用户都能在列表里一眼认出自己的语言。
 *
 * 注意:应用名与标题固定为 "Screen Locker",不随语言变化,
 * 因此各语言资源目录中都不声明 `app_name`。
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String,
) {
    /** 跟随系统语言(默认)。 */
    SYSTEM("system", ""),

    ENGLISH("en", "English"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE("pt", "Português"),
    RUSSIAN("ru", "Русский"),
    ARABIC("ar", "العربية"),
    HINDI("hi", "हिन्दी"),
    ;

    /**
     * 需要应用的 [Locale]。
     *
     * [SYSTEM] 返回 null,表示沿用系统配置、不做任何覆盖。
     */
    val locale: Locale?
        get() = if (this == SYSTEM) null else Locale.forLanguageTag(tag)

    companion object {
        /** 「跟随系统」的持久化标签。 */
        const val SYSTEM_TAG = "system"

        /** 已保存的标签 -> 枚举;无法识别时回落到 [SYSTEM]。 */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
