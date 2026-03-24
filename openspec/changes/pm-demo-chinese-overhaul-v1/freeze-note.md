# T0-SPEC Freeze Note

Date: 2026-03-24
Workspace: D:/code/fanbianyi/.worktrees/wh-t0-spec
Change: pm-demo-chinese-overhaul-v1

## Capability 范围确认
- `localized-pm-ui`: 全中文界面与文案治理，统一“演示数据”术语。
- `demo-comment-data-foundation`: 100 条可复现演示评论数据底座。
- `visual-trend-and-wordcloud`: 趋势图与词云图可视化能力。
- `three-monster-login`: 固定账号密码 + 三怪物交互登录能力。
- `comment-linked-modules`: 模块导航与评论决策主线强绑定。

## 关键决策确认
- 采用“演示数据模式”语义替代“测试模式”语义。
- 洞察以评论数据聚合驱动，避免纯硬编码输出。
- 趋势与词云统一采用前端图形库实现，兼顾移动端与按需加载。
- 登录交互按状态机建模（`tracking / typing / error / jumping`）。
- 模块治理采用“默认收敛 + 可解释保留”策略。

## 本次不做项（冻结）
- 生产级鉴权（JWT、RBAC、审计登录等）
- 真实混沌注入平台
- 复杂 BI 平台

## 校验证据
- 命令：`openspec validate pm-demo-chinese-overhaul-v1`
- 结果：`Change 'pm-demo-chinese-overhaul-v1' is valid`
