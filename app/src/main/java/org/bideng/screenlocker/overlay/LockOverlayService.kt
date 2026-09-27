package org.bideng.screenlocker.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import org.bideng.screenlocker.MainActivity
import org.bideng.screenlocker.R
import org.bideng.screenlocker.admin.LockScreenHelper
import org.bideng.screenlocker.i18n.LocaleManager
import org.bideng.screenlocker.ui.theme.AccentColorStore
import kotlin.math.abs
import kotlin.math.max

/**
 * 前台悬浮窗服务,在系统上方显示一个可拖动的电源按钮(| O 风格)。
 *
 * 点击按钮立即调用 [LockScreenHelper.lockScreen] 让设备熄屏。
 */
class LockOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    /**
     * 让 Service 也遵循应用内选择的语言 —— 否则前台通知与 Toast
     * 会退回系统语言,与界面不一致。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 必须先调用 startForeground,Android 8.0+ 在 Service 创建后 5 秒内未启动前台会被杀
        startForegroundWithNotification()
        addOverlayView()
        notifyStateChange(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownAndRemoveOverlay()
                return START_NOT_STICKY
            }

            // 用户在应用内换了主题色 —— 正在显示的按钮需要立即换底色
            ACTION_REFRESH -> applyAccentColor()
        }
        return START_STICKY
    }

    /** 按当前保存的强调色重绘按钮底色,无需重启 Service。 */
    private fun applyAccentColor() {
        val container = overlayView ?: return
        container.background = createButtonBackground()
    }

    override fun onDestroy() {
        removeOverlayView()
        notifyStateChange(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    /**
     * 给宿主 Activity 查询当前 Service 是否已显示悬浮窗,以及通知状态变更。
     */
    inner class LocalBinder : Binder() {
        fun isOverlayAttached(): Boolean = overlayView != null
    }

    private fun shutdownAndRemoveOverlay() {
        // 先把前台通知摘掉,前台 Service 才允许立即销毁
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * 通知宿主状态变更:把当前运行状态写入静态引用,
     * 由 MainActivity 在 onStart 时主动 poll 一次,后续依赖 onDestroy 自然取消订阅。
     */
    private fun notifyStateChange(running: Boolean) {
        StateHub.running = running
        StateHub.version += 1
    }

    private fun startForegroundWithNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun addOverlayView() {
        if (overlayView != null) return

        // Android 8.0+ 必须使用 TYPE_APPLICATION_OVERLAY
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            dp(OVERLAY_WIDTH_DP),
            dp(OVERLAY_HEIGHT_DP),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(160)
        }

        overlayView = createButtonView()
        windowManager.addView(overlayView, params)
    }

    private fun removeOverlayView() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // 视图已经移除
            }
            overlayView = null
        }
    }

    /**
     * 经典电源按钮图标(| O):一个不完整的圆环(顶部有缺口) + 中央竖线。
     *
     * 视觉参考 IEC 60417-5009 电源符号,白色笔触。
     */
    private class PowerButtonIcon(context: Context) : View(context) {

        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) * 0.36f

            iconPaint.strokeWidth = radius * 0.20f
            drawGlyph(canvas, cx, cy, radius, iconPaint)
        }

        /**
         * 绘制 | O 电源符号。
         *
         * Android 的坐标系里 0° 在 3 点钟方向、角度顺时针增大,
         * 因此 90° 在正下方、270° 在正上方。缺口要留在正上方,
         * 所以从 270° + gapHalfDeg 起画,逆着缺口绕一整圈。
         */
        private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
            // 不完整的圆环:顶部留出缺口,让竖线"穿过"
            val gapHalfDeg = 38f
            canvas.drawArc(
                cx - radius, cy - radius, cx + radius, cy + radius,
                270f + gapHalfDeg,         // 缺口右沿(右上),顺时针绕回缺口左沿(左上)
                360f - 2f * gapHalfDeg,
                false, paint
            )
            // 中央竖线:从圆环顶部缺口上方延伸至圆心稍下方
            canvas.drawLine(cx, cy - radius * 1.05f, cx, cy + radius * 0.05f, paint)
        }
    }

    /** 悬浮按钮底色 —— 跟随用户在应用内选择的主题强调色。 */
    private fun createButtonBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
        setColor(AccentColorStore.current(this@LockOverlayService).argbInt)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButtonView(): View {
        val context = this

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(OVERLAY_WIDTH_DP),
                dp(OVERLAY_HEIGHT_DP)
            )
        }

        // 外层:竖长方形圆角底(无描边),颜色取自用户选择的主题色
        container.background = createButtonBackground()

        // 中层:居中的白色电源图标
        val icon = PowerButtonIcon(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(ICON_SIZE_DP),
                dp(ICON_SIZE_DP),
                Gravity.CENTER
            )
        }
        container.addView(icon)

        // 点击与拖动的处理:统一在 onTouch 中,避免 setOnClickListener 被 onTouch 拦截。
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    v.alpha = 0.85f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.alpha = 1.0f
                    if (isDragging) {
                        // 吸附到屏幕左/右侧
                        val screenWidth = resources.displayMetrics.widthPixels
                        val viewWidth = dp(OVERLAY_WIDTH_DP)
                        params.x = if (params.x + viewWidth / 2 < screenWidth / 2) {
                            dp(8)
                        } else {
                            screenWidth - viewWidth - dp(8)
                        }
                        windowManager.updateViewLayout(container, params)
                    } else {
                        // 未拖动 -> 视为点击,触发锁屏
                        val success = LockScreenHelper.lockScreen(context)
                        if (!success) {
                            // 基础 Context 已在 attachBaseContext 中按所选语言包装,
                            // 因此 getString 会直接返回对应语言的文案
                            val msgRes = if (!LockScreenHelper.isDeviceAdminActive(context)) {
                                R.string.lock_failed_no_admin
                            } else {
                                R.string.lock_failed_generic
                            }
                            android.widget.Toast.makeText(
                                context,
                                getString(msgRes),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.e(
                                "LockOverlay",
                                "锁屏失败，adminActive=${LockScreenHelper.isDeviceAdminActive(context)}"
                            )
                        }
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                            .withEndAction {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                            }.start()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    true
                }

                else -> false
            }
        }

        return container
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return max(1, (value * density).toInt())
    }

    companion object {
        const val CHANNEL_ID = "lock_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "org.bideng.screenlocker.action.STOP_OVERLAY"

        /** 主题色变化时重绘悬浮按钮。 */
        const val ACTION_REFRESH = "org.bideng.screenlocker.action.REFRESH_OVERLAY"

        /** 悬浮窗形状:竖长方形,宽度 56dp */
        private const val OVERLAY_WIDTH_DP = 56

        /** 悬浮窗形状:竖长方形,高度 96dp */
        private const val OVERLAY_HEIGHT_DP = 96

        /** 圆角半径 */
        private const val CORNER_RADIUS_DP = 16

        /** 中间电源图标尺寸 */
        private const val ICON_SIZE_DP = 40

        fun start(context: Context) {
            val intent = Intent(context, LockOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止悬浮窗:
         * 1) 发送自定义 ACTION_STOP Intent,通过 onStartCommand 走 stopForeground + stopSelf 路径;
         * 2) 兜底 stopService。
         *
         * 双管齐下避免国产 ROM 对前台 Service 延迟响应 stopService 的问题。
         */
        fun stop(context: Context) {
            val stopIntent = Intent(context, LockOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(stopIntent)
            context.stopService(Intent(context, LockOverlayService::class.java))
        }

        /**
         * 通知正在运行的悬浮窗刷新外观(用户改主题色时调用)。
         *
         * 悬浮窗没在运行时直接返回 —— 否则 `startService` 会把 Service 拉起来,
         * 用户没开悬浮窗却突然冒出个按钮。
         */
        fun refresh(context: Context) {
            if (!isRunning()) return
            val intent = Intent(context, LockOverlayService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startService(intent)
        }

        /**
         * 当前 Service 是否正在显示悬浮窗。
         * 使用静态引用 + 版本号让主页无需绑定即可获知状态变更。
         */
        @JvmStatic
        fun isRunning(): Boolean = StateHub.running

        /**
         * 状态变更计数器,主页可用于在 onResume 时检查是否需要刷新 UI。
         */
        @JvmStatic
        fun stateVersion(): Int = StateHub.version
    }

    /**
     * 进程内静态状态枢纽,被 Service 与 MainActivity 共享。
     * 用对象 + volatile 字段实现,避免依赖广播/LiveData/Flow。
     */
    private object StateHub {
        @Volatile
        var running: Boolean = false

        @Volatile
        var version: Int = 0
    }
}
