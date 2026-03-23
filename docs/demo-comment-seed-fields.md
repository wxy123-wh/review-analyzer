# 演示评论字段清单（T2 数据底座）

## 1. 目标

- 面向演示环境提供单产品固定 100 条评论数据。
- 以 `reviews_raw` 为主表，确保字段含义明确、可追溯且可重复初始化（幂等）。

## 2. 字段定义

| 字段名 | 类型 | 必填 | 约束/索引 | 说明 | 演示生成规则 |
| --- | --- | --- | --- | --- | --- |
| `source` | `VARCHAR(64)` | 是 | 与 `source_review_id` 组成唯一键 | 数据来源标识 | 固定为 `demo-seed` |
| `source_review_id` | `VARCHAR(128)` | 是 | 唯一键组成列 | 来源侧评论 ID | `productCode-001` 到 `productCode-100` |
| `product_id` | `BIGINT` | 是 | FK -> `products.id` | 评论归属产品 | 初始化时自动确保产品存在并关联 |
| `rating` | `NUMERIC(3,1)` | 否 | - | 评分 | 固定分布（2.0 / 3.0 / 4.5） |
| `content` | `TEXT` | 是 | - | 评论正文 | 基于 aspect 模板生成确定性文本 |
| `review_time` | `TIMESTAMPTZ` | 否 | - | 评论时间 | 从固定起点按 6 小时间隔递增 |
| `anonymized_author_id` | `VARCHAR(128)` | 否 | - | 匿名用户 ID | `demo-user-001` 循环生成 |
| `demo_data_version` | `VARCHAR(32)` | 否 | - | 演示数据版本标识 | 当前固定为 `demo-comments-v1` |
| `fetched_at` | `TIMESTAMPTZ` | 是 | 默认 `NOW()` | 入库时间 | 初始化执行时刷新 |

## 3. 版本登记

新增 `demo_seed_versions` 表记录版本元数据：

- `seed_key`：当前值 `demo-comments`
- `product_code`：当前初始化目标产品
- `data_version`：当前值 `demo-comments-v1`
- `target_count`：当前值 `100`
- `last_seeded_at`：最近一次初始化时间

该表用于回溯“哪一版演示数据”在“哪个产品”上已执行过初始化。
