/**
 * 构建产物标识测试（T1a 唯一允许的测试）。
 *
 * 断言依据任务书 §4.6 / §6 A3：
 * - applicationId 必须是 com.zhangran.app
 * - versionName 必须是 0.1.0（不得写成 0.1 / 1.0.0）
 * - versionCode 必须是 1
 *
 * 说明：只读取 AGP 生成的 [BuildConfig] 常量，不依赖 Android 运行时与资源，
 * 因此作为纯 JVM 单元测试执行，不需要设备。
 */
package com.zhangran.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {

    @Test
    fun applicationId_is_zhangran() {
        assertEquals("com.zhangran.app", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun versionName_is_0_1_0() {
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }

    @Test
    fun versionCode_is_1() {
        assertEquals(1, BuildConfig.VERSION_CODE)
    }
}
