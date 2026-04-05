# 项目改进计划：从“看见真实用户问题”到“支持更快决策”

## TL;DR
> **Summary**: 这个项目存在的根本原因，不是为了“做一个评论分析系统”，而是为了让管理者更直观地看见真实用户问题，并把问题、行动、验证串成可决策的闭环。当前仓库已经具备 demo 能力，但在价值表达、架构可信度、质量稳定性三方面都还停留在“能演示”而非“能支撑持续决策”。
> **Deliverables**:
> - 一个以“真实用户问题”为核心的单线决策视图
> - 一套从评论 → 问题 → 行动 → 验证的统一数据口径
> - 一组去占位化、可证明、可重复演示的核心能力
> - 一条最小但可信的质量防线（测试、类型检查、CI）
> **Effort**: Large
> **Parallel**: YES - 2 waves
> **Critical Path**: 1 → 2 → 3 → 4 → 7

## Context
### Original Request
为当前项目写一份改进计划；从第一性原理出发，先回答“为什么需要这个项目”；用于向无技术背景老板汇报；重点覆盖产品价值、技术架构和质量；表达上强调直观展现与可视化，不靠堆数字；不需要时间线；项目定位为内部决策辅助工具。

### Interview Summary
- 汇报对象是老板，且无技术背景。
- 计划既要能汇报，也要能指导后续执行。
- 老板关心的是：能否更直观看到真实用户问题，并因此更快做产品决策。
- 项目定位不是对外产品，而是内部辅助决策工具。
- 表达方式上应优先使用图像、前后对比和故事化单线流程，而不是技术指标堆砌。

### Metis Review (gaps addressed)
- 将计划严格限定为“提高决策清晰度、可信度、可重复性”，避免扩展成全量生产化重构。
- 每个改进项都必须同时说明：业务意图、非目标、证据来源、可执行验证、回退边界。
- 计划默认当前系统仍是 demo/prototype，不假设已经具备真实生产流量或稳定业务使用。
- 质量改进只覆盖会影响“老板信任”和“重复演示稳定性”的最小闭环，不追求一次性补齐全部工程债。

## Work Objectives
### Core Objective
把当前“蓝牙耳机评论改进决策系统”从一个可展示的分析 demo，改造成一个**能稳定呈现真实用户问题、支持优先级判断、并能证明改进行动是否有效**的内部决策辅助工具。

### Deliverables
- 一个面向老板的“单屏主叙事”看板：先展示用户最痛的问题，再展示建议动作，最后展示验证结果。
- 一套统一的数据与口径机制，确保前端、后端、NLP 输出不互相打架。
- 一套去掉占位内容、可复现演示的核心路径。
- 一条最小质量防线：测试命令、类型检查/静态检查、CI 自动执行。

### Definition of Done (verifiable conditions with commands)
- `npm --prefix frontend test` 通过。
- `npm --prefix frontend run build` 通过。
- `mvn -f backend/pom.xml test` 通过。
- `python -m pytest nlp-service/tests -q` 通过。
- CI 配置存在，且至少执行 frontend test/build、backend test、nlp pytest。
- 核心 demo 路径中不再依赖 `implemented=false` 的 showcase 占位返回。
- 从“问题洞察 → 改进行动 → 验证结果”可用同一组样例数据完整跑通。

### Must Have
- 叙事中心从“模块罗列”改为“决策闭环”。
- 可视化必须强调真实问题、影响范围、建议动作、结果验证四层信息。
- 系统内所有关键结论必须能追溯到同一来源数据或统一计算逻辑。
- 演示链路必须稳定、可重复、可验证。

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- 不把本计划扩展成完整生产平台重构。
- 不引入与“帮助老板更快做决策”无关的炫技功能。
- 不使用无法验证来源的结论、图表或文案。
- 不把“质量提升”等同于无边界清理所有历史工程问题。

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.
- Test decision: tests-after + existing Vitest / Spring Boot Test / pytest
- QA policy: Every task has agent-executed scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: 定义决策主线、统一口径、识别占位内容、建立最小质量防线（Tasks 1-4）

Wave 2: 重构看板表达、补齐验证闭环、提升演示可信度（Tasks 5-8）

### Dependency Matrix (full, all tasks)
- 1 blocks 2, 5, 6
- 2 blocks 5, 6, 7
- 3 blocks 7
- 4 blocks 8
- 5 blocks 8
- 6 blocks 8
- 7 blocks 8
- 8 blocks Final Verification Wave

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 4 tasks → deep / writing / unspecified-high
- Wave 2 → 4 tasks → visual-engineering / deep / unspecified-high
- Final Verification → 4 tasks → oracle / unspecified-high / deep

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [ ] 1. 重写项目主叙事：从“分析系统”改为“决策辅助工具”

  **What to do**: 基于现有 README、设计文档和前端首页文案，统一项目的对内表述：项目存在的原因是“帮助管理者看到真实用户问题并更快做决策”，不是“展示评论分析模块”。将首页主标题、摘要文案、README 开头和演示脚本改成同一叙事。
  **Must NOT do**: 不扩展到对外商业化定位；不引入未经验证的商业收益承诺。

  **Recommended Agent Profile**:
  - Category: `writing` — Reason: 该任务核心是统一业务叙事与表达逻辑
  - Skills: `[]` — 暂无特定技能依赖
  - Omitted: `[brand-guidelines]` — 当前重点是内部汇报一致性，不是品牌营销语气

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 2, 5, 6 | Blocked By: none

  **References**:
  - Pattern: `docs/项目初始设计文档.md` — 项目原始目标与闭环定义
  - Pattern: `README.md` — 当前对项目用途、使用方式的外显说明
  - Pattern: `frontend/src/App.vue` — 当前首页命名与模块组织方式
  - API/Type: `docs/接口文档.md` — 能力边界与已实现/未实现能力的事实依据

  **Acceptance Criteria**:
  - [ ] README、首页主标题/摘要、演示脚本都明确强调“看见真实用户问题 → 支持决策”
  - [ ] 不再使用“分析模块集合”作为主叙事结构

  **QA Scenarios**:
  ```
  Scenario: Narrative consistency check
    Tool: Bash
    Steps: Read README.md, docs/项目初始设计文档.md, frontend/src/App.vue and compare intro copy against the new decision-support framing.
    Expected: Three surfaces use consistent wording around user-problem visibility, decision support, and closed-loop validation.
    Evidence: .sisyphus/evidence/task-1-narrative-consistency.md

  Scenario: Scope-drift check
    Tool: Bash
    Steps: Search updated copy for customer-facing product claims, commercialization promises, or production-grade guarantees.
    Expected: No unsupported external-product or guaranteed-ROI claims are present.
    Evidence: .sisyphus/evidence/task-1-narrative-scope.md
  ```

  **Commit**: YES | Message: `docs(strategy): reframe project as decision-support tool` | Files: `README.md`, `frontend/src/App.vue`, `docs/项目初始设计文档.md`

- [ ] 2. 建立统一事实口径：定义“问题、行动、验证”的单一来源

  **What to do**: 梳理前端、后端、NLP 三处对“问题聚类、趋势、词云、行动、验证”的定义与计算来源，建立统一契约。优先消除同一问题在多个服务中口径不一致的风险，确保一个结论只能有一个权威来源。
  **Must NOT do**: 不重写全部 NLP 逻辑；不引入新的复杂数据平台。

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: 需要跨前后端和数据流的系统性梳理
  - Skills: `[]` — 基于现有代码与文档即可完成
  - Omitted: `[openspec]` — 当前不是独立规格体系建设，而是收敛现有契约

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 5, 6, 7 | Blocked By: 1

  **References**:
  - Pattern: `frontend/src/api/client.ts` — 前端实际消费的接口与 fallback 行为
  - Pattern: `backend/src/main/java/com/wh/review/backend/controller/` — 后端对外接口边界
  - Pattern: `backend/src/main/java/com/wh/review/backend/service/` — 洞察、聚合、同步、验证逻辑
  - Pattern: `nlp-service/app/main.py` — NLP 服务入口与对外分析能力
  - API/Type: `infra/db/init/001_init.sql` — reviews / issue_clusters / improvement_actions / validation_metrics 数据模型
  - External: `docs/接口文档.md` — 哪些接口为 static/placeholder 的事实说明

  **Acceptance Criteria**:
  - [ ] 每个关键业务概念都有唯一权威来源服务或表结构
  - [ ] 前端不再通过隐藏 fallback 掩盖真实数据缺失
  - [ ] “问题 → 行动 → 验证”在文档与实现中使用同一术语和口径

  **QA Scenarios**:
  ```
  Scenario: Source-of-truth trace
    Tool: Bash
    Steps: Trace one demo issue from DB schema through backend service/controller to frontend API client and rendered view.
    Expected: The issue definition, action registration, and validation metric all resolve to a documented single source of truth.
    Evidence: .sisyphus/evidence/task-2-source-of-truth.md

  Scenario: Cross-service inconsistency check
    Tool: Bash
    Steps: Compare issue/trend/validation fields exposed by frontend client, backend endpoints, and NLP response models.
    Expected: Any mismatched names, duplicate calculations, or placeholder substitutions are removed or explicitly documented.
    Evidence: .sisyphus/evidence/task-2-contract-audit.md
  ```

  **Commit**: YES | Message: `refactor(core): unify decision data contracts` | Files: `frontend/src/api/client.ts`, `backend/src/main/java/com/wh/review/backend/**`, `nlp-service/app/**`, `docs/接口文档.md`

- [ ] 3. 清理“看起来真实、其实占位”的演示能力

  **What to do**: 识别并移除或明确标注当前 `implemented=false`、静态 compare、假队列状态、showcase placeholder 等内容；保留真正能跑通的能力，去掉会误导老板判断成熟度的“伪完成感”。
  **Must NOT do**: 不为了去占位而临时拼接虚假数据；不以 UI 美化掩盖未实现能力。

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: 需要跨文档、前端和后端同步清理误导性能力
  - Skills: `[]` — 无需额外技能
  - Omitted: `[code-simplifier]` — 当前重点是事实准确，不是代码优雅化

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 7 | Blocked By: none

  **References**:
  - Pattern: `docs/接口文档.md` — `compare` static、analysis QUEUED、showcase implemented=false 的明确说明
  - Pattern: `frontend/src/api/client.ts` — 前端 fallback 与展示数据来源
  - Pattern: `backend/src/main/java/com/wh/review/backend/service/OneBoundReviewClient.java` — 当前较真实的外部数据路径
  - Pattern: `README.md` — demo/本地启动定位说明

  **Acceptance Criteria**:
  - [ ] 所有未实现能力在 UI 和文档中被移除、降级或显式标注
  - [ ] 老板演示路径中不再出现“看似可用、实则占位”的模块

  **QA Scenarios**:
  ```
  Scenario: Placeholder audit
    Tool: Bash
    Steps: Search docs and code for implemented=false, static compare behavior, QUEUED-only analysis status, and showcase placeholder markers.
    Expected: All such paths are either removed from the main demo flow or explicitly labeled as non-core/preview.
    Evidence: .sisyphus/evidence/task-3-placeholder-audit.md

  Scenario: Demo honesty check
    Tool: Playwright
    Steps: Open the main dashboard flow and navigate only through boss-facing core views.
    Expected: No core view exposes a fake-complete capability without a preview/experimental label.
    Evidence: .sisyphus/evidence/task-3-demo-honesty.png
  ```

  **Commit**: YES | Message: `fix(demo): remove misleading placeholder capabilities` | Files: `frontend/src/**`, `backend/src/main/java/com/wh/review/backend/**`, `docs/接口文档.md`, `README.md`

- [ ] 4. 建立最小质量防线，确保演示可重复而非碰运气

  **What to do**: 在保留现有测试体系的基础上，补齐缺失的自动化护栏：frontend typecheck script、基础 lint/静态检查、CI 流水线。目标不是追求完美工程，而是保证每次演示前都能自动验证“不会当场坏掉”。
  **Must NOT do**: 不引入重量级质量平台；不新增与当前仓库规模不匹配的复杂流程。

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: 涉及前端、后端、NLP 与 CI 的最小统一质量控制
  - Skills: `[]` — 现有项目配置足以完成
  - Omitted: `[claude-settings-audit]` — 当前不是权限审计任务

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 8 | Blocked By: none

  **References**:
  - Pattern: `frontend/package.json` — 现有 build/test/check:copy 脚本
  - Pattern: `frontend/vite.config.ts` — 前端测试环境配置
  - Pattern: `frontend/tsconfig.json` — 有 TS 配置但无 typecheck script
  - Pattern: `backend/pom.xml` — Spring Boot test/build 能力
  - Pattern: `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java` — 后端代表性测试
  - Pattern: `nlp-service/tests/test_analyze.py` — NLP 代表性测试
  - Pattern: `README.md` — 已记录 test/build 命令

  **Acceptance Criteria**:
  - [ ] frontend 存在显式 typecheck 命令并纳入自动化流程
  - [ ] CI 文件存在并自动执行 frontend/backend/nlp 核心验证
  - [ ] 本地与 CI 的校验命令保持一致

  **QA Scenarios**:
  ```
  Scenario: Quality pipeline pass
    Tool: Bash
    Steps: Run npm --prefix frontend test; npm --prefix frontend run build; mvn -f backend/pom.xml test; python -m pytest nlp-service/tests -q.
    Expected: All commands exit 0 and produce reproducible pass output.
    Evidence: .sisyphus/evidence/task-4-quality-pipeline.txt

  Scenario: CI enforcement check
    Tool: Bash
    Steps: Inspect CI config and confirm it runs the same validation commands as local workflow.
    Expected: CI exists and mirrors the documented local verification path.
    Evidence: .sisyphus/evidence/task-4-ci-check.md
  ```

  **Commit**: YES | Message: `chore(ci): add minimal repeatable quality gate` | Files: `frontend/package.json`, `.github/workflows/**`, `README.md`

- [ ] 5. 重构首页与核心看板：从“模块拼盘”变成“单线决策画布”

  **What to do**: 以一个管理者最自然的问题链重组 UI：当前最严重的问题是什么、它影响谁、建议先做什么、做完后如何验证。将概览、趋势、词云、行动、验证等模块重组为单线叙事，而不是平铺模块导航。
  **Must NOT do**: 不增加更多图表种类；不为了“看起来丰富”牺牲信息层次。

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: 该任务核心是信息层级与可视化叙事重构
  - Skills: `[]` — 不需要额外技能
  - Omitted: `[blog-writing-guide]` — 不是长文内容创作

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 8 | Blocked By: 1, 2

  **References**:
  - Pattern: `frontend/src/App.vue` — 当前页面结构与模块组织
  - Pattern: `frontend/src/components/LoginGate.vue` — 进入体验与 demo 登录门槛
  - Pattern: `frontend/src/test/App.spec.ts` — 现有 UI 行为测试样式
  - Pattern: `docs/项目初始设计文档.md` — 原始用户操作路径
  - External: `README.md` — 当前展示/使用流程

  **Acceptance Criteria**:
  - [ ] 首屏能在一个连续视图内回答“问题/影响/行动/验证”四个问题
  - [ ] 不需要用户在多个平级模块间自行拼接故事
  - [ ] 视觉重点优先突出问题真实性和建议动作，不是底层技术过程

  **QA Scenarios**:
  ```
  Scenario: Boss walkthrough
    Tool: Playwright
    Steps: Open the app, complete the demo login if required, and follow the main dashboard from top to bottom.
    Expected: A single uninterrupted flow surfaces top issue, evidence, recommended action, and validation outcome without cross-module hunting.
    Evidence: .sisyphus/evidence/task-5-boss-walkthrough.png

  Scenario: Narrative fragmentation check
    Tool: Playwright
    Steps: Count required navigation jumps to understand one issue from symptom to action to result.
    Expected: The core decision story is understandable in one view or one guided path, not scattered across unrelated tabs.
    Evidence: .sisyphus/evidence/task-5-fragmentation.md
  ```

  **Commit**: YES | Message: `feat(ui): reshape dashboard into decision canvas` | Files: `frontend/src/App.vue`, `frontend/src/components/**`, `frontend/src/test/App.spec.ts`

- [ ] 6. 强化“真实情况可见性”：把漂亮图表改成可信证据图层

  **What to do**: 调整趋势图、词云、问题列表等视觉组件的表达重点：不仅展示“有问题”，还要展示问题来自哪里、置信度如何、是否基于真实样本、有哪些限制。目标是避免图像带来虚假确定感。
  **Must NOT do**: 不用炫酷图形掩盖低样本、偏差或占位数据；不输出没有上下文的热词。

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: 这是可视化语义与可信表达的调整
  - Skills: `[]` — 无需额外技能
  - Omitted: `[brand-guidelines]` — 当前重点是证据透明，不是品牌口吻

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 8 | Blocked By: 1, 2

  **References**:
  - Pattern: `frontend/src/test/TrendList.spec.ts` — 趋势列表组件测试模式
  - Pattern: `frontend/src/test/WordCloudPanel.spec.ts` — 词云面板测试模式
  - Pattern: `frontend/src/api/client.ts` — 可视化数据来源与 fallback 风险
  - Pattern: `docs/接口文档.md` — 数据真实度与占位说明的证据边界

  **Acceptance Criteria**:
  - [ ] 每个核心可视化都能说明来源、范围或限制条件
  - [ ] 低样本/占位/预览数据不会被展示成“确定事实”
  - [ ] 图像表达优先服务“真实情况可见”，而不是“图表丰富”

  **QA Scenarios**:
  ```
  Scenario: Evidence-aware visualization check
    Tool: Playwright
    Steps: Inspect trend and word-cloud related views using demo data.
    Expected: Visuals expose source/coverage/preview-state messaging whenever certainty is limited.
    Evidence: .sisyphus/evidence/task-6-visual-trust.png

  Scenario: Low-confidence handling
    Tool: Bash
    Steps: Feed low-volume or placeholder-backed data through component tests or mocked API responses.
    Expected: UI degrades gracefully with caveat messaging instead of authoritative-looking charts.
    Evidence: .sisyphus/evidence/task-6-low-confidence.txt
  ```

  **Commit**: YES | Message: `feat(ui): make visual evidence trustworthy` | Files: `frontend/src/components/**`, `frontend/src/test/TrendList.spec.ts`, `frontend/src/test/WordCloudPanel.spec.ts`

- [ ] 7. 打通“问题 → 行动 → 验证”闭环，证明项目不是只会看图

  **What to do**: 以一条真实样例贯通：从评论识别问题、登记改进行动、再查看验证结果。把当前分散的 insight/action/validation 能力连接成一个可重复演示且可解释的最小闭环。
  **Must NOT do**: 不伪造验证结果；不绕过 action registration 直接展示“成功故事”。

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: 需要跨服务串联业务闭环
  - Skills: `[]` — 可基于现有代码实现
  - Omitted: `[find-bugs]` — 当前重点是闭环打通而不是独立找缺陷

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 8 | Blocked By: 2, 3

  **References**:
  - Pattern: `infra/db/init/001_init.sql` — issue/action/validation 数据关系
  - Pattern: `backend/src/main/java/com/wh/review/backend/service/` — insight、action、validation 逻辑所在
  - Pattern: `frontend/src/api/client.ts` — 前端对 action / validation 的调用路径
  - Pattern: `docs/项目初始设计文档.md` — 闭环目标的原始设计意图
  - Test: `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java` — API 级联验证模式

  **Acceptance Criteria**:
  - [ ] 至少一条样例数据可完成“发现问题 → 提交行动 → 查看验证结果”全路径
  - [ ] 每一步都能追溯输入、输出与业务含义
  - [ ] 演示中不需要人工解释“这里其实还没接通”

  **QA Scenarios**:
  ```
  Scenario: End-to-end decision loop
    Tool: Playwright
    Steps: Launch the stack, log in, inspect one issue, register an improvement action, and open its validation result.
    Expected: The loop completes with consistent issue/action/validation data and no placeholder-only step.
    Evidence: .sisyphus/evidence/task-7-decision-loop.png

  Scenario: Broken-loop prevention
    Tool: Bash
    Steps: Call the relevant backend endpoints for issue details, action creation, and validation retrieval using the same sample ID.
    Expected: IDs, status transitions, and displayed summaries remain consistent across the flow.
    Evidence: .sisyphus/evidence/task-7-api-loop.txt
  ```

  **Commit**: YES | Message: `feat(flow): connect issue-action-validation loop` | Files: `frontend/src/**`, `backend/src/main/java/com/wh/review/backend/**`, `infra/db/init/001_init.sql`

- [ ] 8. 固化“老板演示模式”：一键进入、稳定复现、结果可截图留档

  **What to do**: 把 demo 登录、样例数据准备、核心路径、截图留档、故障回退串成一个固定演示模式。目标是让这个项目的价值每次都能稳定被看见，而不是依赖临场发挥。
  **Must NOT do**: 不把所有高级功能都塞进演示；不要求现场手工修正数据或状态。

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: 需要整合 UX、样例数据、验证与交付稳定性
  - Skills: `[]` — 不依赖特定技能
  - Omitted: `[create-pr]` — 当前不是提交评审阶段

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: Final Verification Wave | Blocked By: 4, 5, 6, 7

  **References**:
  - Pattern: `frontend/src/components/LoginGate.vue` — 当前 demo 登录方式
  - Pattern: `README.md` — 本地启动与使用步骤
  - Pattern: `docker-compose.yml` — 本地多服务演示拓扑
  - Pattern: `docker-compose.prod.yml` — 对外展示部署形态
  - Pattern: `frontend/src/test/App.spec.ts` — UI 主流程测试入口

  **Acceptance Criteria**:
  - [ ] 有固定演示入口与标准样例路径
  - [ ] 演示完成后能自动生成截图/文本证据归档
  - [ ] 演示失败时有明确回退路径，不影响核心价值展示

  **QA Scenarios**:
  ```
  Scenario: Repeatable boss demo
    Tool: Playwright
    Steps: Start the application, execute the documented login and core walkthrough, and capture final screenshots.
    Expected: The same story can be replayed end to end without manual patching or hidden setup.
    Evidence: .sisyphus/evidence/task-8-repeatable-demo.png

  Scenario: Recovery path check
    Tool: Bash
    Steps: Simulate one service being unavailable or one non-core module failing during demo startup.
    Expected: The documented fallback still preserves the core decision-support story.
    Evidence: .sisyphus/evidence/task-8-recovery.md
  ```

  **Commit**: YES | Message: `chore(demo): codify repeatable boss walkthrough` | Files: `README.md`, `frontend/src/**`, `docker-compose.yml`, `docker-compose.prod.yml`

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [ ] F1. Plan Compliance Audit — oracle
- [ ] F2. Code Quality Review — unspecified-high
- [ ] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [ ] F4. Scope Fidelity Check — deep

## Commit Strategy
- One objective per commit.
- 测试与实现同任务提交，不拆成“先写代码后补验证”。
- 非必要不做跨服务大提交；只有一个用户可感知行为必须跨服务时，才允许 frontend/backend/nlp 同一提交。
- 所有面向老板的表述调整，必须与真实实现状态同步提交，避免“文案先超前”。

## Success Criteria
- 老板在一次演示中可以直观看到：当前最重要的用户问题是什么、为什么值得优先处理、建议先做什么、做完后怎么验证。
- 系统展示出的结论具备可信来源，不会因为占位数据、口径冲突或临场故障而失真。
- 工程质量提升到“可重复演示、可持续迭代”的最低可信水平，而不是继续停留在“这次刚好能跑”。
