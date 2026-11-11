/**
 * 读本机无障碍和电池优化的当前状态。界面返回前台时再读一次。
 */
package com.zhangran.app.data.device

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.zhangran.app.capture.accessibility.AccessibilityCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DevicePermissionProbe {

    fun accessibilityGranted(): Boolean

    fun batteryOptimizationIgnored(): Boolean
}

@Singleton
class AndroidDevicePermissionProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : DevicePermissionProbe {

    override fun accessibilityGranted(): Boolean {
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false
        val flat = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, AccessibilityCaptureService::class.java)
        val longName = component.flattenToString()
        val shortName = component.flattenToShortString()
        return flat.split(':').any { entry ->
            entry.equals(longName, ignoreCase = true) || entry.equals(shortName, ignoreCase = true)
        }
    }

    override fun batteryOptimizationIgnored(): Boolean = runCatching {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}
