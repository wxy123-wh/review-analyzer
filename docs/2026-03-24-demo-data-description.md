# 演示数据说明文档（T8-9.2）

## 1. 目的

本文用于说明当前演示环境评论数据的来源、生成方式和使用边界，支持评审现场快速回答“数据从哪来、能否复现、可信边界在哪里”。

## 2. 数据来源

- 数据类型：系统内置“演示数据”，不采集真实用户隐私评论。
- 初始化入口：`POST /api/v1/demo-data/init`。
- 主实现：
  - `backend/src/main/java/com/wh/review/backend/service/DemoDataInitializationService.java`
  - `backend/src/main/java/com/wh/review/backend/persistence/DemoReviewSeedRepository.java`
- 存储表：
  - `reviews_raw`（评论正文与评分等）
  - `demo_seed_versions`（seed 版本追溯）

## 3. 生成方式

### 3.1 固定规模与幂等

- 单产品目标条数固定 `100`。
- 缺省产品编码：`demo-earphone`（可通过请求体覆盖 `productCode`）。
- 通过 `source + source_review_id` 唯一约束与 upsert 逻辑保证幂等：
  - 首次初始化：`insertedReviewCount=100`
  - 重复初始化同产品：以更新为主，不会无限新增

### 3.2 规则化生成

- `source_review_id`：`<productCode>-001` 到 `<productCode>-100`。
- 时间分布：从 `2026-01-01T00:00:00Z` 起，每条间隔 `6` 小时。
- 方面轮转：`battery`、`bluetooth`、`noise-canceling`、`comfort`、`microphone`。
- 评分分布（每 10 条循环）：
  - `2.0`：4 条（40%）
  - `3.0`：3 条（30%）
  - `4.5`：3 条（30%）
- 文本生成：按正向/中性/负向模板与方面拼装，带批次号。
- 作者匿名标识：`demo-user-001` 到 `demo-user-037` 循环。
- 版本标识：`demo-comments-v1`。

### 3.3 示例命令

```bash
curl -X POST http://localhost:8080/api/v1/demo-data/init \
  -H "Content-Type: application/json" \
  -d '{"productCode":"demo-earphone"}'
```

返回中关注字段：

- `targetReviewCount`
- `insertedReviewCount`
- `updatedReviewCount`
- `totalReviewCount`
- `dataVersion`
- `durationMs`

## 4. 局限与边界

1. 演示数据为模板化样本，不代表真实市场分布，不可直接用于生产决策。
2. 情感标签主要由评分阈值映射推导，未引入复杂语义模型置信度。
3. 当前默认单产品规模（100 条）主要服务演示与回归稳定性，不覆盖大规模压测结论。
4. 方面词典为固定集合，超出词典的表达可能归入 `unknown` 或被弱化。
5. 时间轴为规则化生成，不能等同真实促销节奏或舆情突发波动。

## 5. 适用范围

- 适用：项目演示、回归测试、接口联调、口径一致性验证。
- 不适用：真实运营看板、财务或商业决策、生产效果评估。
