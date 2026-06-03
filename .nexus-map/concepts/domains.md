> generated_by: nexus-mapper v2
> verified_at: 2026-06-03
> provenance: Manual domain extraction from README, docs, Java services, NLP code, SQL schema, and tests.

# Domain Concepts

## Product

`products` 表中的商品。默认演示商品是 `demo-earphone`，竞品默认是 `demo-earphone-competitor`。如果某些动作或同步流程遇到不存在的商品编码，后端会自动创建基础商品记录。

## Review

原始评论保存在 `reviews_raw`。受控演示数据由 `POST /api/v1/demo-data/init` 创建，固定目标评论数是 100。真实外部评论同步目前属于第二轨，主要表达同步透明度和 handoff 准备度。

## Aspect

评论方面，也就是评论在说哪类问题。当前 canonical aspects:

- `battery`
- `bluetooth`
- `noise-canceling`
- `comfort`
- `microphone`

后端和 NLP 都有方面归一化。NLP 用关键词匹配；后端也能在 NLP 失败时用受控规则回退。

## Sentiment

评论情感极性：`NEGATIVE`、`NEUTRAL`、`POSITIVE`。NLP 服务用正负关键词判断；后端受控分析会根据评分或规则转换为极性和分数。

## Analysis Job

分析任务由 `POST /api/v1/analysis/start` 创建。当前实现是同步执行：`QUEUED -> RUNNING -> SUCCEEDED/FAILED`。成功时写入物化输出。NLP 不可用或返回不合同时，后端可以降级到受控规则分析并仍返回 `SUCCEEDED`，但 `errorMessage` 会带 `degraded:*`。

## Materialization

物化输出是当前查询主链路。`AnalysisMaterializationRepository.replaceOutputs(...)` 覆盖写入:

- `review_aspects`
- `issue_clusters`
- `issue_scores`

问题列表、对比、趋势、词云、showcase 大多读取这些物化结果。

## Issue

问题簇来自负向评论聚合。优先级评分由固定权重计算：

- negativeRate: 0.35
- mentionVolume: 0.25
- trendGrowth: 0.20
- competitorGap: 0.20

答辩时应说明这不是大模型或复杂机器学习，而是可解释的规则评分。

## Action

改进行动由 `POST /api/v1/actions` 创建，默认状态 `PLANNED`。它关联 productCode 和 issueId，用于后续验证窗口。

## Validation

验证模块用于比较动作前后的负向率变化。后端优先读取 `validation_metrics`，缺少快照时会按动作窗口或回退边界计算并写回。

## Showcase

showcase 模块不是独立业务系统，而是把同步、分析、物化、动作、验证等运行态组合成演示面板：

- pipeline
- agent arena
- explainability
- chaos
- report preview

其中 explainability 当前是 `CONTROLLED_DATA_ONLY`，解释固定权重，不是模型归因。chaos 当前是运行态信号板，不是完整故障注入系统。
