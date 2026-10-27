/**
 * 字段级用户修改保护（`FieldEdit`）的读写（`docs/03` §11、`M2b` §4.3）。
 *
 * 职责边界：
 * - **只做数据访问**：不校验字段名是否合法、不判断是否需要保护（规则在
 *   `UpdateTransactionFieldUseCase` / Domain）。
 * - 只**新增**保护记录，不提供按单字段删除的接口（`docs/03` §11：被保护字段永不被自动流程覆盖）；
 *   同一交易同一字段重复编辑时覆盖为最新一次（复合主键 + REPLACE）。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.FieldEditDao
import com.zhangran.app.data.local.entity.FieldEditEntity
import javax.inject.Inject
import javax.inject.Singleton

interface FieldEditRepository {

    /** 记录一次用户字段修改（同一交易同一字段覆盖为最新）。 */
    suspend fun record(fieldEdit: FieldEditEntity)

    /** 某交易的全部被保护字段（按 fieldName 升序）。 */
    suspend fun listByTransaction(transactionId: String): List<FieldEditEntity>

    /** 某交易的某字段是否已被用户修改过。 */
    suspend fun isProtected(transactionId: String, fieldName: String): Boolean
}

@Singleton
class RoomFieldEditRepository @Inject constructor(
    private val dao: FieldEditDao,
) : FieldEditRepository {

    override suspend fun record(fieldEdit: FieldEditEntity) = dao.insert(fieldEdit)

    override suspend fun listByTransaction(transactionId: String): List<FieldEditEntity> =
        dao.listByTransaction(transactionId)

    override suspend fun isProtected(transactionId: String, fieldName: String): Boolean =
        dao.find(transactionId, fieldName) != null
}
