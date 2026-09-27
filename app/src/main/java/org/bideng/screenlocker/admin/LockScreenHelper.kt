package org.bideng.screenlocker.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * 熄屏辅助类。
 *
 * 第三方应用锁屏的标准公开 API 是 [DevicePolicyManager.lockNow]，
 * 需要用户先激活本应用的设备管理员（并声明 force-lock 策略）。
 *
 * 注意：
 * - [PowerManager.goToSleep] 是系统 API，普通应用无权限调用
 * - 反射无法绕过设备管理员校验
 */
object LockScreenHelper {

    private const val TAG = "LockScreenHelper"

    /**
     * 立即锁屏熄屏。
     *
     * @return 成功返回 true；未激活设备管理员或调用失败返回 false
     */
    fun lockScreen(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)

        if (!dpm.isAdminActive(admin)) {
            Log.w(TAG, "Device admin not active, cannot lockNow()")
            return false
        }

        return try {
            dpm.lockNow()
            Log.d(TAG, "lockNow() succeeded")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "lockNow() denied", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "lockNow() failed", e)
            false
        }
    }

    /** 是否已激活本应用的设备管理员。 */
    fun isDeviceAdminActive(context: Context): Boolean {
        return isDeviceAdminActive(context, adminComponent(context))
    }

    fun isDeviceAdminActive(context: Context, adminComponent: ComponentName): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent)
    }

    fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, DeviceAdminReceiver::class.java)
    }
}
