## Why

当前系统仍带有明显“技术展示/占位实现”痕迹，语言中英混杂，且登录与部分模块不符合项目经理导向的业务演示场景。为了支持面向项目经理的评审，需要一次“全中文 + 演示数据闭环 + 可视化优先 + 交互统一”的改造。

## What Changes

- 将前端所有对外展示文案统一为简体中文，移除“炫技展示/PLACEHOLDER/测试模式”等非业务导向词汇。
- 将“测试数据语义”统一替换为“演示数据语义”，构建可复现的 100 条演示评论数据集，并以其驱动问题、趋势、词云、动作与验证模块。
- 趋势模块由纯文字列表升级为图表展示，并新增词云图展示，提升项目经理阅读效率。
- 登录模块升级为固定账号密码校验（`wxy` / `123456`）与三怪物交互（红蓝黄、三种形态），实现失败摇头、密码输入转头、常态视线跟随、点击蹦跳。
- 对“混沌演练”等与评论决策弱相关模块进行收敛：默认隐藏或改造为“与评论链路直接相关”的演练视图，避免脱离主线。
- 补齐改造后的前后端与 NLP 回归测试、验收脚本与演示话术文档。

## Capabilities

### New Capabilities
- `localized-pm-ui`: 面向项目经理的全中文界面与文案治理能力。
- `demo-comment-data-foundation`: 100 条演示评论数据基础能力，支撑端到端演示与复现。
- `visual-trend-and-wordcloud`: 趋势图与词云图的可视化能力。
- `three-monster-login`: 三怪物登录交互与固定账号密码校验能力。
- `comment-linked-modules`: 模块与评论决策主线强绑定的导航与内容治理能力。

### Modified Capabilities
- `<none>`: 当前改造不依赖已有归档 capability 的 requirement 变更。

## Impact

- Frontend:
  - `frontend/src/App.vue`
  - `frontend/src/components/LoginGate.vue`
  - `frontend/src/components/TrendList.vue`（或替换为图表组件）
  - 新增词云组件与图表依赖接入
  - `frontend/src/api/client.ts`
  - `frontend/src/types/domain.ts`
  - `frontend/src/test/App.spec.ts`
- Backend:
  - `backend/src/main/java/.../service/InsightQueryService.java`
  - `backend/src/main/java/.../controller/*`（issues/trends/showcase）
  - 新增/扩展演示评论相关 repository/service/controller
  - `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java`
- Data & Infra:
  - `infra/db/init/001_init.sql`（演示评论与词频支撑）
  - 统一运行时初始化与 SQL 初始化的表结构差异
- Documentation:
  - `README.md`
  - 新增面向项目经理的演示流程与验收清单文档
