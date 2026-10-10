/**
 * 交易时间与时区规范化（纯 Kotlin/JVM，无 Android 依赖）。
 *
 * 依据：
 * - `docs/03` §5 字段约束（`transactionTime` 为 UTC epoch millis；`transactionTimeZone` 记录发生时的时区 id；禁止省略时区的本地格式化字符串；禁止用 `System.currentTimeMillis()` 伪造）
 * - `docs/10` §5 上游事实（`time` 是毫秒时间戳但**不带时区**）
 * - `docs/08` §17.2「时区补齐」断言
 *
 * 设计纪律：
 * - **时区必须由调用方显式传入**；本文件不在转换函数内部读取设备时区，否则跨月归属会因设备而异且不可测。
 * - 时区 id 非法时**显式失败**，禁止回退到 `ZoneId.systemDefault()`（静默回退会造成跨月错账）。
 * - 仅提供 [deviceZoneId] 供 Adapter 显式读取设备时区（Adapter 决定用它，属 Adapter 策略）。
 */
package com.zhangran.app.domain.service

import java.time.Instant
import java.time.ZoneId
import java.time.DateTimeException

/**
 * 时间规范化的失败原因。
 */
enum class TimeIssue {
    /** 上游未提供时间。 */
    MISSING,

    /** 时间非正数（上游用 0 表示未设置）。 */
    NON_POSITIVE,

    /** 时间超出合理范围（过小或过大）。 */
    OUT_OF_RANGE,

    /** 时区 id 无法识别。 */
    INVALID_ZONE,
}

/**
 * 时间规范化结果。
 */
sealed interface TransactionTimeResult {

    /**
     * 成功。
     *
     * @property epochMillis `transactionTime`（UTC epoch millis）
     * @property zoneId `transactionTimeZone`（IANA 时区 id，如 `Asia/Shanghai`）
     */
    data class Success(
        val epochMillis: Long,
        val zoneId: String,
    ) : TransactionTimeResult {
        /** 该时间在某时区下的 `LocalDate`（供跨日/跨月归属断言使用）。 */
        fun localDateIn(zone: String = zoneId): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(zone)).toLocalDate().toString()
    }

    /**
     * 失败。
     *
     * @property issue 失败原因
     * @property detail 便于日志与测试断言的说明
     */
    data class Failure(
        val issue: TimeIssue,
        val detail: String,
    ) : TransactionTimeResult
}

/**
 * 时间与时区规范化（对象方法均为纯函数）。
 */
object TransactionTime {

    /** 可接受的最早时间（1970-01-01T00:00:00Z 之后，用于拒绝 0 / 负数）。 */
    private const val MIN_EPOCH_MILLIS: Long = 0L

    /** 可接受的最晚时间：2100-01-01T00:00:00Z。 */
    private const val MAX_EPOCH_MILLIS: Long = 4_102_444_800_000L

    /** 上游时间字段的常见单位：毫秒。 */
    private const val MILLIS_THRESHOLD: Long = 100_000_000_000L

    /**
     * 规范化上游时间戳。
     *
     * @param epochMillis 上游毫秒时间戳（`null` 表示未提供）
     * @param zoneId 交易发生时区（**必须显式传入**，例如 `"Asia/Shanghai"`）
     */
    fun fromEpochMillis(epochMillis: Long?, zoneId: String?): TransactionTimeResult {
        if (epochMillis == null) {
            return TransactionTimeResult.Failure(TimeIssue.MISSING, "上游未提供交易时间")
        }
        val zone = resolveZone(zoneId) ?: return TransactionTimeResult.Failure(
            TimeIssue.INVALID_ZONE,
            "时区 id 非法或缺失：$zoneId",
        )
        if (epochMillis <= MIN_EPOCH_MILLIS) {
            return TransactionTimeResult.Failure(
                TimeIssue.NON_POSITIVE,
                "交易时间非正数：$epochMillis",
            )
        }
        if (epochMillis > MAX_EPOCH_MILLIS) {
            return TransactionTimeResult.Failure(
                TimeIssue.OUT_OF_RANGE,
                "交易时间超出合理范围：$epochMillis",
            )
        }
        return TransactionTimeResult.Success(epochMillis = epochMillis, zoneId = zone.id)
    }

    /**
     * 规范化可能以“秒”给出的时间戳（防御上游单位不一致）。
     *
     * 判定：绝对值小于 [MILLIS_THRESHOLD] 视为秒，乘以 1000 后按毫秒处理。
     *
     * @param epoch 上游时间戳（秒或毫秒）
     * @param zoneId 交易发生时区
     */
    fun fromEpochMaybeSeconds(epoch: Long?, zoneId: String?): TransactionTimeResult {
        if (epoch == null) {
            return TransactionTimeResult.Failure(TimeIssue.MISSING, "上游未提供交易时间")
        }
        val millis = if (epoch in 1 until MILLIS_THRESHOLD) epoch * 1000 else epoch
        return fromEpochMillis(millis, zoneId)
    }

    /**
     * 读取设备当前时区 id，供 Adapter 显式使用。
     *
     * 说明：这是本文件**唯一**接触设备环境的方法，且转换函数不会隐式调用它。
     * v0.1 的策略是“用设备当前时区记录 `transactionTimeZone`”（`docs/03` §5）。
     */
    fun deviceZoneId(): String = ZoneId.systemDefault().id

    /** 解析时区 id；非法返回 `null`（由调用方转成显式失败）。 */
    private fun resolveZone(zoneId: String?): ZoneId? {
        val id = zoneId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return try {
            ZoneId.of(id)
        } catch (e: DateTimeException) {
            null
        }
    }
}
