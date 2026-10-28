/**
 * 关于页的静态文案（`M4d` §4.2/§4.3；SSOT：`docs/06` §4.18、`docs/02` §18）。
 *
 * 集中存放的原因：
 * - 隐私 4 条必须**逐字**使用 D1 后的口径，禁止出现"不申请 INTERNET / 整个方案不联网"等旧措辞；
 * - 版本号不在这里（来自 `BuildConfig`，见 `AboutScreen`）；
 * - 采集核心来源与 commit 来自 `docs/07` §20 的版本锚点（冻结 commit `34b38f8`）。
 *
 * 本页**不实现** GitHub / 反馈 / 检查更新三个网络入口（`06` §562）。
 */
package com.zhangran.app.ui.screen.about

object AboutInfo {

    const val APP_NAME: String = "账然"

    const val SLOGAN_LINE_1: String = "不主动，不打扰"
    const val SLOGAN_LINE_2: String = "账目自然清晰"

    /** 隐私承诺（D1 口径，逐字；顺序固定）。 */
    val PRIVACY_LINES: List<String> = listOf(
        "所有数据仅存储在本地",
        "INTERNET 仅用于与本机识别服务的回环通信，账务数据不出本机",
        "不收集、不上传任何信息",
        "无障碍服务只读取可见文本",
    )

    /** 开源协议摘要（点「开源协议」展开）。 */
    const val LICENSE_SUMMARY: String =
        "账然自身未声明开源许可（个人自用项目）。\n" +
            "内嵌采集核心 AutoAccounting 采用 GPL-3.0；仅个人自用、不公开分发，" +
            "并已保留原作者版权声明。\n" +
            "如未来公开分发，需重新做许可证合规评估。"

    const val LICENSE_ROW_DESCRIPTION: String = "GPL-3.0 · 仅个人自用"

    /** 采集核心来源与许可（点「采集核心」展开）。 */
    const val CAPTURE_CORE_NAME: String = "AutoAccounting"
    const val CAPTURE_CORE_REPOSITORY: String =
        "https://github.com/AutoAccountingOrg/AutoAccounting.git"
    const val CAPTURE_CORE_COMMIT: String = "34b38f8"
    const val CAPTURE_CORE_LICENSE: String = "GPL-3.0"
    const val CAPTURE_CORE_ROW_DESCRIPTION: String = "AutoAccounting · GPL-3.0"

    /** 采集核心摘要（页内展开）。 */
    const val CAPTURE_CORE_SUMMARY: String =
        "来源：AutoAccounting（Gradle 模块内嵌）\n" +
            "仓库：$CAPTURE_CORE_REPOSITORY\n" +
            "冻结 commit：$CAPTURE_CORE_COMMIT\n" +
            "许可：$CAPTURE_CORE_LICENSE · 仅个人自用、不公开分发"

    /** 版本文案前缀（实际值来自 `BuildConfig`，格式：`版本 0.1.0 (1)`）。 */
    const val VERSION_PREFIX: String = "版本 "

    /**
     * 明确**禁止**在本页出现的措辞/入口（供单测防回归）。
     *
     * - 旧双 APK 时代的隐私措辞（D1 已改口径）；
     * - 产品范围决定不提供的网络入口。
     */
    val FORBIDDEN_TEXTS: List<String> = listOf(
        "不申请 INTERNET",
        "不申请网络权限",
        "整个方案不联网",
        "完全离线",
        "零网络",
        "GitHub 仓库",
        "反馈与建议",
        "检查更新",
        "版本 1.0.0",
    )
}
