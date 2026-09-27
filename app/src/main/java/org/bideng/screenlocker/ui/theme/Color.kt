package org.bideng.screenlocker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 语义固定的颜色。
 *
 * 主色 / 次要色 / 第三色 / 中性色**不再写死在这里** —— 它们由用户选择的
 * [AccentColor] 动态推导,见 `AccentScheme.kt`。只有「错误」在 MD3 中属于
 * 语义色(必须让人一眼看出是错误),不随强调色变化,因此保留固定色值。
 */

// ── 错误色 Error ─────────────────────────────────────────────
val Error10 = Color(0xFF410E0B)
val Error20 = Color(0xFF601410)
val Error30 = Color(0xFF8C1D18)
val Error40 = Color(0xFFB3261E)
val Error80 = Color(0xFFF2B8B5)
val Error90 = Color(0xFFF9DEDC)
