/**
 * 首次安装的初始数据写入（`docs/02` §9、`docs/03` §8「首次初始化」）。
 *
 * 四项硬约束与实现方式：
 *
 * | 约束 | 实现 |
 * |---|---|
 * | **C1 仅执行一次** | [InitializationMarker]（SharedPreferences 布尔标记）；**不以"表为空"判断** |
 * | **C2 幂等** | 标记 + 按 `name + type` 判重（双保险） |
 * | **C3 删除不复活** | 标记为 `true` 后永不再写（用户删掉的预置项不会回来） |
 * | **C4 migration 不重跑** | 只在 `RoomDatabase.Callback.onCreate` 之后的首次检查里写；升级不会触发 `onCreate` |
 *
 * 为什么标记不用数据库表：新增元数据实体需要改 schema + migration，而本任务**禁止**改 schema
 * （任务书 §4.2）。SharedPreferences 零新增依赖、语义足够（一个布尔）。
 *
 * 为什么不在 `onCreate` 回调里直接写数据：`onCreate` 在**建库事务内**执行，
 * 用另一个连接（DAO）写入会死锁（任务书 R2）。因此 `onCreate` 只负责"把标记置为待初始化"，
 * 真正的写入发生在数据库**打开之后**（[ensureInitialized]），用 DAO 在独立事务内完成。
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.withTransaction
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType
import java.util.UUID

/**
 * "已完成初始数据写入"的持久化标记。
 *
 * @param context 用于取 SharedPreferences
 * @param prefsName 偏好文件名（测试可传独立名字，避免污染真实数据）
 */
class InitializationMarker(
    context: Context,
    prefsName: String = PREFS_NAME,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** 是否已完成初始化。 */
    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)

    /** 标记为"待初始化"（由 `onCreate` 调用，语义上等于"这是新库"）。 */
    fun markPending() {
        prefs.edit().putBoolean(KEY_DONE, false).commit()
    }

    /** 标记为已完成。 */
    fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).commit()
    }

    /** 清除标记（**仅供测试**模拟"全新安装"）。 */
    fun reset() {
        prefs.edit().remove(KEY_DONE).commit()
    }

    companion object {
        /** 偏好文件名（生产）。 */
        const val PREFS_NAME: String = "zhangran_initialization"

        /** 标记键。 */
        const val KEY_DONE: String = "initial_data_done"
    }
}

/**
 * 一次初始化的结果（供日志与测试断言）。
 *
 * @property insertedCategories 本次实际插入的分类数
 * @property insertedAccounts 本次实际插入的账户数
 * @property skipped 是否因"已完成"而整体跳过
 */
data class InitializationResult(
    val insertedCategories: Int = 0,
    val insertedAccounts: Int = 0,
    val skipped: Boolean = false,
)

/**
 * 初始数据写入器。
 *
 * @param marker 持久化标记（决定"是否需要写"）
 * @param clock 时间源（同一批次共用同一时间戳；测试可注入固定值）
 */
class DatabaseInitializer(
    private val marker: InitializationMarker,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * 确保初始数据已写入。
     *
     * **幂等**：标记为已完成时直接跳过；未完成时按 `name + type` 判重后补齐缺失项
     * （因此即使上一次写到一半中断，重试也不会产生重复）。
     *
     * @param database 已打开的数据库（本方法在数据库打开之后调用，避免 `onCreate` 事务内死锁）
     */
    suspend fun ensureInitialized(database: ZhangRanDatabase): InitializationResult {
        if (marker.isDone()) return InitializationResult(skipped = true)

        val now = clock()
        var insertedCategories = 0
        var insertedAccounts = 0

        database.withTransaction {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()

            // 分类：按 (type, name) 判重，逐项补齐
            val existingCategories = categoryDao.listAll()
                .map { it.type to it.name }
                .toSet()

            suspend fun insertAll(list: List<DefaultCategory>, type: CategoryType) {
                list.forEachIndexed { index, category ->
                    if ((type to category.name) in existingCategories) return@forEachIndexed
                    categoryDao.insert(
                        CategoryEntity(
                            id = idGenerator(),
                            name = category.name,
                            type = type,
                            icon = category.icon,
                            sortOrder = index,
                            enabled = true,
                            systemDefined = true,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    insertedCategories++
                }
            }

            insertAll(DEFAULT_EXPENSE_CATEGORIES, CategoryType.EXPENSE)
            insertAll(DEFAULT_INCOME_CATEGORIES, CategoryType.INCOME)

            // 账户：按 name 判重（只有一个默认账户）
            val existingAccountNames = accountDao.listAll().map { it.name }.toSet()
            if (DEFAULT_ACCOUNT.name !in existingAccountNames) {
                accountDao.insert(
                    AccountEntity(
                        id = idGenerator(),
                        name = DEFAULT_ACCOUNT.name,
                        type = DEFAULT_ACCOUNT.type,
                        channel = null,
                        last4 = null,
                        enabled = true,
                        sortOrder = 0,
                        isDefault = DEFAULT_ACCOUNT.isDefault,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                insertedAccounts++
            }
        }

        marker.markDone()
        return InitializationResult(
            insertedCategories = insertedCategories,
            insertedAccounts = insertedAccounts,
            skipped = false,
        )
    }
}
