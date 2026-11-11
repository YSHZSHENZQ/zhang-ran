/**
 * 用户对疑似重复的判定（`docs/03` §20）。
 *
 * 只负责读写判定记录。不改交易状态，也不删除证据。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.DuplicateDecisionDao
import com.zhangran.app.data.local.entity.DuplicateDecisionEntity
import javax.inject.Inject
import javax.inject.Singleton

interface DuplicateDecisionRepository {

    suspend fun insert(decision: DuplicateDecisionEntity)

    suspend fun listAll(): List<DuplicateDecisionEntity>
}

@Singleton
class RoomDuplicateDecisionRepository @Inject constructor(
    private val decisions: DuplicateDecisionDao,
) : DuplicateDecisionRepository {

    override suspend fun insert(decision: DuplicateDecisionEntity) {
        decisions.insert(decision)
    }

    override suspend fun listAll(): List<DuplicateDecisionEntity> = decisions.listAll()
}
