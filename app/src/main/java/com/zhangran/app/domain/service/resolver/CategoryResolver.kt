package com.zhangran.app.domain.service.resolver

/**
 * 分类名 → 分类 id 的解析接口（`docs/03` §7）。
 *
 * 归属：`docs/03` §15 要求 Normalize **不得**访问数据库；因此名称→ID 的查询以接口注入，
 * 由 M1c 用 DAO 实现在装配层提供（本任务不碰 data 层）。
 *
 * 约定：
 * - 输入为**已拆分**的单个名称（可能是"父/子"完整名，也可能是子名；
 *   调用顺序与回退策略由 `Normalize` 决定，见任务书 §4.4 D-3）。
 * - 实现必须按交易方向对应的 `CategoryType` 过滤（收入分类与支出分类互不可用，`docs/03` §7）；
 *   过滤所需的类型由实现从会话/参数获取，本接口只负责"名字 → id"。
 * - 未命中返回 `null`（**不得**抛异常、不得返回空串）。
 */
fun interface CategoryResolver {

    /**
     * @param categoryName 分类名（已 trim；非空）
     * @return 命中返回分类 id；未命中返回 `null`
     */
    fun resolveId(categoryName: String): String?
}
