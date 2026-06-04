> generated_by: nexus-mapper v2
> verified_at: 2026-06-04
> provenance: AST-backed for Java/TypeScript/Python; Vue and SQL details partly inferred from manual inspection and tests.

# Systems

## 1. Frontend Console

- code_path: `frontend/`
- entry: `frontend/src/main.ts`, `frontend/src/App.vue`
- tech: Vue 3, TypeScript, Vite, Axios, Vitest, @antv/g2plot, GSAP
- responsibility: 单页验收控制台，负责内部访问门禁、左侧模块导航、加载状态、图表/列表展示和 API 响应归一化。当前前端主导航只保留：数据接入、taxonomy、问题、卖点、竞品分析/对比、趋势图、词云、前后对比。独立 `taxonomy` 模块通过 `TaxonomyManagerPanel` 编辑和保存可复用 taxonomy；保存后，数据接入页 taxonomy 下拉框会出现这套 taxonomy。数据接入页会调用后端自动识别 `crawler/output` 下的 raw JSONL 候选，支持商品名称输入/回填和手动路径兜底；`ProductSetupPanel` 已拆成 `JSONL`、`taxonomy`、`导入分析`、`状态评论` 四个页内步骤，父面板保持 `overflow: hidden`，长列表只在局部 `scroll-region` 中滚动；顶部选择控件通过 `GET /api/v1/taxonomies` 读取已有 taxonomy 并以下拉框选择，不再手动输入品类；该接口只返回每个 taxonomy 系列的当前 active 版本，默认 taxonomy 只保留一个 `general-product` 当前项，前端在接口失败或 taxonomy 空标签时不会伪造通用 UX 草稿；`taxonomy` 步骤通过只读 `UxTaxonomyTree` 预览当前 taxonomy，并用“绑定当前 taxonomy”按钮完成商品绑定，不再在数据接入页内编辑 UX 标签；数据接入页还通过 `GET /api/v1/reviews/imported-products` 读取数据库已导入商品历史，用户选择并点击展示后会发出 `product-selected`，`App.vue` 更新当前展示商品、清空旧对比结果，并按新 `productCode` 刷新问题、卖点、趋势、词云和前后对比。`App.vue` 顶部固定显示当前展示商品名/商品编号；如果商品名仍等于编号，会提示商品名称未识别。数据库导入动作只有在 cleaned 文件实际存在时才使用 `cleanedOutputPath`，并通过 `replaceExisting=true` 覆盖旧 `reviews_raw` 和旧物化结果；启动 LLM 分析时如果 `intake-status` 显示该商品已有入库评论，则复用现有 `reviews_raw`，不会再次覆盖导入 cleaned JSONL；页面加载和每步操作后通过 `fetchReviewIntakeStatus` 读取后端流程台账，展示 raw/cleaned 文件、清洗摘要、taxonomy 状态、入库/已分析数量、`downstreamReady`、最新 LLM job 和默认最近 10 条评论；异步分析通过 `fetchAnalysisJob` 轮询 `QUEUED/RUNNING/SUCCEEDED/FAILED`，进度条优先使用后端持久化的 `processedReviewCount/totalReviewCount/progressPercent/currentStage`，并在 `downstreamReady=true` 或物化计数增长时触发问题、卖点、趋势和词云刷新；问题、卖点、趋势和词云模块如果之前停在空态/错误态，重新进入模块时会重新请求后端。趋势图会先读取当前商品绑定的 taxonomy，把启用的 UX 二级标签分别传给 `GET /api/v1/trends?uxSecondaryLabel=...`，再在同一面板画成多条不同颜色折线；点击图例或折线点会高亮对应维度并更新右侧详情。`client.ts` 在空 `VITE_API_BASE_URL` 时会从当前页面 `protocol/hostname` 推导后端 `:8080`，避免 `localhost` 和 `127.0.0.1`/局域网访问混用导致请求失败。对比和前后对比优先显示商品名，商品编号只作为查询键/辅助信息。词云展示关键词词频、情绪、词性和业务词类；后端返回项已按负向痛点与正向认可做配额分桶，并过滤品牌/型号/平台字段噪声。全局视觉已切到纯白极简风格。
- key files:
  - `frontend/src/api/client.ts`: 所有后端接口的 Axios 封装、超时处理、状态归一化，包含 taxonomy 列表读取 `fetchTaxonomies`、独立 taxonomy 保存 `saveTaxonomyDefinition`、商品绑定 taxonomy `bindProductTaxonomy`、商品绑定 taxonomy 读取 `fetchProductTaxonomy`、接入台账查询 `fetchReviewIntakeStatus`、数据库已导入商品历史读取 `fetchImportedProducts`、异步分析任务查询 `fetchAnalysisJob`，以及空 `VITE_API_BASE_URL` 时基于当前页面主机推导后端地址的 `resolveApiBaseURL`。`fetchTrends` 支持把 `uxSecondaryLabel` 作为查询参数传给后端，用于多维趋势图逐标签请求。
  - `frontend/src/types/domain.ts`: 前端领域类型。
  - `frontend/src/components/TaxonomyManagerPanel.vue`: 左侧独立 taxonomy 业务模块，负责读取已保存 taxonomy、新建/编辑 taxonomy 名称、商品品类和 UX 一级/二级标签，并保存为数据接入页可选择的 taxonomy。
  - `frontend/src/components/ProductSetupPanel.vue`: 终端采集/CSV 转换后的 raw JSONL 候选选择、路径输入兜底、商品名称输入/回填、raw JSONL 清洗、数据库已导入商品历史选择、已有 taxonomy 下拉选择、只读 taxonomy 预览、商品 taxonomy 绑定、数据库导入、异步 LLM 分析启动、后端台账刷新、真实批次进度和最近 10 条评论展示；分析轮询期间如果发现物化评论数增长，会立即通知外层刷新问题、卖点、趋势和词云；选择历史商品时会通知外层切换当前展示商品；当前使用页内四步导航减轻单页压力。
  - `frontend/src/components/UxTaxonomyTree.vue`: 可复用 UX taxonomy 树状/思维导图组件，按一级/二级标签展示；在 taxonomy 模块内可编辑，在数据接入页以 readonly 模式只读预览。
  - `frontend/src/components/IssueTable.vue`: 后端问题簇展示。
  - `frontend/src/components/PositiveInsightPanel.vue`: 后端正向卖点展示。
  - `frontend/src/components/CompareTable.vue`: 输入主商品和竞品 productCode 后展示后端竞品对比。
  - `frontend/src/components/TrendList.vue`: 多维趋势图展示；同一 SVG 内按 UX 二级标签绘制多条负面率趋势折线，图例和点位点击会切换高亮维度与右侧详情。
  - `frontend/src/components/WordCloudPanel.vue`: 后端词云数据展示；前端继续消费同一 `WordCloudItem` 合同，正负向均衡和噪声过滤由后端完成。
  - `frontend/src/components/UxChangeComparisonPanel.vue`: 创建/查看后端前后时间窗口对比。
- status: implemented。
- evidence gap: Vue 文件只做 module-only AST 覆盖，组件内部细节来自人工阅读和 Vitest 结果；in-app Browser 访问本地页面被企业安全策略阻断，页面点击验收仍需人工或允许的浏览器环境补做。

## 2. Backend API

- code_path: `backend/`
- entry: `backend/src/main/java/review/backend/BackendApplication.java`
- tech: Spring Boot 3.3.3, Java 21, Spring Web, Spring JDBC, Spring Validation, PostgreSQL, H2 tests
- responsibility: 业务主入口，提供 `/api/v1/*` REST 接口、任务状态流转、真实评论导入、采集 JSONL 任务反馈、本地 raw JSONL 自动发现、raw JSONL 清洗、商品名持久化、NLP 调用、同步/异步分析物化、查询聚合、竞品 taxonomy 校验和前后时间窗口对比。当前代码没有找到 `/api/v1/demo-data/init` controller，验收主链路应使用 JSONL 清洗/导入/分析。`POST /api/v1/reviews/clean-jsonl` 只清洗 raw JSONL，输出 cleaned/removed/summary/sample，不写 `products`、`reviews_raw`、`sync_jobs`、`data_quality_runs`，并返回 handoff `READY_FOR_TAXONOMY_BINDING`。`GET /api/v1/reviews/intake-status` 汇总 raw/cleaned 文件存在性、清洗摘要、taxonomy 绑定、入库数量、已分析数量、`downstreamReady`、最新分析任务和最近 10 条评论，用于刷新后恢复每一步进度。`GET /api/v1/reviews/imported-products` 基于 `products`、`reviews_raw`、`review_aspects`、`review_semantic_labels`、`product_taxonomy_bindings` 和 `analysis_jobs` 聚合数据库已导入商品历史，返回商品名、商品编号、入库评论数、已分析评论数、是否下游就绪、是否绑定 taxonomy、最近分析状态和最近导入时间，供前端选择当前展示商品。`POST /api/v1/reviews/import-jsonl` 既兼容 raw JSONL 清洗导入，也支持直接导入 `cleaned_reviews_*.jsonl`；请求带 `replaceExisting=true` 时会先检查同商品是否存在 `QUEUED`/`RUNNING` 分析任务，若存在则拒绝覆盖导入，避免删除正在分析的 `reviews_raw`；若不存在，才清空旧物化输出、删除旧本地 JSONL 评论并导入 cleaned 评论。`POST /api/v1/analysis/jobs` 立即返回 `QUEUED`，前端通过 `GET /api/v1/analysis/jobs/{id}` 轮询；`AnalysisJobService` 开始执行时先清空该商品旧分析物化输出，然后按 10 条评论一批调用 NLP，每批 LLM 返回后立即追加写入 `review_aspects` 和 `review_semantic_labels`，写入前会校验该批 `review_id` 仍存在且仍属于当前商品；若分析期间评论被重新导入或替换，会把任务标记为失败并提示重新启动分析，而不是暴露数据库外键错误。每批成功写入后会基于当前累计评论重建 `issue_clusters`/`issue_scores`，同时把 `total_review_count`、`processed_review_count`、`progress_percent`、`current_stage`、`materialized_review_count`、`semantic_label_count`、`issue_cluster_count` 和 `downstream_ready` 持久化到 `analysis_jobs`。词云查询优先从 `review_semantic_labels.standardized_reason/evidence` 提取 VOC 词，回退到原评论正文，并按负向/正向分桶返回，过滤 `buds/pro/小米/手机/耳机` 等品牌型号或对象名。旧 `POST /api/v1/analysis/start` 保留同步执行。后端清洗会移除纯平台占位评价，并保留“占位前缀 + 追评”中的真实追评内容。NLP 明确返回缺 LLM 配置时，分析任务失败且不物化 fallback。动作、验证、showcase 后端仍保留，但不再是当前前端主导航能力。
- taxonomy note: `GET /api/v1/taxonomies` 会确保默认 taxonomy 存在，但只返回每个 taxonomy 系列的当前 active 版本；同名默认 taxonomy 的历史重复记录会被置为 inactive，默认候选统一走 `general-product`。`GET /api/v1/products/{productCode}/taxonomy` 只读取当前绑定或默认候选，不再因为读取动作偷偷写入 product binding；真正绑定仍由 `PUT /api/v1/products/{productCode}/taxonomy` 完成。
- controller modules:
  - `review/backend/api`: 健康检查、同步、采集、采集任务 JSONL 导入、本地 JSONL 发现/清洗/导入/接入状态、taxonomy、同步分析、异步分析任务、洞察、正向卖点、竞品对比、趋势、词云、前后对比，以及兼容保留的动作、验证和 showcase 接口。
  - `review/backend/api/dto`: 请求与响应 DTO。
- service modules:
  - `review/backend/application`: 分析任务、异步任务轮询、NLP 调用、真实评论规则回退、洞察查询、竞品对比、前后对比、showcase、同步、采集、JSONL 清洗/导入和 taxonomy 编排。
  - `review/backend/data`: 数据库读写、物化结果、前后对比 checkpoint、schema 初始化和任务仓储。
- status: implemented。

## 3. Crawler and Cleaning Pipeline

- code_path: `crawler/`, `pipeline/`
- entry: `crawler/service.py`, `crawler/jd_reviews.py`, `pipeline/clean_reviews.py`
- tech: Python, FastAPI, DrissionPage, pytest
- responsibility: `crawler/jd_reviews.py` 和 `crawler/service.py` 都保留合规浏览器辅助采集能力；当前前端主流程不再启动爬虫，而是让用户通过终端 CLI 采集或 CSV 转换得到 `crawler/output/raw_reviews_<productCode>.jsonl`。`crawler/jd_reviews.py` 的交互式 CLI 会提示商品链接、商品编号、商品名称、品类、采集数量和输出路径，并把 `productName` 写入每条 JSONL 评论。`pipeline/clean_reviews.py` 保留命令行清洗能力，支持 HTML/空白归一、去重、非法 JSON、空内容和平台占位评价处理；后端 `ReviewImportService` 也内置同类清洗逻辑。当前主流程中，后端清洗接口只生成 cleaned/removed/summary/sample，后续 taxonomy 绑定和数据库导入是独立步骤；数据库导入可直接消费 cleaned JSONL。
- status: implemented。
- compliance boundary: 不绕过登录、验证码、风控或反爬；出现验证时进入人工处理/等待状态。

## 4. NLP Service

- code_path: `nlp-service/`
- entry: `nlp-service/app/main.py`
- tech: Python, FastAPI, Pydantic, pytest
- responsibility: 提供 `/health` 和 `/analyze`。默认模式要求配置 OpenAI-compatible LLM key/model，`/analyze` 返回结构化情感、UX 标签、标准原因、证据和负向问题簇，并带 `analysisMode/llmUsed/fallbackReason` 来源字段。缺 key/model 时返回 `503 llm_config_missing`，不会自动生成规则 fallback；只有显式 `NLP_FORCE_LOCAL=true` 才启用规则演示模式。LLM 返回 `<think>...</think>`、前置说明或其它非 JSON 文本时，NLP 会扫描并抽取最后一个合法 JSON 数组/`results` 对象；如果 LLM 把有明显体验词的正向评论标为“无明显问题”，会用 taxonomy 同义词保守找回 UX 标签，避免卖点面板被误置空。
- method: 默认走 OpenAI-compatible LLM；显式规则模式使用关键词。
  - aspect keywords: battery, bluetooth, noise-canceling, comfort, microphone。
  - polarity markers: 负向词如“差、断开、噪音、卡顿、掉电”；正向词如“好、稳定、清晰、舒适、满意”。
  - cluster: 对负向 aspect/UX 标签计数并按 mentionCount 排序。
- status: implemented。

## 5. Data and Infra

- code_path: `infra/`, root `docker-compose.yml`, `docker-compose.prod.yml`
- tech: Docker Compose, PostgreSQL 16, Redis 7, SQL
- responsibility: 编排数据库、缓存、NLP、后端和前端；初始化核心表结构。
- key tables: `products`（`product_name` 用于前端展示真实商品名）、`reviews_raw`, `review_aspects`, `issue_clusters`, `issue_scores`, `ux_change_checkpoints`, `improvement_actions`, `validation_metrics`, `sync_jobs`, `analysis_jobs`, `data_quality_runs`（含 `placeholder_content_count`）。
- sync_jobs note: 采集任务仍会记录 `source_url`、`external_job_id`、`taxonomy_id`、`output_path`、`progress_path`、`captured_packet_count`。手动 JSONL 路径导入会创建 `local-jsonl` sync job，并返回 raw/cleaned/removed/summary 路径与样本。
- analysis_jobs note: 异步/同步分析任务持久化状态、起止时间、错误信息、taxonomy 版本、真实进度字段 `total_review_count`、`processed_review_count`、`progress_percent`、`current_stage`，以及物化结果字段 `materialized_review_count`、`semantic_label_count`、`issue_cluster_count`、`downstream_ready`。异步任务每完成一个 LLM 批次就会更新这些物化计数字段；前端不再把 `SUCCEEDED` 单独当作图表可读信号，必须确认 `downstream_ready` 或物化计数有效。
- status: implemented。
- evidence gap: SQL 文件只有 module-only AST 覆盖；表结构来自人工阅读。

## 6. Knowledge Map and Optional Docs

- code_path: `.nexus-map/`; optional `docs/`, root `README.md`
- responsibility: `.nexus-map/` 是后续 AI 默认读取和维护的项目知识库。`docs/` 和 `README.md` 只在用户明确要求报告、说明书或答辩材料时维护。
- key files:
  - `.nexus-map/INDEX.md`: 项目主口径和操作指南。
  - `.nexus-map/arch/*.md`: 系统边界、依赖和测试覆盖。
  - `.nexus-map/concepts/domains.md`: 核心领域概念。
- status: implemented。

## Non-main Directories

- `legacy/`: 旧版 wh-source 已在 2026-06-04 删除；当前仓库不再保留另一套前端/后端源码。
- `.tmp/`: 文档 QA、临时脚本和临时产物，不应进入答辩模块口径。
