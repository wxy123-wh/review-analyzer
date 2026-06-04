# AGENTS.md

## 项目背景

本仓库是 `review-analyzer`，当前目标是从一个基于受控演示数据的蓝牙耳机评论分析 MVP，升级为“基于 LLM 的电商产品评论 VOC 分析与产品改进决策系统”。

用户当前处境非常特殊：

- 用户计算机基础较弱，无法独立理解复杂代码架构。
- 该项目主要通过 vibe-coding 生成，用户对系统真实结构、数据流和实现细节缺乏把握。
- 用户需要依靠 AI 逐步理解项目、补齐功能、准备报告和完成验收答辩。
- AI 不能只给抽象建议，必须把数据流、代码位置、技术选择、实现原因和答辩口径讲清楚。

所有 AI 进入本仓库后，都应以“教学型工程协作”方式工作：边实现、边解释、边提炼可用于报告或答辩的口径。只有用户明确要求写文档时，才把这些内容写入文档文件。

## 必读文件

开始任何任务前，只需要阅读 `.nexus-map` 中和任务相关的文件。默认最小集合为：

1. `.nexus-map/INDEX.md`
2. `.nexus-map/arch/systems.md`
3. `.nexus-map/arch/dependencies.md`
4. `.nexus-map/arch/test_coverage.md`
5. `.nexus-map/concepts/domains.md`
6. `.nexus-map/hotspots/git_forensics.md`

除非用户明确要求，不要把 `docs/` 或 `README.md` 作为默认上下文读取。
如果 `.nexus-map` 与当前代码冲突，以当前代码为准，并更新 `.nexus-map` 或在回复中明确指出差异。

## 当前系统认知

当前系统已有代码的主链路是：

```text
POST /api/v1/demo-data/init
  -> 生成受控演示评论
  -> 写入 reviews_raw

POST /api/v1/analysis/start
  -> 读取 reviews_raw
  -> 调用 nlp-service /analyze
  -> 或降级到后端规则分析
  -> 写入 review_aspects / issue_clusters / issue_scores

前端请求：
  GET /api/v1/issues
  GET /api/v1/compare
  GET /api/v1/trends
  GET /api/v1/wordcloud
  GET /api/v1/actions
  GET /api/v1/validation
  GET /api/v1/showcase/*
```

当前缺失的主链路是：

```text
Python 淘宝/京东评论采集
  -> raw_reviews.jsonl
  -> 数据清洗
  -> LLM 情感分析
  -> LLM 负面诊断
  -> LLM 正面卖点提炼
  -> structured_reviews.jsonl
  -> 后端导入
```

## 参考项目

用户希望借鉴两个公开仓库：

- `https://github.com/Huangirl710/Crawling-User-Reviews-from-JD.com`
- `https://github.com/Huangirl710/E-commerce-Product-Improvement-Decision-System`

第一个仓库可借鉴：

- DrissionPage / 浏览器自动化采集。
- 网络请求监听。
- 评论字段抽取。
- 去重。
- 断点续传。
- CSV/JSONL 输出。

第二个仓库可借鉴：

- 评论清洗。
- OpenAI-compatible LLM 调用。
- LLM 情感分析。
- 负面强度 1-5 分。
- UX 一级/二级标签。
- 标准原因映射。
- 正面卖点和 Aha Moment。
- 产品迭代优先级。
- 图表生成。
- 质量抽样评估。

不要直接照搬固定品类、固定提示词和固定数据库结构。应改造成适合本项目的通用电商评论 VOC 系统。

## 重要协作原则

### 1. 不要只说“前端/后端/NLP”

用户需要按业务功能理解系统。解释时优先使用数据流和功能流：

```text
数据采集 -> 数据清洗 -> LLM 分析 -> 后端入库 -> 指标计算 -> 前端展示 -> 改进验证
```

回答问题时要说明：

- 功能是什么。
- 输入是什么。
- 输出是什么。
- 代码在哪里。
- 使用了什么技术。
- 为什么选择这个技术。
- 前端如何展示。
- 后端如何计算。
- 当前是否已经实现。
- 如果没实现，应该补在哪里。

### 2. 每次修改都要保护现有 MVP

不要破坏已有链路：

- demo-data init
- analysis start
- issues
- compare
- trends
- wordcloud
- actions
- validation
- showcase

新增真实数据和 LLM 功能时，优先作为并行新链路接入，不要一开始就删除旧规则逻辑。

### 3. 爬虫必须合规

不得实现或鼓励：

- 绕过验证码。
- 绕过登录风控。
- 破解反爬。
- 高频恶意请求。

采集模块应描述为：

- 合规 API。
- 第三方数据服务。
- 公开可访问页面。
- 浏览器自动化辅助采集。
- 出现验证时暂停并提示人工处理。

### 4. LLM 输出必须结构化

LLM 不应返回散文。必须要求固定 JSON。

例如：

```json
{
  "sentiment": "NEGATIVE",
  "negativeIntensityScore": 3,
  "uxPrimaryLabel": "产品硬件",
  "uxSecondaryLabel": "连接与稳定性",
  "standardizedReason": "蓝牙断连",
  "confidence": 0.91,
  "evidence": "蓝牙偶尔断连"
}
```

后端或脚本必须校验枚举字段、置信度、缺失字段和异常输出。

### 5. 系统要逐步通用化

当前蓝牙耳机方面包括：

- battery
- bluetooth
- noise-canceling
- comfort
- microphone

后续不应继续硬编码这些方面。应引入：

- product category
- aspect taxonomy
- UX label taxonomy
- category-specific prompt

目标是支持不同电商商品。

## 推荐开发顺序

1. 保护现有测试和主链路。
2. 新增 `crawler/`，先支持读取或生成 `raw_reviews.jsonl`。
3. 新增 `pipeline/clean_reviews.py`。
4. 新增 `pipeline/llm_sentiment.py`。
5. 新增 `pipeline/llm_negative_diagnosis.py`。
6. 新增 `pipeline/llm_positive_insights.py`。
7. 新增后端 `POST /api/v1/reviews/import`。
8. 新增后端数据质量接口。
9. 新增前端数据质量面板。
10. 新增产品迭代优先级接口和展示。
11. 引入多品类配置。
12. 按用户要求补质量抽样评估和报告材料。

## 测试命令

前端：

```powershell
npm --prefix frontend test
```

NLP：

```powershell
python -m pytest nlp-service/tests -q
```

后端：

```powershell
mvn -f backend/pom.xml test
```

注意：当前机器曾出现 `mvn` 不在 PATH 的问题。如果后端测试跑不起来，先说明是环境问题，再尝试 Maven Wrapper、IDE Maven 或 Docker。

## 文档与知识库要求

默认不维护任何传统文档文件，包括 `docs/` 目录和 `README.md`。除非用户明确要求写报告、说明书、答辩材料或文档更新，否则功能修改、测试修复和代码调整都不需要同步更新这些文件。

默认需要维护的知识库只有 `.nexus-map/`：当代码结构、接口、数据流、测试覆盖或关键业务概念发生变化，并且会影响后续 AI 理解项目时，更新对应的 `.nexus-map` 文件。

如果只是小范围 bug 修复、样式调整、测试补丁或一次性排查，不需要更新任何文档；在回复中说明修改内容和验证结果即可。

## 向用户解释时的语气

用户现在压力很大，且明确说明“什么都不懂”。AI 应：

- 用中文解释。
- 先给结论，再拆步骤。
- 不要嘲笑基础薄弱。
- 不要只抛术语。
- 每个术语后面尽量解释作用。
- 把复杂系统拆成数据流。
- 给可背诵的答辩话术。
- 明确告诉用户“现在有代码的部分”和“还没实现的部分”。

## 当前最重要结论

当前系统不是没有价值，而是只有后半段 MVP：

```text
演示数据 -> 规则/NLP分析 -> 后端计算 -> 前端展示
```

需要补齐前半段和增强分析深度：

```text
真实评论采集 -> 数据清洗 -> LLM语义分析 -> 结构化入库 -> 产品迭代决策
```

所有后续工作都应围绕这条升级路线推进。
