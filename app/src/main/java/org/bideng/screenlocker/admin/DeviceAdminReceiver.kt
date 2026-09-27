package org.bideng.screenlocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import org.bideng.screenlocker.R
import org.bideng.screenlocker.i18n.LocaleManager

/**
 * 设备管理员接收器
 *
 * 用于接收设备管理员相关的事件回调
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        showToast(context, R.string.admin_enabled_toast)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        showToast(context, R.string.admin_disabled_toast)
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        // 可以在这里添加密码变更后的处理逻辑
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // 密码输入失败
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        // 密码输入成功
    }

    /**
     * 广播接收器由系统直接创建,不会经过 Activity 的基础 Context 包装,
     * 因此这里手动套用一次语言偏好,保证提示与界面语言一致。
     */
    private fun showToast(context: Context, @StringRes messageRes: Int) {
        val localized = LocaleManager.wrap(context)
        Toast.makeText(localized, localized.getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}
