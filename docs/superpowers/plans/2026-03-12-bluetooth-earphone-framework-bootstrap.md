# Bluetooth Earphone Review System Framework Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable monorepo technical framework (backend + NLP + frontend + infra) for future feature implementation.

**Architecture:** Use a three-service architecture: Spring Boot API for business orchestration, FastAPI for NLP analysis, and Vue dashboard for visualization shell. Keep all business logic as placeholders but expose stable interfaces matching V1.0 design APIs.

**Tech Stack:** Java 21 + Spring Boot 3, Python 3.11 + FastAPI, Vue 3 + TypeScript + Vite, PostgreSQL, Redis, Docker Compose

---

## Chunk 1: Repository and Infrastructure Skeleton

### Task 1: Create repository-level scaffold

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `.env.example`
- Create: `docker-compose.yml`

- [ ] **Step 1: Write the failing validation command**

Run: `docker compose config`
Expected: Fails because `docker-compose.yml` does not exist yet.

- [ ] **Step 2: Create minimal infrastructure files**

Add root documentation, env examples, and compose service definitions for `backend`, `nlp-service`, `frontend`, `postgres`, `redis`.

- [ ] **Step 3: Run infrastructure validation**

Run: `docker compose config`
Expected: Succeeds with merged compose output.

### Task 2: Create database bootstrap script

**Files:**
- Create: `infra/db/init/001_init.sql`

- [ ] **Step 1: Write schema validation check**

Run: `rg "CREATE TABLE" infra/db/init/001_init.sql`
Expected: Fails because file does not exist yet.

- [ ] **Step 2: Implement initial schema**

Create core tables aligned to design: `products`, `competitors`, `reviews_raw`, `review_aspects`, `issue_clusters`, `issue_scores`, `improvement_actions`, `validation_metrics`, `sync_jobs`, `analysis_jobs`.

- [ ] **Step 3: Re-run schema validation**

Run: `rg "CREATE TABLE" infra/db/init/001_init.sql`
Expected: Lists table DDL entries.

## Chunk 2: Backend API (Spring Boot)

### Task 3: Add backend project and tests first

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java`

- [ ] **Step 1: Write failing backend tests**

Test health endpoint and analysis job start/status APIs before controllers exist.

- [ ] **Step 2: Run backend test command**

Run: `mvn -f backend/pom.xml test`
Expected: FAIL due missing implementation.

- [ ] **Step 3: Add minimal backend implementation**

Create Spring Boot app, controllers, DTOs, and an in-memory job store.

- [ ] **Step 4: Re-run backend tests**

Run: `mvn -f backend/pom.xml test`
Expected: PASS.

### Task 4: Add backend configuration and API placeholders

**Files:**
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/wh/review/backend/controller/*.java`
- Create: `backend/src/main/java/com/wh/review/backend/service/*.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/*.java`

- [ ] **Step 1: Add endpoints matching V1.0 API shape**

Include `/api/v1/health`, `/api/v1/analysis/start`, `/api/v1/analysis/jobs/{id}`, `/api/v1/issues`.

- [ ] **Step 2: Validate backend compiles and tests remain green**

Run: `mvn -f backend/pom.xml test`
Expected: PASS.

## Chunk 3: NLP Service (FastAPI)

### Task 5: Add NLP service tests first

**Files:**
- Create: `nlp-service/requirements.txt`
- Create: `nlp-service/tests/test_health.py`
- Create: `nlp-service/tests/test_analyze.py`

- [ ] **Step 1: Write failing FastAPI tests**

Test `/health` and `/analyze` endpoints before app implementation.

- [ ] **Step 2: Run NLP tests**

Run: `pytest nlp-service/tests -q`
Expected: FAIL due missing app.

- [ ] **Step 3: Add minimal app implementation**

Create FastAPI app and schemas with deterministic placeholder response.

- [ ] **Step 4: Re-run NLP tests**

Run: `pytest nlp-service/tests -q`
Expected: PASS.

## Chunk 4: Frontend Shell (Vue 3 + TypeScript)

### Task 6: Add frontend test first and app shell

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/components/StatusCard.vue`
- Create: `frontend/src/test/App.spec.ts`

- [ ] **Step 1: Write failing frontend test**

Create one render-level test verifying dashboard title and service status cards.

- [ ] **Step 2: Run frontend tests**

Run: `npm --prefix frontend test`
Expected: FAIL before implementation/config exists.

- [ ] **Step 3: Implement minimal frontend shell**

Add dashboard scaffold with API placeholders and layout component.

- [ ] **Step 4: Re-run frontend tests**

Run: `npm --prefix frontend test`
Expected: PASS.

## Chunk 5: End-to-end project verification

### Task 7: Verify all framework layers

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document startup and test commands**

Add commands for local service runs and docker compose startup.

- [ ] **Step 2: Execute available verification commands**

Run:
- `mvn -f backend/pom.xml test`
- `pytest nlp-service/tests -q`
- `npm --prefix frontend test`
- `docker compose config`

Expected: Commands pass when toolchains are installed; otherwise capture actionable failure reasons.

- [ ] **Step 3: Summarize status**

Report implemented scaffold, test status, and immediate next tasks.
