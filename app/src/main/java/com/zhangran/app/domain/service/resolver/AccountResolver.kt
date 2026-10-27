package com.zhangran.app.domain.service.resolver

/**
 * 账户名 → 账户 id 的解析接口（`docs/03` §8）。
 *
 * 归属：`docs/03` §15 要求 Normalize **不得**访问数据库；名称→ID 查询以接口注入，
 * 由 M1c 用 DAO 实现在装配层提供。
 *
 * 约定：
 * - 上游给的是**名称**（`accountNameFrom` / `accountNameTo`），不是 id（`docs/03` §18.1）。
 * - 实现只按启用中的账户匹配（`enabled = true`）；禁用账户不得作为新接入选。
 * - 未命中返回 `null`（**不得**抛异常、不得返回空串）；未命中由 Decision 规则 7 转为
 *   `ACCOUNT_UNCERTAIN`（`docs/03` §8、§16）。
 */
fun interface AccountResolver {

    /**
     * @param accountName 账户名（已 trim；非空）
     * @return 命中返回账户 id；未命中返回 `null`
     */
    fun resolveId(accountName: String): String?
}
