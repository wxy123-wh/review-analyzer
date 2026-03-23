# Bluetooth Earphone Review Decision System (Framework Bootstrap)

This repository provides a runnable technical framework for the system described in:
`docs/2026-03-12-bluetooth-earphone-review-system-design-v1.0.md`.

## Repository Layout

- `backend/`: Spring Boot API service (business orchestration + async task 演示数据流程)
- `nlp-service/`: FastAPI NLP service (analysis 演示数据流程)
- `frontend/`: Vue 3 dashboard shell
- `infra/db/init/`: PostgreSQL initialization scripts
- `docs/`: design and planning documents

## Quick Start

1. Copy env template:
   - PowerShell: `Copy-Item .env.example .env`
   - Bash: `cp .env.example .env`
2. Start all services:
   - `docker compose up --build`
3. Open apps:
   - Frontend: `http://localhost:5175`
   - Backend Health: `http://localhost:8080/api/v1/health`
   - NLP Health: `http://localhost:8000/health`

### Persistence Notes

`actions/sync/analysis` now persist into PostgreSQL tables instead of in-memory maps:

- `improvement_actions`
- `sync_jobs`
- `analysis_jobs`

You can inspect data with:

```bash
docker exec -it wh-postgres psql -U earphone -d earphone_review
```

### OneBound Configuration

To enable real comment sync from OneBound, set these variables in `.env`:

- `ONEBOUND_API_KEY`
- `ONEBOUND_API_SECRET` (if your account uses a separate secret)
- Optional: `ONEBOUND_BASE_URL`, `ONEBOUND_DEFAULT_PLATFORM`

Then trigger sync with provider `onebound`:

```bash
curl -X POST http://localhost:8080/api/v1/sync/start \
  -H "Content-Type: application/json" \
  -d '{"provider":"onebound","platform":"taobao","targetProductCode":"600530677643"}'
```

## Local Test Commands

- Backend:
  - With local Maven: `mvn -f backend/pom.xml test`
  - Without local Maven (PowerShell): `docker run --rm -v ${PWD}/backend:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn test`
- NLP:
  - `python -m pip install -r nlp-service/requirements.txt`
  - `python -m pytest nlp-service/tests -q`
- Frontend:
  - `npm --prefix frontend install`
  - `npm --prefix frontend test`

## Current Scope

This is a technical framework skeleton for V1.0:
- API contracts and service boundaries are in place.
- Core domain schema is initialized.
- Feature logic remains intentionally minimal and ready for iterative development.
- Key action/sync/analysis records are persisted to PostgreSQL.

## Production Deployment (ECS / Cloud VM)

For a complete and detailed step-by-step guide to deploying this system to cloud servers (such as Aliyun ECS, AWS EC2, or Tencent CVM), please refer to our dedicated deployment guide:
[**Cloud Deployment Guide (`docs/CLOUD_DEPLOY_GUIDE.md`)**](docs/CLOUD_DEPLOY_GUIDE.md)

### Quick Summary

Use the production compose file to avoid Vite dev server in cloud:

1. Prepare env:
   - `cp .env.example .env`
   - fill `ONEBOUND_API_KEY`, `ONEBOUND_API_SECRET`
2. Start production stack:
   - `docker compose -f docker-compose.prod.yml up --build -d`
3. Access:
   - Frontend + API gateway: `http://<server-ip>:${PUBLIC_PORT:-80}`
4. Health checks:
   - `curl http://<server-ip>:${PUBLIC_PORT:-80}/api/v1/health`
   - `curl http://<server-ip>:${PUBLIC_PORT:-80}/api/v1/issues`

The production frontend image serves static files via Nginx and proxies `/api/*` to backend service.

## V1 Baseline API Coverage

The backend currently exposes these V1 baseline endpoints:

- `GET /api/v1/health`
- `POST /api/v1/sync/start`
- `GET /api/v1/sync/jobs/{id}`
- `POST /api/v1/analysis/start`
- `GET /api/v1/analysis/jobs/{id}`
- `GET /api/v1/issues`
- `GET /api/v1/compare`
- `GET /api/v1/trends`
- `POST /api/v1/actions`
- `GET /api/v1/validation`

## Dashboard Modules

Frontend now provides a narrow-sidebar navigation workflow with these modules:

- `总览 (Overview)`: service status and key metrics
- `问题 (Issues)`: priority-ranked issue table with evidence summary
- `对比 (Compare)`: product vs competitor score comparison
- `趋势 (Trends)`: aspect-level negative-rate and volume changes
- `动作 (Actions)`: improvement action registration and list
- `验证 (Validation)`: before/after impact tracking for actions

## V1.5 演示扩展

### 项目经理说明（中文）

- 目标：在不扩大集成范围的前提下，形成可演示、可讲解、可复盘的 V1.5 前端体验。
- 交付：统一中文文案和术语（统一为“演示数据”），并保留可持续校验机制。
- 价值：支持评审与路演时快速说明当前能力边界，以及后续从演示数据平滑过渡到真实链路。

### 登录体验

前端在进入看板前提供沉浸式登录页：

- 光标轨迹互动
- 吉祥物视线追踪
- 密码聚焦隐私动作

当前登录用于演示流程，用户名和密码均为非空即可进入。

### 演示能力 API

后端提供以下演示能力接口：

- `GET /api/v1/showcase/pipeline`
- `GET /api/v1/showcase/agent-arena`
- `GET /api/v1/showcase/explainability`
- `GET /api/v1/showcase/chaos`
- `POST /api/v1/showcase/reports/preview`

前端统一展示为确定性返回，并约定：

- `status: 演示数据`
- `implemented: false`

### 演示模块

侧边栏包含以下高可见模块：

- `流水线`: 编排阶段可视化
- `智能体`: 协同状态表格
- `可解释性`: 权重贡献条形展示
- `混沌演练`: 韧性场景剧本
- `报告中心`: 演示数据报告预览

### 建议演示流程

用于验收或路演时，推荐如下顺序：

1. 从 `WH 演示控制台` 登录（任意非空用户名与密码）。
2. 展示吉祥物视线追踪和密码聚焦隐私动作。
3. 在看板从 `总览` 切换至 `流水线 -> 智能体 -> 可解释性 -> 混沌演练`。
4. 打开 `报告中心` 触发预览，说明当前为演示数据与后续路线。
5. 最后切到 `验证`，把演示链路与行动效果闭环关联起来。
