package org.bideng.screenlocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.bideng.screenlocker.admin.DeviceAdminReceiver
import org.bideng.screenlocker.admin.LockScreenHelper
import org.bideng.screenlocker.i18n.AppLanguage
import org.bideng.screenlocker.i18n.LocaleManager
import org.bideng.screenlocker.overlay.LockOverlayService
import org.bideng.screenlocker.ui.theme.AccentColor
import org.bideng.screenlocker.ui.theme.AccentColorStore
import org.bideng.screenlocker.ui.theme.ScreenLockerTheme

class MainActivity : ComponentActivity() {

    /**
     * 在 Activity 创建之前替换基础 Context,让资源按用户选择的语言加载。
     *
     * 应用名与标题固定为 "Screen Locker",不受此设置影响。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            // 主题色与语言不同:它只需要驱动重组,不必重建 Activity
            var accent by remember { mutableStateOf(AccentColorStore.current(context)) }

            ScreenLockerTheme(accent = accent) {
                HomeRoute(
                    accent = accent,
                    onSelectAccent = { selected ->
                        accent = selected
                        AccentColorStore.save(context, selected)
                        // 悬浮窗若正在显示,让它立即换底色
                        LockOverlayService.refresh(context)
                    },
                )
            }
        }
    }
}

/**
 * 承载界面状态的路由层。
 *
 * 权限状态与悬浮窗运行状态在此集中维护,每 400ms 轮询一次 ——
 * 用户从系统设置页返回、或悬浮窗被系统回收后,界面都能自动跟随。
 */
@Composable
private fun HomeRoute(
    accent: AccentColor,
    onSelectAccent: (AccentColor) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 语言偏好保存在 SharedPreferences 中,当前生效值只在创建时读取一次
    val activity = remember(context) { context.findActivity() }
    val currentLanguage = remember(context) { LocaleManager.current(context) }

    var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays(context)) }
    var isAdminActive by remember {
        mutableStateOf(LockScreenHelper.isDeviceAdminActive(context))
    }
    var isOverlayRunning by remember { mutableStateOf(LockOverlayService.isRunning()) }

    val refresh: () -> Unit = {
        hasOverlayPermission = canDrawOverlays(context)
        isAdminActive = LockScreenHelper.isDeviceAdminActive(context)
        isOverlayRunning = LockOverlayService.isRunning()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(400)
        }
    }

    HomeScreen(
        hasOverlayPermission = hasOverlayPermission,
        isAdminActive = isAdminActive,
        isOverlayRunning = isOverlayRunning,
        currentLanguage = currentLanguage,
        currentAccent = accent,
        onGrantOverlay = { context.startActivity(overlayPermissionIntent(context)) },
        onGrantAdmin = { context.startActivity(deviceAdminIntent(context)) },
        onToggleOverlay = { enable ->
            if (enable) LockOverlayService.start(context) else LockOverlayService.stop(context)
        },
        onSelectLanguage = { language ->
            // 先落盘,再重建 Activity:重建时 attachBaseContext 会按新语言加载资源
            LocaleManager.save(context, language)
            activity?.recreate()
        },
        onSelectAccent = onSelectAccent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    hasOverlayPermission: Boolean,
    isAdminActive: Boolean,
    isOverlayRunning: Boolean,
    currentLanguage: AppLanguage,
    currentAccent: AccentColor,
    onGrantOverlay: () -> Unit,
    onGrantAdmin: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectAccent: (AccentColor) -> Unit,
) {
    val ready = hasOverlayPermission && isAdminActive
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverlayStatusCard(
                isOverlayRunning = isOverlayRunning,
                ready = ready,
                onToggle = onToggleOverlay,
            )

            SectionLabel(stringResource(R.string.section_permissions))

            PermissionCard(
                iconRes = R.drawable.ic_lock,
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                granted = hasOverlayPermission,
                actionText = stringResource(R.string.perm_action_grant),
                onAction = onGrantOverlay,
            )

            PermissionCard(
                iconRes = R.drawable.ic_settings,
                title = stringResource(R.string.perm_admin_title),
                description = stringResource(R.string.perm_admin_desc),
                granted = isAdminActive,
                actionText = stringResource(R.string.perm_action_grant),
                onAction = onGrantAdmin,
            )

            SectionLabel(stringResource(R.string.section_how_to))

            UsageCard()

            SectionLabel(stringResource(R.string.section_theme))

            AppearanceCard(
                currentAccent = currentAccent,
                onSelect = onSelectAccent,
            )

            SectionLabel(stringResource(R.string.section_language))

            LanguageCard(
                currentLanguage = currentLanguage,
                onChangeClick = { showLanguageDialog = true },
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            selected = currentLanguage,
            onSelect = { language ->
                showLanguageDialog = false
                onSelectLanguage(language)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

/**
 * 主状态卡:左侧电源符号 + 状态文案,右侧 M3 开关直接控制悬浮窗启停。
 *
 * 卡片底色随状态切换 —— 运行中为 primaryContainer(强调),
 * 未运行时为 surfaceContainerHigh(中性)。
 */
@Composable
private fun OverlayStatusCard(
    isOverlayRunning: Boolean,
    ready: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val containerColor = if (isOverlayRunning) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isOverlayRunning) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PowerGlyph(
                            color = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.overlay_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            isOverlayRunning -> stringResource(R.string.overlay_state_running)
                            ready -> stringResource(R.string.overlay_state_ready)
                            else -> stringResource(R.string.overlay_state_blocked)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Switch(
                    checked = isOverlayRunning,
                    onCheckedChange = onToggle,
                    enabled = ready,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            if (!ready) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = contentColor.copy(alpha = 0.16f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.overlay_hint_blocked),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 权限项卡片。
 *
 * 已授权时右侧显示对勾,未授权时显示「去授权」按钮 ——
 * 用状态图标替代整块彩色卡片,视觉更克制。
 */
@Composable
private fun PermissionCard(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            if (granted) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = stringResource(R.string.perm_granted),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                FilledTonalButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}

/**
 * 使用步骤卡片。
 */
@Composable
private fun UsageCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TipRow("1", stringResource(R.string.tip_step_1))
            TipRow("2", stringResource(R.string.tip_step_2))
            TipRow("3", stringResource(R.string.tip_step_3))
        }
    }
}

@Composable
private fun TipRow(index: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = index,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** 外观卡片里每行放几个色块。 */
private const val SWATCHES_PER_ROW = 5

/**
 * 外观设置卡:一排色块,点一下即同时切换悬浮按钮底色与界面主题色。
 *
 * 色块不带文字标签 —— 颜色本身就是最好的说明,也省掉了 12 种语言的色名翻译。
 * 每行放 [SWATCHES_PER_ROW] 个,9 种颜色正好折成两行,窄屏也不会溢出。
 */
@Composable
private fun AppearanceCard(
    currentAccent: AccentColor,
    onSelect: (AccentColor) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.theme_card_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.theme_card_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.chunked(SWATCHES_PER_ROW).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { accent ->
                            AccentSwatch(
                                accent = accent,
                                selected = accent == currentAccent,
                                onClick = { onSelect(accent) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个色块。选中时外圈描一道粗环并在中心叠一个对勾 ——
 * 对勾用黑或白,取决于底色明度,保证在任何颜色上都看得清。
 */
@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .padding(if (selected) 4.dp else 3.dp)
            .clip(CircleShape)
            .background(accent.color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = if (accent.color.luminance() > 0.55f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 语言设置卡片:左侧地球图标 + 当前语言,右侧「更改」按钮。
 *
 * 当前语言始终以该语言的母语写法展示 —— 即便界面语言不是用户熟悉的语言,
 * 也能在列表里一眼找到自己的选项。
 */
@Composable
private fun LanguageCard(
    currentLanguage: AppLanguage,
    onChangeClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_language),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.language_current),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentLanguage.displayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            FilledTonalButton(onClick = onChangeClick) {
                Text(stringResource(R.string.language_action_change))
            }
        }
    }
}

/**
 * 语言选择对话框:单选列表,首项固定为「跟随系统」。
 *
 * 选中后立即保存并重建 Activity,界面随之切换。
 */
@Composable
private fun LanguagePickerDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppLanguage.entries.forEach { language ->
                    LanguageOptionRow(
                        label = language.displayLabel(),
                        selected = language == selected,
                        onClick = { onSelect(language) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 语言在列表中的展示名:「跟随系统」按当前界面语言翻译,其余一律用母语写法。
 */
@Composable
private fun AppLanguage.displayLabel(): String =
    if (this == AppLanguage.SYSTEM) {
        stringResource(R.string.language_follow_system)
    } else {
        nativeName
    }

/**
 * 电源符号(| O),与悬浮窗按钮上的图标同源 —— 同一个几何参数,
 * 让界面与悬浮窗在视觉上相互呼应。
 */
@Composable
private fun PowerGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = radius * 0.20f
        val gapHalfDeg = 38f

        drawArc(
            color = color,
            startAngle = 270f + gapHalfDeg,
            sweepAngle = 360f - 2f * gapHalfDeg,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 1.05f),
            end = Offset(center.x, center.y + radius * 0.05f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun canDrawOverlays(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

/**
 * 跳转系统「显示在其他应用上层」授权页。
 *
 * Android 6.0 起悬浮窗属于特殊权限,只能在系统设置中手动开关。
 */
private fun overlayPermissionIntent(context: Context): Intent {
    return Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
}

/**
 * 跳转设备管理员激活页。
 *
 * [DevicePolicyManager.lockNow] 要求应用已被激活为设备管理员。
 */
private fun deviceAdminIntent(context: Context): Intent {
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            ComponentName(context, DeviceAdminReceiver::class.java),
        )
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.admin_request_explanation),
        )
    }
}

/**
 * 从 Compose 提供的 Context 逐层向上找到宿主 Activity。
 *
 * 切换语言后需要调用 `Activity.recreate()`,让界面按新语言的资源重新渲染。
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return null
}
