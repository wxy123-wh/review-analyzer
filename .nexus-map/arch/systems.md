> generated_by: nexus-mapper v2
> verified_at: 2026-06-03
> provenance: AST-backed for Java/TypeScript/Python; Vue and SQL details partly inferred from manual inspection and tests.

# Systems

## 1. Frontend Console

- code_path: `frontend/`
- entry: `frontend/src/main.ts`, `frontend/src/App.vue`
- tech: Vue 3, TypeScript, Vite, Axios, Vitest, @antv/g2plot, GSAP
- responsibility: 单页验收控制台，负责内部访问门禁、左侧模块导航、加载状态、图表/列表展示和 API 响应归一化。
- key files:
  - `frontend/src/api/client.ts`: 所有后端接口的 Axios 封装、超时处理、状态归一化。
  - `frontend/src/types/domain.ts`: 前端领域类型。
  - `frontend/src/components/*.vue`: 问题、对比、趋势、词云、动作、验证、showcase 面板。
- status: implemented。
- evidence gap: Vue 文件只做 module-only AST 覆盖，组件内部细节来自人工阅读和 Vitest 结果。

## 2. Backend API

- code_path: `backend/`
- entry: `backend/src/main/java/review/backend/BackendApplication.java`
- tech: Spring Boot 3.3.3, Java 21, Spring Web, Spring JDBC, Spring Validation, PostgreSQL, H2 tests
- responsibility: 业务主入口，提供 `/api/v1/*` REST 接口、任务状态流转、演示数据初始化、NLP 调用、分析物化、查询聚合和 showcase 运行态。
- controller modules:
  - `review/backend/api`: 健康检查、同步、采集、taxonomy、分析、导入、洞察、动作、验证和 showcase 接口。
  - `review/backend/api/dto`: 请求与响应 DTO。
- service modules:
  - `review/backend/application`: 分析任务、NLP 调用、真实评论规则回退、洞察查询、showcase、同步、采集和 taxonomy 编排。
  - `review/backend/data`: 数据库读写、物化结果、schema 初始化和任务仓储。
- status: implemented。

## 3. NLP Service

- code_path: `nlp-service/`
- entry: `nlp-service/app/main.py`
- tech: Python, FastAPI, Pydantic, pytest
- responsibility: 提供 `/health` 和 `/analyze`。`/analyze` 根据评论文本识别方面、情感极性、分数、置信度和负向问题簇。
- method: 当前不是机器学习模型，而是关键词规则。
  - aspect keywords: battery, bluetooth, noise-canceling, comfort, microphone。
  - polarity markers: 负向词如“差、断开、噪音、卡顿、掉电”；正向词如“好、稳定、清晰、舒适、满意”。
  - cluster: 对负向 aspect 计数并按 mentionCount 排序。
- status: implemented。

## 4. Data and Infra

- code_path: `infra/`, root `docker-compose.yml`, `docker-compose.prod.yml`
- tech: Docker Compose, PostgreSQL 16, Redis 7, SQL
- responsibility: 编排数据库、缓存、NLP、后端和前端；初始化核心表结构。
- key tables: `products`, `reviews_raw`, `review_aspects`, `issue_clusters`, `issue_scores`, `improvement_actions`, `validation_metrics`, `sync_jobs`, `analysis_jobs`, `demo_seed_versions`。
- status: implemented。
- evidence gap: SQL 文件只有 module-only AST 覆盖；表结构来自人工阅读。

## 5. Docs and Acceptance

- code_path: `docs/`, root `README.md`
- responsibility: 为安装、启动、接口、代码结构、需求图和个人工作总结提供材料。
- key files:
  - `README.md`: 操作手册。
  - `docs/代码文档.md`: 代码结构与模块说明。
  - `docs/接口文档.md`: 当前已实现接口说明。
  - `docs/requirements/diagrams/*`: 需求与系统图。
- status: implemented。

## Non-main Directories

- `legacy/`: 旧版 wh-source，保留历史参考，不属于当前主运行链路。
- `wh/`: 另一个旧版/构建相关目录，静态分析显示代码量较大，但不是 README 当前启动链路。
- `.tmp/`: 文档 QA、临时脚本和临时产物，不应进入答辩模块口径。
