# ui/theme —— 设计 token（唯一落点）

依据：`docs/05_UI设计系统.md` §3–§6。

| 文件 | 内容 |
|---|---|
| `Color.kt` | 颜色 token（Background / Surface / SurfaceVariant / Primary / Secondary / TextPrimary / TextSecondary / TextDisabled / Divider / Expense / Income / Transfer / Warning / Error） |
| `Typography.kt` | 排版 token（Display / Headline / Title / Body / Label / Caption / **Amount**） |
| `Dimens.kt` | 间距 token（xs / sm / md / lg / xl / xxl）与组件圆角 |
| `Shape.kt` | 形状 token（Card / Dialog / Button / Input / Chip） |
| `Theme.kt` | `ZhangRanTheme` 组装（Material3 + token），支持明暗切换 |

规则：

- 页面与组件内**禁止**出现字面色值（`Color(0xFF…)`）与裸数字尺寸；
- 通过 `ZhangRanTheme.colors / shapes / spacing / radii` 访问；
- 当前为**占位值**：`TODO(视觉)` 标注了待按 `05` §3–§6 与原型 CSS 变量确定的取值。