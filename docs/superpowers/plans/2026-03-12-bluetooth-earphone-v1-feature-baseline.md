# Bluetooth Earphone Review V1 Feature Baseline Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a runnable V1 baseline that covers the core FR APIs and a usable narrow-sidebar dashboard workflow for sync, analysis, issues, compare, trends, actions, and validation.

**Architecture:** Keep the existing three-service split (Spring Boot backend, FastAPI NLP service, Vue frontend). Expand backend with in-memory domain services and deterministic sample analytics while preserving API contracts from the V1.0 design document.

**Tech Stack:** Java 21 + Spring Boot 3, Python FastAPI, Vue 3 + TypeScript + Vitest, Docker Compose, PostgreSQL/Redis placeholders

---

## Chunk 1: Backend API Expansion (FR-01, FR-06, FR-07, FR-08, FR-10)

### Task 1: Add failing backend API tests for missing endpoints

**Files:**
- Modify: `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java`

- [ ] **Step 1: Add failing tests for endpoint coverage**

Add tests for:
- `POST /api/v1/sync/start`
- `GET /api/v1/sync/jobs/{id}`
- `GET /api/v1/compare`
- `GET /api/v1/trends`
- `POST /api/v1/actions`
- `GET /api/v1/validation`

- [ ] **Step 2: Run failing tests**

Run: `mvn -f backend/pom.xml test`
Expected: FAIL due missing controllers/service logic.

### Task 2: Implement backend DTOs and service layer for V1 contracts

**Files:**
- Create: `backend/src/main/java/com/wh/review/backend/controller/SyncController.java`
- Create: `backend/src/main/java/com/wh/review/backend/controller/CompareController.java`
- Create: `backend/src/main/java/com/wh/review/backend/controller/TrendController.java`
- Create: `backend/src/main/java/com/wh/review/backend/controller/ActionController.java`
- Create: `backend/src/main/java/com/wh/review/backend/controller/ValidationController.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/SyncStartRequest.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/SyncJobResponse.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/CompareItem.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/CompareResponse.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/TrendPoint.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/TrendResponse.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/ActionCreateRequest.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/ActionResponse.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/ValidationItem.java`
- Create: `backend/src/main/java/com/wh/review/backend/dto/ValidationResponse.java`
- Create: `backend/src/main/java/com/wh/review/backend/service/SyncJobService.java`
- Create: `backend/src/main/java/com/wh/review/backend/service/InsightQueryService.java`
- Create: `backend/src/main/java/com/wh/review/backend/service/ActionService.java`

- [ ] **Step 1: Implement minimal contract-complete DTOs**

Keep response fields explicit and stable for frontend consumption.

- [ ] **Step 2: Implement deterministic in-memory services**

Support:
- sync job creation/query
- compare/trend/validation sample responses
- action create/list for validation feed

- [ ] **Step 3: Wire controllers to services**

Return proper HTTP status codes (`202`, `201`, `200`, `404`).

### Task 3: Expand issue output with priority evidence

**Files:**
- Modify: `backend/src/main/java/com/wh/review/backend/dto/IssueItem.java`
- Modify: `backend/src/main/java/com/wh/review/backend/controller/IssueController.java`
- Modify: `backend/src/main/java/com/wh/review/backend/service/InsightQueryService.java`

- [ ] **Step 1: Add failing assertion(s) for issue payload richness**

Add test checks for non-empty issues including `priorityScore` and evidence summary.

- [ ] **Step 2: Implement issue payload generation**

Compute `priorityScore` from weighted factors (negative rate, mention volume, trend growth, competitor gap) using fixed sample factors.

- [ ] **Step 3: Run backend tests**

Run: `mvn -f backend/pom.xml test`
Expected: PASS.

## Chunk 2: NLP Service Strengthening (FR-03, FR-04, FR-05)

### Task 4: Add failing NLP tests for richer analysis output

**Files:**
- Modify: `nlp-service/tests/test_analyze.py`

- [ ] **Step 1: Add failing test assertions**

Assert:
- known aspect mapping (`battery`, `connectivity`, etc.)
- negative polarity capture
- non-empty issue clusters when negatives exist

- [ ] **Step 2: Run failing NLP tests**

Run: `pytest nlp-service/tests -q`
Expected: FAIL before implementation updates.

### Task 5: Improve analyzer rules and payload consistency

**Files:**
- Modify: `nlp-service/app/analyzer.py`
- Modify: `nlp-service/app/main.py`

- [ ] **Step 1: Refine keyword/polarity logic**

Expand keyword markers for core earphone aspects and stable confidence behavior.

- [ ] **Step 2: Ensure deterministic cluster ordering**

Sort clusters by mention count descending to stabilize UI and tests.

- [ ] **Step 3: Run NLP tests**

Run: `pytest nlp-service/tests -q`
Expected: PASS.

## Chunk 3: Frontend Narrow-Sidebar Dashboard

### Task 6: Add failing frontend tests for narrow sidebar and core sections

**Files:**
- Modify: `frontend/src/test/App.spec.ts`

- [ ] **Step 1: Add failing test expectations**

Assert:
- narrow sidebar container renders
- nav tabs exist (Overview/Issues/Compare/Trends/Actions/Validation)
- default dashboard content renders with API-backed cards/tables.

- [ ] **Step 2: Run failing frontend tests**

Run: `npm --prefix frontend test`
Expected: FAIL before UI rewrite.

### Task 7: Implement narrow sidebar layout and V1 views

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/api/client.ts`
- Create: `frontend/src/types/domain.ts`
- Create: `frontend/src/components/IssueTable.vue`
- Create: `frontend/src/components/CompareTable.vue`
- Create: `frontend/src/components/TrendList.vue`
- Create: `frontend/src/components/ActionList.vue`
- Create: `frontend/src/components/ValidationList.vue`

- [ ] **Step 1: Add typed API clients for new endpoints**

Implement fetchers for issues/compare/trends/actions/validation and action creation.

- [ ] **Step 2: Build narrow sidebar interaction model**

Create compact fixed-width sidebar and content switching by active module.

- [ ] **Step 3: Render module-specific cards/tables**

Show loading/error states and safe empty fallback for each module.

- [ ] **Step 4: Run frontend tests**

Run: `npm --prefix frontend test`
Expected: PASS.

## Chunk 4: End-to-End Verification

### Task 8: Run full verification and update docs

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README with expanded endpoint coverage and UI navigation**

Add API summary and narrow-sidebar module overview.

- [ ] **Step 2: Run all verification commands**

Run:
- `mvn -f backend/pom.xml test`
- `pytest nlp-service/tests -q`
- `npm --prefix frontend test`
- `docker compose config`

Expected: All commands pass.

- [ ] **Step 3: Report completion evidence**

Summarize implemented features mapped to FR identifiers and include verification outputs.
