> generated_by: nexus-mapper v2
> verified_at: 2026-06-04
> provenance: Manual domain extraction from README, docs, Java services, NLP code, SQL schema, and tests.

# Domain Concepts

## Product

`products` 表中的商品。当前默认商品编码是 `jd-100127936932`。`productCode` 是数据库查询键，不适合当用户主展示名；`productName` 来自爬虫 CLI 输入、JSONL 内容或前端数据接入页手填。注意：`POST /api/v1/reviews/clean-jsonl` 只做文件清洗，不写 `products.product_name`；商品信息要在后续数据库导入步骤才进入 `products`。竞品对比不再在前端配置竞品属性，只输入主商品和竞品的 `productCode`；两者都必须已在数据库中存在并完成分析物化。如果两个商品绑定的 taxonomy 既不是同一个 `taxonomyId`，也不是结构等价的标签体系，后端返回 `taxonomy-mismatch`。

## Review

系统现在区分三层评论数据：

- raw JSONL：终端 Python 爬虫或 CSV 转换脚本负责生成原始评论并写入 `crawler/output/raw_reviews_<productCode>.jsonl`，每条评论可带 `productName`，不判断情感和 UX 标签。
- cleaned JSONL：清洗阶段去除空内容、非法 JSON、HTML 噪声、平台占位评价和精确重复；如果内容是“此用户未及时填写评价内容 | [追评]: ...”，只剥掉占位前缀并保留真实追评。`POST /api/v1/reviews/clean-jsonl` 输出 `cleaned_reviews_<productCode>.jsonl`、`removed_reviews_<productCode>.jsonl`、`cleaning_summary_<productCode>.json` 和 sample，摘要含 `placeholderContentCount`，handoff 为 `READY_FOR_TAXONOMY_BINDING`。这个步骤不写数据库。
- `reviews_raw`：清洗后的评论导入数据库后进入分析主链路。当前前端显式导入数据库时优先使用 `cleanedOutputPath`，后端收到 `cleaned_reviews_*.jsonl` 时直接读取 cleaned 文件和同目录 summary 入库，不再重复清洗 raw JSONL；但启动 LLM 分析时如果数据库已经有该商品评论，前端会复用现有 `reviews_raw`，不会为了启动分析再次覆盖导入 cleaned JSONL。

当前代码没有找到 `POST /api/v1/demo-data/init` controller。真实外部评论同步和导入入口已经存在，当前前端通过 `GET /api/v1/reviews/jsonl-files` 自动识别 `crawler/output` 下的 raw JSONL，并把候选选择、手动路径兜底、商品名称、raw/cleaned JSONL 文件反馈、已保存 taxonomy 选择与绑定、数据库导入和异步分析启动放在 `ProductSetupPanel` 中。UX 标签编辑不再挤在数据接入页内，而是在左侧同级业务模块 `taxonomy` 中完成。前端主流程不再负责启动爬虫。

刷新页面后的进度恢复由 `GET /api/v1/reviews/intake-status` 提供。该接口按 `productCode` 和可选 `inputPath` 汇总 raw/cleaned 文件是否存在、清洗摘要、taxonomy 是否已绑定、数据库已导入评论数、已物化分析评论数、最新 LLM analysis job，以及默认最近 10 条评论。前端数据接入页优先用这个台账显示每一步状态，而不是只靠按钮亮/灭或本次点击返回值。

## Taxonomy Binding

UX 标签不由爬虫或前端自动决定。左侧独立 `taxonomy` 模块负责编辑和保存可复用 taxonomy，包括 taxonomy 名称、商品品类、UX 一级标签和 UX 二级标签。保存成功后，这套 taxonomy 会出现在数据接入页“商品品类”下拉框中；数据接入页只负责选择一个已保存 taxonomy、只读预览标签树，并绑定到当前商品，不再提供内联 UX 标签编辑。后端在同步 `POST /api/v1/analysis/start` 或异步 `POST /api/v1/analysis/jobs` 分析时读取当前 `productCode` 绑定的 taxonomy，把它作为 NLP/规则分析的候选标签体系。`clean-jsonl` 完成后返回 `READY_FOR_TAXONOMY_BINDING`，表示下一步应先绑定 taxonomy；分析后如果更换 taxonomy，需要重新分析。

## Aspect

评论方面，也就是评论在说哪类问题。当前 canonical aspects:

- `battery`
- `bluetooth`
- `noise-canceling`
- `comfort`
- `microphone`

后端和 NLP 都有方面归一化。NLP 默认要求 LLM 输出结构化 JSON；显式规则模式才用关键词匹配。NLP 现在会容忍 LLM 在 JSON 前输出 `<think>` 或说明文字，并从正文里抽取最后一个合法 JSON 数组/`results` 对象；如果 LLM 把明显命中 taxonomy 同义词的正面评论标成“无明显问题”，会保守找回 UX 二级标签，避免正向卖点空掉。后端在普通 NLP 不可用时可用受控规则回退，但缺 LLM 配置属于 fatal，不会物化假结果。

## Sentiment

评论情感极性：`NEGATIVE`、`NEUTRAL`、`POSITIVE`。NLP 默认由 LLM 判断；显式规则模式用正负关键词判断；后端受控分析会根据评分或规则转换为极性和分数。

## Analysis Job

分析任务现在有两个入口：旧 `POST /api/v1/analysis/start` 保留同步执行，用于保护原 MVP；新 `POST /api/v1/analysis/jobs` 是异步入口，会立即返回 `QUEUED`，前端再通过 `GET /api/v1/analysis/jobs/{id}` 轮询 `QUEUED/RUNNING/SUCCEEDED/FAILED`。`AnalysisJobService` 开始执行时先清空该商品旧物化输出，再按 10 条评论一批调用 NLP/LLM，并把 `total_review_count`、`processed_review_count`、`progress_percent`、`current_stage` 写入 `analysis_jobs`，所以前端进度条显示的是后端真实处理进度，不是前端动画。每批 LLM 返回后，后端会立即追加写入该批 `review_aspects` 和 `review_semantic_labels`，写入前会校验这些 `review_id` 仍存在且仍属于当前商品，避免分析期间重新导入 cleaned JSONL 后旧评论 ID 被删除造成外键错误；如果发现数据已经被替换，任务会失败并提示“评论数据在 LLM 分析过程中被重新导入或替换，请重新启动 LLM 分析”。写入成功后会用当前累计已分析评论重建 `issue_clusters` 和 `issue_scores`，再把 `materialized_review_count`、`semantic_label_count`、`issue_cluster_count`、`downstream_ready` 写回 `analysis_jobs`；前端看到物化条数增长就刷新问题、卖点、趋势和词云，不需要等全部评论都分析完成。如果分析结束但物化结果为空，会标记失败或下游未就绪，避免前端显示“完成”但图表无数据。NLP 服务不可用或响应合同异常时，后端仍可降级到受控规则分析并返回 `SUCCEEDED`，`errorMessage` 带 `degraded:*`，用于保护演示链路；但 NLP 明确返回 `llm_config_missing` 或 `llm_analysis_failed` 且 `NLP_ALLOW_LLM_FALLBACK=false` 时是 fatal，后端会 `FAILED` 并跳过物化，避免没填 LLM key 时产生假语义分析结果。本地验收可设置 `NLP_ALLOW_LLM_FALLBACK=true`，表示 NLP 优先尝试真实 LLM，外部模型限流/连接/格式失败时再使用本地规则兜底。分析阶段才输出 `uxPrimaryLabel`、`uxSecondaryLabel`、标准原因、证据和置信度。

## Crawl Job

采集任务由 `POST /api/v1/crawl/start` 创建，后端调用 Python `crawler.service`。`GET /api/v1/crawl/jobs/{id}` 返回任务状态、`outputPath`、`progressPath`、抓包数量、采集评论数和最多 3 条 JSONL 样本。采集完成后，`POST /api/v1/crawl/jobs/{id}/import` 由后端读取 raw JSONL，执行清洗，导入 `reviews_raw`，保存 `data_quality_runs`，并返回 cleaned/removed/summary 文件路径。该链路保留兼容，但当前前端主入口改为选择后端识别到的本地 raw JSONL 或手动填写 JSONL 路径。命令行采集脚本 `crawler/jd_reviews.py` 已改成交互式 CLI，会逐步提示商品链接、`productCode`、`productName`、品类、采集数量和输出路径。

## Manual JSONL Clean and Import

现成 CSV 或终端爬虫结果进入系统时，推荐先转成 `crawler/output/raw_reviews_<productCode>.jsonl`。后端 `GET /api/v1/reviews/jsonl-files` 会扫描固定 `crawler/output` 根目录并返回候选文件、商品编码、商品名、大小、修改时间和样本评论；前端 `ProductSetupPanel` 可选择候选或手动填写路径。

当前主流程分四步：

1. `POST /api/v1/reviews/clean-jsonl`：只读取 raw JSONL，执行清洗、去重、占位评价处理，写入 `crawler/output/cleaned/`，返回 cleaned/removed/summary/sample 和 handoff `READY_FOR_TAXONOMY_BINDING`，不写 `products`、`reviews_raw`、`sync_jobs`、`data_quality_runs`。
2. 绑定 taxonomy：先在左侧 `taxonomy` 模块维护并保存标签体系，再回到数据接入页从“商品品类”下拉框选择已保存 taxonomy，预览后绑定到当前商品。
3. 导入数据库：用户执行导入动作时，前端只有在 cleaned 文件实际存在时才把 `cleanedOutputPath` 传给 `POST /api/v1/reviews/import-jsonl`；此时请求会带 `replaceExisting=true`。后端会先检查同商品是否有 `QUEUED`/`RUNNING` 的 LLM 分析任务；如果有，会拒绝覆盖导入，防止把正在分析任务持有的旧 `reviews_raw.id` 删除；如果没有，才清空该商品旧物化输出并删除旧本地 JSONL 评论，再直接读取 cleaned JSONL 并复用同目录 cleaning summary，形成新的 `products` 和 `reviews_raw`。如果用户跳过清洗直接传 raw JSONL，该接口仍兼容旧流程，会先清洗再入库。
4. `POST /api/v1/analysis/jobs`：启动异步 LLM 分析；如果 `intake-status.importedReviewCount > 0`，前端直接复用现有入库评论，不再自动覆盖导入 cleaned JSONL。之后用 `GET /api/v1/analysis/jobs/{id}` 轮询；每批 LLM 结果写入数据库后都会更新物化计数，前端看到 `downstreamReady=true` 或物化计数增长时就刷新下游图表。

贯穿四步的状态接口是 `GET /api/v1/reviews/intake-status`：前端加载、清洗、绑定、导入、启动分析和轮询期间都会刷新它。它让用户看到每一步的数据情况：文件路径、文件是否存在、清洗数量、入库数量、已分析数量、`downstreamReady`、最新任务状态，以及最近评论列表（默认 10 条）。如果刷新页面后数据库已有评论，前端可复用该台账继续启动 LLM 分析；只有用户明确执行导入数据库动作时，才会用 cleaned JSONL 覆盖旧评论，避免下游图表继续读未清洗数据。

Docker Compose 后端通过 `./crawler/output:/app/crawler/output` 挂载读取宿主机生成的 JSONL。

## Materialization

物化输出是当前查询主链路。同步/一次性分析可用 `AnalysisMaterializationRepository.replaceOutputs(...)` 覆盖写入；异步 LLM 分析会在任务开始时清空旧输出，并在每批结果返回后用 `appendOutputs(...)` 追加评论级结果、重建当前累计问题簇。`replaceOutputs(...)` 和 `appendOutputs(...)` 写入前都会校验输出中的评论 ID 是否仍属于当前商品，避免旧任务把语义标签写到已删除或已替换的评论上:

- `review_aspects`
- `review_semantic_labels`
- `issue_clusters`
- `issue_scores`

问题列表、正向卖点、竞品对比、趋势、词云、前后对比都读取这些物化结果。趋势依赖 `review_aspects`，正向卖点依赖 `review_semantic_labels`；词云从 `review_aspects` 取得情绪和 UX 标签过滤，同时左连接 `review_semantic_labels`，优先从 `standardized_reason` 和 `evidence` 提取 VOC 词，缺失时才回退原评论正文。所以当前 `downstreamReady` 要求这两类结果都已写入，问题簇可以为 0，因为全正向评论确实可能没有负面问题。趋势图前端会读取当前商品绑定的 taxonomy，把启用的 UX 二级标签逐个传给 `/api/v1/trends` 的 `uxSecondaryLabel` 参数；后端按 `review_aspects.ux_secondary_label` 聚合周维度负面率和提及量，前端再把所有维度画在一个多折线面板中。词云现在每个词项带 `partOfSpeech` 和 `wordType`，用于展示词性和业务词类；后端按负向痛点和正向认可各取一部分，避免正评高频词淹没负面问题词，并过滤品牌名、型号词、商品对象名和平台模板字段。前后对比还会把 checkpoint 写入 `ux_change_checkpoints`，实际对比明细仍从 `review_aspects` 和 `reviews_raw` 按时间窗口重新统计。

## Trend

趋势图展示“各 UX 二级标签在不同时间周期里的负面率变化”。前端不再固定请求 `battery`，而是从 `fetchProductTaxonomy(productCode)` 得到当前商品启用的 UX 二级标签，逐个调用 `fetchTrends(productCode, inferredAspect, uxSecondaryLabel)`。`inferredAspect` 只用于兼容旧字段和显示兜底，真实过滤条件是 `uxSecondaryLabel`。`TrendList` 把返回的多组 `TrendPoint` 画在同一个 SVG 面板，用不同颜色区分；点击图例或折线点会高亮该维度，并在右侧显示当前周期、负面率、提及量和较上一期变化。

## Issue

问题簇来自负向评论聚合。优先级评分由固定权重计算：

- negativeRate: 0.35
- mentionVolume: 0.25
- trendGrowth: 0.20
- competitorGap: 0.20

答辩时应说明这不是大模型或复杂机器学习，而是可解释的规则评分。

## Competitor Compare

竞品对比由 `GET /api/v1/compare` 提供。输入是主商品 `productCode` 和竞品 `comparisonProductCode`。响应会带 `productName` 和 `comparisonProductName` 供前端显示真实商品名。后端算法不是前端拼数据，而是：

- 检查主商品是否有物化分析输出。
- 检查竞品是否有物化分析输出。
- 检查两个商品绑定的 taxonomy 是否兼容：同一个 `taxonomyId` 直接通过；不同 ID 但商品品类、一级/二级标签、启用状态、同义词和描述完全一致，也视为同一套标签体系。
- 从物化方面数据中按 aspect/UX 标签聚合提及量、负向量、平均情感分。
- 输出双方得分、提及量、负向率和差距。

如果 taxonomy 不兼容，返回 `taxonomy-mismatch`；如果任一商品没有分析结果，返回对应空状态。

## UX Change Comparison

前后对比由 `POST/GET /api/v1/ux-change-comparisons` 提供。用户只选择商品、checkpoint 时间点和窗口预设，不需要记录“改了哪个 UX 标签”。

后端算法：

- 创建 checkpoint，默认窗口是 `ONE_MONTH`，即 checkpoint 前一个月和后一个月。
- 也支持 `TWO_WEEKS`、`THREE_MONTHS` 和 `CUSTOM` 天数窗口。
- 读取窗口内的 `review_aspects` 和 `reviews_raw.review_time`。
- 按 UX 二级标签聚合 before/after 评论数、负向评论数、负向率。
- 输出每个 UX 二级标签的负向率变化、提及量变化和样本数。

## Backend-Only Legacy Capabilities

`Action`、`Validation` 和 `Showcase` 后端代码仍存在，用于兼容历史链路和测试，但当前前端主导航已经移除这些组件。后续讲系统主功能时，不应把它们作为当前主要用户界面能力。
