# placeholder-features-improvement-plan

## Key Decisions

- **首发策略**: 先做受控数据闭环可用化；外部评论源接入放入同一计划第二轨，避免阻塞首发。
- **分析架构**: 固定为 **analysis job 产出物化结果，查询侧统一消费**，不再让 Java 即时聚合、NLP 输出、前端拼装并存。
- **showcase 收敛**: pipeline / agent / explainability / report / resilience 不再保留 placeholder，统一收敛为有限但真实的 v1 模块。
- **测试策略**: `tests-after`，但必须补齐前端、后端、NLP 和至少 1 条 backend↔NLP 集成验证。

## Scope

### IN
- 主闭环、analysis job、backend↔NLP 集成、真实 compare、actions/validation、总览与核心面板、showcase 替换、错误/降级语义、测试补齐、第二轨外部源骨架、文档收束

### OUT
- 全生产级多租户平台、完整自治 agent 系统、以外部源可用性阻塞首发、复杂 chaos 平台、重型鉴权体系

## Guardrails

- 先做 **占位清单治理**，每个 demo/placeholder 明确 replace / hide / delete。
- 必须定义每个展示模块的 **v1 边界**，防止“全面补齐”失控。
- 强制 **单一事实来源**，避免多套分析逻辑长期并存。
- 外部源能力只做第二轨，不得伪装成首发已完成。

## Defaults Applied

- 受控数据场景继续以当前蓝牙耳机演示域为首发验证域。
- external-source 采用 OneBound 现有结构做第二轨骨架，而非首发完成项。
- 不新增完整生产鉴权，仅建立最低 internal-use 基线。

## Implementation Plan

### Wave 1

- [x] 1. 固化 v1 领域契约与占位清单
  - 建立 module inventory，明确 core/showcase 各模块的 real / placeholder / gated / controlled-data-only 状态。
  - 统一 compare / validation / explainability / report / overview 的字段形状与语义描述。
  - 统一 canonical taxonomy naming，并明确 alias 到 canonical 的归一规则。
  - 明确 analysis result source：analysis job 负责物化，查询侧只消费结果。

- [ ] 2. 扩展 analysis job 为真实任务生命周期
  - 将 analysis job 从仅 `QUEUED` 扩展为 `QUEUED / RUNNING / SUCCEEDED / FAILED`。
  - 扩展 repository / service / controller 以支持完整生命周期与状态查询。
  - 设计可重试与幂等语义，保证受控数据分析可重复触发。
  - 为任务生命周期补齐后端测试与执行证据。

- [ ] 3. 打通 backend ↔ NLP 契约与 taxonomy 单一事实来源
  - 统一 Java backend 与 nlp-service 的 canonical aspect / issue taxonomy。
  - 实现 backend→NLP 请求/响应契约、超时、重试与结构化降级语义。
  - 覆盖成功、超时、错误三类 contract tests。
  - 证明 NLP 不可用时 schema 与降级语义仍稳定。

- [ ] 4. 建立 internal-use 首发基线与模块开关治理
  - 替换前端默认 demo 登录直入路径，改为 internal-use / env-gated 访问行为。
  - 将 backend CORS 收敛到环境驱动 allowed origins。
  - 为实验模块建立 feature flag 与显式 UI 状态。
  - 补齐配置与最小验证证据。

### Wave 2

- [ ] 5. 让 issues / trends / wordcloud 统一消费物化分析结果
  - 将 `InsightQueryService` 与相关 repository 改为消费最新 materialized analysis。
  - 保留无分析结果、空数据、小样本时的显式 fallback / degraded 语义。
  - 更新控制器与后端测试，确保查询结果稳定。
  - 产出物化查询成功与缺失分析路径的证据。

- [x] 6. 替换静态 compare 为真实受控数据对比
  - 为 compare 准备主商品 / 对比商品受控数据支持。
  - 实现负向率、提及量、issue delta 等真实 compare 计算。
  - 更新 compare API 与前端消费端测试。
  - 验证缺失对比目标时的结构化 empty / error 行为。

- [x] 7. 做实 actions → validation 闭环
  - 扩展 ActionController / Service / Repository 支持 action 列表与详情。
  - 用 action 关联的时间窗或快照语义替换现有 validation demo 逻辑。
  - 持久化 validation snapshot / metrics，并补齐 traceability 测试。
  - 提供 closed-loop action→validation 成功与无 action 空态证据。

### Wave 3

- [x] 8. 前端核心面板切换为真实契约消费
  - 重写 frontend api client 与 `App.vue` 总览流，消费真实 backend contracts。
  - 更新 Issue / Trend / WordCloud / Compare / Action / Validation 组件的 loading / empty / degraded / error 状态。
  - 移除依赖长期静态 mock 的消费逻辑。
  - 增加前端测试与至少一条 UI 级验证证据。

- [x] 9. 用真实 v1 数据替换 showcase placeholder
  - 替换 backend `ShowcaseService/Controller` 占位响应。
  - 让 pipeline / agent / explainability / report / resilience 面板渲染 API-backed v1 内容。
  - 为数据来源解释、失败语义、非-placeholder 行为补齐测试。
  - 提供 showcase 成功与降级场景验证。

- [x] 10. 统一错误、降级与禁用模块语义
  - 统一 backend 结构化 error / notice model。
  - 更新前端 client 与组件，区分 empty / degraded / failed / disabled。
  - 给 pipeline / resilience 模块喂入真实失败或降级状态源。
  - 产出跨层错误传播与前端状态分离证据。

### Wave 4

- [x] 11. 补齐测试矩阵
  - 扩展前端测试，覆盖真实 contracts 与 degraded states。
  - 扩展后端测试，覆盖 analysis jobs、compare、actions、validation、showcase replacements。
  - 扩展 nlp-service 测试，并至少提供一条 backend↔NLP integration verification。
  - 留存完整测试运行证据。

- [x] 12. 为外部评论源接入建立第二轨骨架
  - 形式化 external-source raw review 结构、去重规则、失败状态与同步透明度。
  - 更新 `SyncJobService` 与 `OneBoundReviewClient` 骨架，使其可衔接后续 analysis jobs。
  - 确保外部源失败不会阻塞 controlled-data 首发。
  - 产出 skeleton handoff 与 failure transparency 证据。

- [x] 13. 收束 README / 接口 / 代码文档
  - 更新 `README.md` 说明 controlled-data-first 首发路径与 analysis job 步骤。
  - 更新 `docs/接口文档.md` 反映真实 contracts 与 degraded/error semantics。
  - 更新 `docs/代码文档.md` 或 runbook，覆盖 explainability / report / resilience 使用与排障。
  - 做 docs-to-runtime consistency audit。

### Final Wave

- [x] F1. 运行 oracle 计划合规审查并记录 approve/reject verdict
- [x] F2. 运行 code-quality review 并记录 approve/reject verdict
- [x] F3. 运行真实手动 QA（含 Playwright UI 验证）并记录 approve/reject verdict
- [x] F4. 运行 scope-fidelity check 并记录 approve/reject verdict
