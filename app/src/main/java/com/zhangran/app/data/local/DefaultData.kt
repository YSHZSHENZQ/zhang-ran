/**
 * 首次安装的初始数据清单（**唯一落点**）。
 *
 * 依据：`docs/02_产品规格.md` §9「初始数据」——清单必须与该节**逐字、逐顺序**一致。
 * 产品调整只改本文件（其他位置不得出现分类名字面量）。
 *
 * 说明：
 * - `icon` 存**语义标识**（小写英文），由 UI 层映射到图标资源；**不存 emoji**（`02` §9）。
 * - 「其他」为**兜底分类**（[DefaultCategory.isFallback] = true）：不可删除、不可重命名、不可禁用；
 *   它保证每个 `type` 下始终至少有一个可用分类（`02` §9、裁定-P009 §2.2）。
 * - `AccountType.CASH` 是 `Enums.kt` 的既有取值，**不新增枚举**。
 */
package com.zhangran.app.data.local

import com.zhangran.app.domain.model.AccountType

/**
 * 一条默认分类。
 *
 * @property name 分类名（`02` §9 的清单原文）
 * @property icon 语义图标标识（如 `food`），UI 层负责映射为图标资源
 * @property isFallback 是否为兜底分类（「其他」）；兜底项不可删除/重命名/禁用
 */
data class DefaultCategory(
    val name: String,
    val icon: String,
    val isFallback: Boolean = false,
)

/**
 * 默认账户。
 *
 * @property name 账户名
 * @property type 账户类型（仅用既有枚举值）
 * @property isDefault 是否默认账户（初始化后全库仅此一个 `true`）
 */
data class DefaultAccount(
    val name: String,
    val type: AccountType,
    val isDefault: Boolean,
)

/** 默认支出分类（12，顺序即 `sortOrder`；`02` §9）。 */
val DEFAULT_EXPENSE_CATEGORIES: List<DefaultCategory> = listOf(
    DefaultCategory(name = "餐饮", icon = "food"),
    DefaultCategory(name = "交通", icon = "transport"),
    DefaultCategory(name = "购物", icon = "shopping"),
    DefaultCategory(name = "居住", icon = "housing"),
    DefaultCategory(name = "娱乐", icon = "entertainment"),
    DefaultCategory(name = "医疗", icon = "medical"),
    DefaultCategory(name = "学习", icon = "education"),
    DefaultCategory(name = "通讯", icon = "communication"),
    DefaultCategory(name = "人情", icon = "gift"),
    DefaultCategory(name = "旅行", icon = "travel"),
    DefaultCategory(name = "宠物", icon = "pet"),
    // 兜底分类：不可删除、不可重命名、不可禁用（02 §9）
    DefaultCategory(name = "其他", icon = "other", isFallback = true),
)

/** 默认收入分类（6，顺序即 `sortOrder`；`02` §9）。 */
val DEFAULT_INCOME_CATEGORIES: List<DefaultCategory> = listOf(
    DefaultCategory(name = "工资", icon = "salary"),
    DefaultCategory(name = "奖金", icon = "bonus"),
    DefaultCategory(name = "理财", icon = "investment"),
    DefaultCategory(name = "兼职", icon = "parttime"),
    DefaultCategory(name = "红包", icon = "redpacket"),
    // 只写一次（持久化标记）；用户删掉的不得复活（02 §9）
    DefaultCategory(name = "其他", icon = "other", isFallback = true),
)

/** 默认账户（1，`02` §9：只预置一个，微信/支付宝/银行卡由用户自建）。 */
val DEFAULT_ACCOUNT: DefaultAccount = DefaultAccount(
    name = "现金",
    type = AccountType.CASH,
    isDefault = true,
)

/** 清单中的分类总数（12 + 6 = 18），供初始化与测试共用。 */
const val DEFAULT_CATEGORY_COUNT: Int = 18
