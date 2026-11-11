/**
 * 展示交易时间时用的设备时区。
 *
 * 存储仍是 UTC epoch millis。这里每次读取 [java.util.TimeZone.getDefault]，
 * 跟系统设置走，不使用入库时记下的 `transactionTimeZone`。
 */
package com.zhangran.app.ui.common

import java.time.ZoneId
import java.util.TimeZone

object DeviceZone {

    fun current(): ZoneId = TimeZone.getDefault().toZoneId()
}
