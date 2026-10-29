/**
 * 无障碍服务配置测试（`M6` 阶段 1）。
 *
 * 不依赖真机页面，只验证 Manifest/XML 声明正确：
 * - 服务受 BIND_ACCESSIBILITY_SERVICE 保护；
 * - meta-data 指向的配置包含两类事件与两个白名单包名。
 */
package com.zhangran.app.capture.accessibility

import android.content.ComponentName
import android.view.accessibility.AccessibilityEvent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityServiceConfigTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun 服务声明受BIND权限保护且含配置元数据() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, AccessibilityCaptureService::class.java),
            PackageManager.GET_META_DATA,
        )

        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            serviceInfo.permission,
        )
        assertTrue("服务必须导出给系统绑定", serviceInfo.exported)
        assertNotNull(
            "必须声明 accessibilityservice meta-data",
            serviceInfo.metaData?.getInt("android.accessibilityservice"),
        )
    }

    @Test
    fun 配置只监听微信支付宝与两类窗口事件() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, AccessibilityCaptureService::class.java),
            PackageManager.GET_META_DATA,
        )
        val configRes = serviceInfo.metaData.getInt("android.accessibilityservice")
        val parser = context.resources.getXml(configRes)
        // 推进到根 START_TAG（跳过 START_DOCUMENT/空白文本）
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        val namespace = "http://schemas.android.com/apk/res/android"

        fun attr(name: String): String? =
            parser.getAttributeValue(namespace, name) ?: parser.getAttributeValue(null, name)
        val packageNames = attr("packageNames").orEmpty()
        val eventTypes = attr("accessibilityEventTypes").orEmpty()
        val canRetrieve = attr("canRetrieveWindowContent")

        assertTrue("packageNames=$packageNames eventTypes=$eventTypes", packageNames.contains("com.tencent.mm"))
        assertTrue(packageNames.contains("com.eg.android.AlipayGphone"))
        val eventFlags = eventTypes.removePrefix("0x").toInt(16)
        assertTrue(
            "eventTypes=$eventTypes 应含 typeWindowStateChanged",
            eventFlags and AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED != 0,
        )
        assertTrue(
            "eventTypes=$eventTypes 应含 typeWindowContentChanged",
            eventFlags and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0,
        )
        assertEquals("true", canRetrieve)
    }
}
