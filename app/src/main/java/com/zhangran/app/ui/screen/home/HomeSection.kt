/**
 * 首页日期分组模型与图标视觉映射（`M2e-1b` §4.4 / §4.1）。
 *
 * 为什么分组标题是"已算好的字符串"：分组规则（今天/昨天/日期、跨年显示年份、时区）
 * 属于展示口径，必须在 ViewModel 的纯函数里算完并单测（任务书 §7 R4/R11），
 * Composable 只负责画，不再判日期。
 *
 * 为什么图标视觉映射集中在本文件：`AGENTS.md` §7.2 要求"不得散落 if-else"。
 * 分类 → 渐变组、分类 → emoji 的规则只在这里定义一次，交易行与后续页面共用。
 * emoji 的**语义标识 → 字形**映射仍复用 [com.zhangran.app.ui.common.CategoryIconMapper]。
 */
package com.zhangran.app.ui.screen.home

import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.theme.IconGradientGroup

/**
 * 一个日期分组（`M2e-1b` §4.4）。
 *
 * @property label 分组标题：今天 / 昨天 / `M月D日` / 跨年时 `YYYY年M月D日`
 * @property items 组内交易（已按 `transactionTime` 倒序）
 */
data class HomeSection(
    val label: String,
    val items: List<TransactionListItem>,
)

/**
 * 分类的视觉映射：分层渐变组 + emoji。
 *
 * 渐变分组依据任务书 §4.1 的语义（橙 = 餐饮/日常；蓝 = 购物/数码/学习；绿 = 交通/居住/出行；
 * 紫 = 收入类别；红 = 医疗/人情）。
 *
 * **补充规则**（原型只给了 5 组示例，未穷举全部 18 个预置分类；见交付说明）：
 * 任务书示例未覆盖的分类按语义就近归组，集中在本表，便于一次性调整。
 * 未知/缺失标识 → 默认橙组；收入类型且无分类 → 紫组；emoji 走 [CategoryIconMapper] 的默认值。
 */
object HomeCategoryVisuals {

    /** 分类语义标识 → 渐变组（覆盖 P-009 的 12 支出 + 6 收入标识）。 */
    private val GRADIENT_BY_ICON: Map<String, IconGradientGroup> = linkedMapOf(
        // 橙：餐饮、日常
        "food" to IconGradientGroup.ORANGE,
        "other" to IconGradientGroup.ORANGE,
        "pet" to IconGradientGroup.ORANGE,
        "entertainment" to IconGradientGroup.ORANGE,
        // 蓝：购物、数码、学习
        "shopping" to IconGradientGroup.BLUE,
        "communication" to IconGradientGroup.BLUE,
        "education" to IconGradientGroup.BLUE,
        // 绿：交通、居住、出行
        "transport" to IconGradientGroup.GREEN,
        "housing" to IconGradientGroup.GREEN,
        "travel" to IconGradientGroup.GREEN,
        // 紫：收入类别
        "salary" to IconGradientGroup.PURPLE,
        "bonus" to IconGradientGroup.PURPLE,
        "investment" to IconGradientGroup.PURPLE,
        "parttime" to IconGradientGroup.PURPLE,
        "redpacket" to IconGradientGroup.PURPLE,
        // 红：医疗、人情
        "medical" to IconGradientGroup.RED,
        "gift" to IconGradientGroup.RED,
    )

    /** 未知/缺失分类的默认渐变组（任务书 §4.1：未知 → 默认橙色组）。 */
    val DEFAULT_GRADIENT: IconGradientGroup = IconGradientGroup.ORANGE

    /**
     * 选渐变组。
     *
     * @param categoryIcon 分类语义标识（可为 `null`：转账无分类、或分类已删除）
     * @param type 交易类型；收入类在无分类时用紫组（"收入类别"语义），其余用默认橙组
     */
    fun gradientFor(categoryIcon: String?, type: TransactionType): IconGradientGroup {
        if (categoryIcon != null) {
            GRADIENT_BY_ICON[categoryIcon]?.let { return it }
        }
        return when (type) {
            TransactionType.INCOME -> IconGradientGroup.PURPLE
            else -> DEFAULT_GRADIENT
        }
    }

    /** 选 emoji：语义标识 → 字形；未知/缺失 → [CategoryIconMapper.DEFAULT_EMOJI]。 */
    fun emojiFor(categoryIcon: String?): String = CategoryIconMapper.toEmoji(categoryIcon)

    /** 全部受支持的分类标识（供自检：`CategoryIconMapper` 已知的标识都应能归组）。 */
    val mappedIcons: Set<String> get() = GRADIENT_BY_ICON.keys
}
