/**
 * 仪器测试自足用的清库、引导状态和无障碍 shell。
 *
 * 全量同进程不会清应用数据。待确认用例要自己清账；状态引导用例要把
 * 「已完成引导 / 已授权无障碍」拨回去，并在前往系统设置之后自己授权。
 */
package com.zhangran.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.capture.accessibility.M3aDebugEntryPoint
import com.zhangran.app.di.SettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking

private const val ACCESSIBILITY_SERVICE =
    "com.zhangran.app/com.zhangran.app.capture.accessibility.AccessibilityCaptureService"

fun instrumentationShell(command: String): String {
    val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    val text = FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }
    pipe.close()
    return text
}

fun clearCapturedLedgerForTest() {
    runBlocking { debugEntry().debugLedgerReset().clearCapturedLedger() }
}

/**
 * 清掉采集健康度。清空日志不会清这些字段（`CaptureHealthStore`）。
 * 健康度没有对外的「写回 null」，这里改同一份 DataStore。
 * Fx1 要在任意顺序下仍看到上次捕获和开始监听都是 null。
 */
fun clearCaptureHealthForTest() {
    runBlocking {
        val store = debugEntry().captureHealthStore()
        val field = store.javaClass.getDeclaredField("dataStore")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val dataStore = field.get(store) as DataStore<Preferences>
        dataStore.edit { prefs ->
            prefs.remove(longPreferencesKey("auto_ledger_last_capture_at"))
            prefs.remove(longPreferencesKey("auto_ledger_watch_started_at"))
            prefs.remove(booleanPreferencesKey("auto_ledger_rejected_samples"))
            prefs.remove(stringPreferencesKey("auto_ledger_stale_flip_marker"))
        }
        debugEntry().setAutoLedgerEnabledUseCase().invoke(false)
        debugEntry().autoLedgerStatusSource().refreshPermissions()
    }
}

/**
 * 冷启动引导只在从未完成时出现（M6-4 AC-2，`docs/cuts/cut-2026-11-11-M6-4.md`）。
 * 前面的用例可能已经点过完成，也可能用 shell 开过无障碍。这里拨回未完成、未授权。
 */
fun resetOnboardAndAccessibilityForTest() {
    instrumentationShell("settings put secure accessibility_enabled 0")
    instrumentationShell("settings delete secure enabled_accessibility_services")
    runBlocking {
        settingsEntry().settingsRepository().setOnboardCompleted(false)
        debugEntry().autoLedgerStatusSource().refreshPermissions()
    }
}

/** 与 Fx1 相同：在「前往授权」和返回之间把本应用无障碍服务打开。 */
fun grantAccessibilityForTest() {
    instrumentationShell("settings put secure accessibility_enabled 1")
    instrumentationShell("settings put secure enabled_accessibility_services $ACCESSIBILITY_SERVICE")
    runBlocking { debugEntry().autoLedgerStatusSource().refreshPermissions() }
}

/**
 * 点首页顶栏的待确认按钮。
 *
 * UX-1（`docs/cuts/cut-2026-11-24-UX-1.md`）给待确认行的朗读文本加了「，待确认」。
 * 子串「待确认」会同时命中顶栏和每一行。顶栏描述是「待确认」或「待确认 N 条」，不含顿号。
 */
fun ComposeContentTestRule.clickHomePendingButton() {
    onNode(
        hasContentDescription("待确认", substring = true) and
            !hasContentDescription("，待确认", substring = true),
    ).performClick()
}

private fun debugEntry(): M3aDebugEntryPoint {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return EntryPointAccessors.fromApplication(context.applicationContext, M3aDebugEntryPoint::class.java)
}

private fun settingsEntry(): SettingsEntryPoint {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
}
