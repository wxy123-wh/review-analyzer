> generated_by: nexus-mapper v2
> verified_at: 2026-06-04
> provenance: Static test inventory plus commands executed in this session.

# Test Coverage

## Executed Checks

- Frontend focused trend checks: `npm --prefix frontend test -- --run TrendList ApiClient`
  - result: passed
  - files: 2 passed
  - tests: 12 passed
  - includes copy guard: `frontend/scripts/check-banned-copy-terms.mjs`
  - latest scope includes API normalization and trend rendering behavior: `fetchTrends` can send `uxSecondaryLabel`, trend data can be represented as multiple `TrendSeries`, `TrendList` renders multiple colored SVG lines, and clicking a legend item highlights that UX dimension and refreshes the detail panel.

- Frontend full suite: `npm --prefix frontend test`
  - result: passed
  - files: 12 passed
  - tests: 54 passed
  - latest scope includes the standalone `TaxonomyManagerPanel`, sidebar `taxonomy` navigation, data intake product-category dropdown, readonly taxonomy preview, and bind-only taxonomy flow in `ProductSetupPanel`.

- Frontend build: `npm --prefix frontend run build`
  - result: passed
  - note: Vite production build completed successfully.

- NLP service: `python -m pytest nlp-service/tests -q`
  - result: passed
  - tests: 14 passed
  - warning: pytest-asyncio default fixture loop scope deprecation warning; not a current failure.
  - latest scope includes explicit `NLP_FORCE_LOCAL=true` rule-mode tests, no-key/no-model `llm_config_missing` 503 behavior, LLM prompt JSON brace escaping, robust extraction of JSON arrays after `<think>`/extra text, positive taxonomy-label recovery when an LLM returns fallback labels for clearly labeled positive reviews, and remote LLM connection-close errors returning JSON `llm_analysis_failed` 502 responses instead of raw FastAPI 500.

- Crawler and cleaning pipeline: `python -m pytest pipeline/tests crawler/tests -q`
  - result: passed
  - tests: 10 passed
  - covers JD packet parsing, JSONL append/dedupe, conservative cleaner behavior, platform placeholder removal, and retaining real follow-up content after stripping placeholders.

- Backend: `mvn -f backend/pom.xml test`
  - result: passed
  - tests: 57 passed
  - note: Maven was run with Java 21.
  - latest scope covers async `POST /api/v1/analysis/jobs` immediate `QUEUED` response, `GET /api/v1/analysis/jobs/{id}` polling across `QUEUED/RUNNING/SUCCEEDED/FAILED`, persisted LLM progress fields and materialization fields in `analysis_jobs`, analysis start clearing stale materialized outputs, per-batch append of review-level materialization and cumulative issue-cluster rebuild, stale `review_id` detection before `review_semantic_labels` / `review_aspects` foreign-key failure, legacy synchronous `POST /api/v1/analysis/start`, `GET /api/v1/reviews/intake-status` reflecting JSONL files, imported/analyzed counts, downstream readiness, recent reviews and latest analysis job, `POST /api/v1/reviews/clean-jsonl` reading raw JSONL and returning cleaned/removed/summary/sample without writing `products`/`reviews_raw`/`sync_jobs`/`data_quality_runs`, `POST /api/v1/reviews/import-jsonl` directly importing `cleaned_reviews_*.jsonl` without re-cleaning raw JSONL, cleaned reimport clearing stale materialized outputs before replacing local raw reviews, blocking `replaceExisting=true` while an analysis job is `QUEUED`/`RUNNING`, persistence integration, positive insights, compare/trends/wordcloud, wordcloud negative/positive VOC bucket balancing and brand/model noise filtering, UX before/after comparison, sync jobs, and fatal NLP LLM-config errors skipping materialization.

## Static Test Inventory

Backend tests:

- `backend/src/test/java/review/backend/ApiSmokeTest.java`
- `backend/src/test/java/review/backend/PersistenceIntegrationTest.java`
- `backend/src/test/java/review/backend/application/AnalysisJobServiceTest.java`
- `backend/src/test/java/review/backend/application/InsightQueryServiceTest.java`
- `backend/src/test/java/review/backend/application/SyncJobServiceTest.java`
- `backend/src/test/java/review/backend/application/UxChangeComparisonServiceTest.java`

Frontend tests:

- `frontend/src/test/App.spec.ts`
- `frontend/src/test/ApiClient.spec.ts`
- `frontend/src/test/CompareTable.spec.ts`
- `frontend/src/test/IssueTable.spec.ts`
- `frontend/src/test/PositiveInsightPanel.spec.ts`
- `frontend/src/test/ProductSetupPanel.spec.ts`
- `frontend/src/test/StatusCard.spec.ts`
- `frontend/src/test/TaxonomyManagerPanel.spec.ts`
- `frontend/src/test/TrendList.spec.ts`
- `frontend/src/test/UxChangeComparisonPanel.spec.ts`
- `frontend/src/test/UxTaxonomyTree.spec.ts`
- `frontend/src/test/WordCloudPanel.spec.ts`

NLP tests:

- `nlp-service/tests/test_health.py`
- `nlp-service/tests/test_analyze.py`
- `nlp-service/tests/conftest.py`

Crawler/pipeline tests:

- `crawler/tests/test_jd_reviews.py`
- `pipeline/tests/test_clean_reviews.py`

## Covered Behaviors

- Frontend coverage currently confirms taxonomy-driven multi-series trend rendering and click highlight behavior, API normalization, sidebar `taxonomy` navigation, standalone taxonomy create/edit/save behavior, data intake dropdown selection of saved taxonomies, readonly taxonomy preview, bind-only product taxonomy flow, persisted intake status restoration, and async LLM progress polling.
- NLP health, explicit rule-mode analyze response behavior for keyword-based aspect/sentiment logic, no-key LLM config failure behavior, LLM prompt construction safety, and JSON error responses for remote LLM connection failures.
- Crawler/pipeline tests cover parser extraction, output dedupe, dry-run JSONL writing, cleaner summary counts, and platform placeholder removal/追评保留。
- Backend tests cover persistence integration, API smoke path, analysis lifecycle/degradation/fatal LLM config failure, async analysis job queue/polling with persisted progress and materialization counts, stale review-id materialization guard, active-analysis replacement import guard, intake-status workflow ledger, JSONL clean-only behavior, direct import placeholder filtering, manual JSONL path handling, positive insight aggregation, compare/trends/wordcloud, UX before/after comparison, sync jobs, and validation/action compatibility.

## Evidence Gaps

- Docker Compose runtime check passed after rebuilding backend/frontend and restarting `nlp-service`: `docker compose ps`, backend `/api/v1/health`, NLP `/health`, frontend HTTP 200 at `http://127.0.0.1:5175`. The running frontend container has blank `VITE_API_BASE_URL`, so client-side fallback uses the current browser host.
- Direct runtime API checks after rebuild: `/api/v1/issues`, `/api/v1/positive-insights`, `/api/v1/trends`, and `/api/v1/wordcloud` returned `success`; `/api/v1/ux-change-comparisons` returned `empty` with zero records when no before/after checkpoint exists, which is a business empty state rather than a failed request.
- Runtime E2E API check passed with `crawler/output/raw_reviews_codex-e2e-20260604.jsonl` and `crawler/output/raw_reviews_codex-e2e-competitor-20260604.jsonl`: JSONL discovery, `clean-jsonl`, taxonomy binding, `import-jsonl`, async `analysis/jobs` polling to `SUCCEEDED`, `/issues`, `/positive-insights`, `/compare`, `/trends`, `/wordcloud`, `/data-quality`, and `/ux-change-comparisons` all returned usable success states.
- Browser check against the current in-app page `http://127.0.0.1:5175/` and a temporary Vite test-mode page `http://127.0.0.1:5181/` could not run because the Browser automation policy blocked local address access in this session.
- No production compose validation was run.
