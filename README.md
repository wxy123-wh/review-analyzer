# Bluetooth Earphone Review Decision System (Framework Bootstrap)

This repository provides a runnable technical framework for the system described in:
`docs/2026-03-12-bluetooth-earphone-review-system-design-v1.0.md`.

## Repository Layout

- `backend/`: Spring Boot API service (business orchestration + async task placeholder)
- `nlp-service/`: FastAPI NLP service (analysis placeholder)
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
