/**
 * 供仪器测试在应用进程里调用无障碍入库（M6-2 截图证明）。
 *
 * Hilt 只聚合主源集里的 EntryPoint，所以不能放在 androidTest。
 */
package com.zhangran.app.capture.accessibility

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccessibilityIngestEntryPoint {
    fun accessibilitySnapshotIngest(): AccessibilitySnapshotIngest
}
