# 洞察与词云接口契约（中文）

本文档覆盖演示数据驱动的后端洞察接口：

- `GET /api/v1/issues`
- `GET /api/v1/trends`
- `GET /api/v1/wordcloud`
- `GET /api/v1/validation`

## 1. 通用约定

- 默认产品：`demo-earphone`
- 所有结果均基于演示评论聚合；数据不足时通过 `notice` 返回中文业务提示。
- `notice` 为 `null` 表示本次响应成功且无额外提示。

## 2. 问题清单接口

### 2.1 请求

- Method: `GET`
- Path: `/api/v1/issues`
- Query 参数：
  - `productCode`（可选，默认 `demo-earphone`）

### 2.2 响应字段

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `items[].issueId` | string | 问题 ID（由方面聚合生成） |
| `items[].title` | string | 问题标题 |
| `items[].aspect` | string | 问题方面编码（如 `battery`） |
| `items[].priorityScore` | number | 优先级分值，范围 `0~1` |
| `items[].evidenceSummary` | string | 证据摘要（评论量、负向量、趋势变化） |

### 2.3 示例响应

```json
{
  "items": [
    {
      "issueId": "iss-demo-battery",
      "title": "续航体验波动",
      "aspect": "battery",
      "priorityScore": 0.4631,
      "evidenceSummary": "续航相关评论共 20 条，负向反馈 8 条（40.0%），较上一时间窗口上升 10.0%。"
    }
  ]
}
```

## 3. 趋势接口

### 3.1 请求

- Method: `GET`
- Path: `/api/v1/trends`
- Query 参数：
  - `productCode`（可选，默认 `demo-earphone`）
  - `aspect`（可选，默认 `battery`）

### 3.2 响应字段

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `productCode` | string | 产品编码 |
| `aspect` | string | 方面编码 |
| `points[].period` | string | 周期（ISO 周格式，如 `2026-W03`） |
| `points[].negativeRate` | number | 周期内负向评论占比 |
| `points[].mentionCount` | number | 周期内提及量 |
| `notice` | string/null | 中文业务提示（如暂无数据或稍后重试） |

### 3.3 示例响应

```json
{
  "productCode": "demo-earphone",
  "aspect": "battery",
  "points": [
    { "period": "2026-W01", "negativeRate": 0.4, "mentionCount": 5 },
    { "period": "2026-W02", "negativeRate": 0.6, "mentionCount": 5 }
  ],
  "notice": null
}
```

## 4. 词云接口

### 4.1 请求

- Method: `GET`
- Path: `/api/v1/wordcloud`
- Query 参数：
  - `productCode`（可选，默认 `demo-earphone`）
  - `aspect`（可选，默认 `all`，支持 `battery/bluetooth/noise-canceling/comfort/microphone/all`）

### 4.2 响应字段

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `productCode` | string | 产品编码 |
| `aspect` | string | 方面编码（或 `all`） |
| `items[].keyword` | string | 关键词 |
| `items[].frequency` | number | 关键词词频 |
| `items[].weight` | number | 词云权重（按最大词频归一化） |
| `items[].sentimentTag` | string | 情感标签（`正向/中性/负向`） |
| `notice` | string/null | 中文业务提示（如暂无数据、聚合失败） |

### 4.3 示例响应

```json
{
  "productCode": "demo-earphone",
  "aspect": "battery",
  "items": [
    { "keyword": "续航", "frequency": 20, "weight": 1.0, "sentimentTag": "负向" },
    { "keyword": "通勤", "frequency": 8, "weight": 0.4, "sentimentTag": "中性" },
    { "keyword": "固件", "frequency": 6, "weight": 0.3, "sentimentTag": "正向" }
  ],
  "notice": null
}
```

## 5. 验证接口

### 5.1 请求

- Method: `GET`
- Path: `/api/v1/validation`
- Query 参数：
  - `actionId`（可选；传入时仅返回该动作验证结果）

### 5.2 响应字段

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `items[].actionId` | string | 动作 ID |
| `items[].beforeNegativeRate` | number | 改进前负向占比 |
| `items[].afterNegativeRate` | number | 改进后负向占比 |
| `items[].improvementRate` | number | 改善值（前 - 后） |
| `items[].summary` | string | 动作验证摘要（含动作名、样本窗口、前后变化） |
| `notice` | string/null | 中文业务提示（动作不存在、数据不足等） |

### 5.3 示例响应

```json
{
  "items": [
    {
      "actionId": "101",
      "beforeNegativeRate": 0.6,
      "afterNegativeRate": 0.4,
      "improvementRate": 0.2,
      "summary": "动作「续航策略调优」在续航维度演示评论中（前10条、后10条），负向占比由 60.0% 降至 40.0%，改善 20.0%。"
    }
  ],
  "notice": null
}
```

## 6. 失败兜底示例

当评论数据尚未初始化时，趋势和词云接口返回空数据并给出中文提示：

```json
{
  "productCode": "demo-earphone",
  "aspect": "battery",
  "points": [],
  "notice": "当前暂无可分析的演示评论，请先初始化演示数据。"
}
```
