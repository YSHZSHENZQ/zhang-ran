/**
 * 搜索 SQL 层测试（`M4e` §5；A1–A5/A7/A8/A17）。
 *
 * 真机 Room：用内存库验证 DAO 的 9 维条件、组合、排序、分页与软删除排除；
 * 走 `RoomTransactionRepository`，覆盖条件→DAO 参数的真实映射。
 */
package com.zhangran.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.RoomTransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchQueryTest {

    private lateinit var db: ZhangRanDatabase
    private lateinit var repository: RoomTransactionRepository

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day1 = dateMillis(2026, 10, 1, 10, 0)
    private val day1Late = dateMillis(2026, 10, 1, 19, 0)
    private val day2 = dateMillis(2026, 10, 2, 12, 0)
    private val day3 = dateMillis(2026, 10, 3, 9, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhangRanDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTransactionRepository(db.transactionDao())
        runBlocking {
            listOf(
                tx(
                    id = "t1",
                    merchant = "Starbucks",
                    note = "咖啡",
                    amountMinor = 3_850,
                    categoryId = "cat-food",
                    accountId = "acc-cash",
                    time = day1,
                    source = CaptureSource.MANUAL,
                    state = TransactionState.CONFIRMED,
                ),
                tx(
                    id = "t2",
                    merchant = "京东",
                    note = "数码",
                    amountMinor = 12_800,
                    categoryId = "cat-shopping",
                    accountId = "acc-alipay",
                    time = day2,
                    source = CaptureSource.NOTIFICATION,
                    state = TransactionState.PENDING,
                ),
                tx(
                    id = "t3",
                    merchant = "工资",
                    note = "十月",
                    amountMinor = 1_000_000,
                    categoryId = "cat-salary",
                    accountId = "acc-bank",
                    time = day3,
                    type = TransactionType.INCOME,
                    source = CaptureSource.MANUAL,
                    state = TransactionState.CONFIRMED,
                ),
                tx(
                    id = "t4",
                    merchant = "美团",
                    note = null,
                    amountMinor = 4_500,
                    categoryId = "cat-food",
                    accountId = "acc-cash",
                    time = day1Late,
                    source = CaptureSource.SMS,
                    state = TransactionState.IGNORED,
                ),
                tx(
                    id = "t5",
                    merchant = "已删除商户",
                    amountMinor = 9_900,
                    categoryId = "cat-food",
                    accountId = "acc-cash",
                    time = day2,
                    deletedAt = day3,
                ),
            ).forEach { repository.insert(it) }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun A1_keyword匹配商户与备注且不区分大小写() = runBlocking {
        assertEquals(listOf("t1"), search(SearchCriteria(keyword = "starbucks")).map { it.id })
        assertEquals(listOf("t1"), search(SearchCriteria(keyword = "STARBUCKS")).map { it.id })
        assertEquals(listOf("t1"), search(SearchCriteria(keyword = "咖啡")).map { it.id })
        assertEquals(listOf("t2"), search(SearchCriteria(keyword = "京东")).map { it.id })
    }

    @Test
    fun A1_amount等值与区间() = runBlocking {
        assertEquals(listOf("t1"), search(SearchCriteria(amountExactMinor = 3_850)).map { it.id })
        assertEquals(
            listOf("t4", "t1"),
            search(SearchCriteria(amountMinMinor = 3_000, amountMaxMinor = 5_000)).map { it.id },
        )
        // 等值优先：即使同时给区间，也按等值查
        assertEquals(
            listOf("t1"),
            search(
                SearchCriteria(
                    amountExactMinor = 3_850,
                    amountMinMinor = 4_000,
                    amountMaxMinor = 5_000,
                ),
            ).map { it.id },
        )
    }

    @Test
    fun A4_日期为半开区间() = runBlocking {
        val from = startOfDay(2026, 10, 2)
        val to = startOfDay(2026, 10, 3)
        assertEquals(
            listOf("t2"),
            search(SearchCriteria(dateFromInclusive = from, dateToExclusive = to)).map { it.id },
        )
        // 边界含 from、不含 to
        assertEquals(
            listOf("t2"),
            search(SearchCriteria(dateFromInclusive = day2, dateToExclusive = day3)).map { it.id },
        )
        assertEquals(
            arrayListOf("t3"),
            search(SearchCriteria(dateFromInclusive = day3)).map { it.id },
        )
    }

    @Test
    fun A1_分类账户类型来源状态单维筛选() = runBlocking {
        assertEquals(listOf("t4", "t1"), search(SearchCriteria(categoryId = "cat-food")).map { it.id })
        assertEquals(listOf("t4", "t1"), search(SearchCriteria(accountId = "acc-cash")).map { it.id })
        assertEquals(listOf("t3"), search(SearchCriteria(type = TransactionType.INCOME)).map { it.id })
        assertEquals(listOf("t4"), search(SearchCriteria(source = CaptureSource.SMS)).map { it.id })
        assertEquals(listOf("t2"), search(SearchCriteria(state = TransactionState.PENDING)).map { it.id })
    }

    @Test
    fun A8_多条件组合为AND() = runBlocking {
        assertEquals(
            listOf("t1"),
            search(
                SearchCriteria(
                    categoryId = "cat-food",
                    source = CaptureSource.MANUAL,
                    dateFromInclusive = startOfDay(2026, 10, 1),
                    dateToExclusive = startOfDay(2026, 10, 2),
                ),
            ).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            search(SearchCriteria(categoryId = "cat-food", state = TransactionState.PENDING))
                .map { it.id },
        )
    }

    @Test
    fun A5_默认交易时间倒序() = runBlocking {
        assertEquals(
            listOf("t3", "t2", "t4", "t1"),
            search(SearchCriteria(keyword = null, accountId = null, categoryId = null, state = null))
                .map { it.id },
        )
    }

    @Test
    fun A7_软删除记录不出现() = runBlocking {
        assertTrue(search(SearchCriteria(keyword = "已删除")).isEmpty())
        assertTrue(search(SearchCriteria(categoryId = "cat-food")).none { it.id == "t5" })
    }

    @Test
    fun A6_分页LIMIT_OFFSET() = runBlocking {
        repeat(55) { index ->
            repository.insert(
                tx(
                    id = "bulk-$index",
                    merchant = "批量商户$index",
                    amountMinor = 100L + index,
                    time = day1 + 60_000L * (index + 1),
                ),
            )
        }

        val firstPage = repository.search(SearchCriteria(keyword = "批量", page = 0))
        val secondPage = repository.search(SearchCriteria(keyword = "批量", page = 1))
        assertEquals(50, firstPage.size)
        assertEquals(5, secondPage.size)
        assertEquals(55, repository.countSearch(SearchCriteria(keyword = "批量")))
        assertEquals(55, firstPage.size + secondPage.size)
        // 页间不重复
        assertTrue(firstPage.map { it.id }.intersect(secondPage.map { it.id }.toSet()).isEmpty())
    }

    private suspend fun search(criteria: SearchCriteria): List<TransactionEntity> =
        repository.search(criteria)

    private fun tx(
        id: String,
        merchant: String? = null,
        note: String? = null,
        amountMinor: Long = 1_000,
        categoryId: String? = null,
        accountId: String? = null,
        time: Long,
        type: TransactionType = TransactionType.EXPENSE,
        source: CaptureSource = CaptureSource.MANUAL,
        state: TransactionState = TransactionState.CONFIRMED,
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currency = Currency.CNY,
        merchant = merchant,
        categoryId = categoryId,
        accountId = accountId,
        transactionTime = time,
        transactionTimeZone = zone.id,
        note = note,
        state = state,
        source = source,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deletedAt,
    )

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day).atTime(hour, minute)
            .atZone(zone).toInstant().toEpochMilli()

    private fun startOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()
}
