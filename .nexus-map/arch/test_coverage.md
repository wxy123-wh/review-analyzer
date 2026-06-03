> generated_by: nexus-mapper v2
> verified_at: 2026-06-03
> provenance: Static test inventory plus commands executed in this session.

# Test Coverage

## Executed Checks

- Frontend: `npm --prefix frontend test`
  - result: passed
  - files: 13 passed
  - tests: 45 passed
  - includes copy guard: `frontend/scripts/check-banned-copy-terms.mjs`

- NLP service: `python -m pytest nlp-service/tests -q`
  - result: passed
  - tests: 8 passed
  - warning: pytest-asyncio default fixture loop scope deprecation warning; not a current failure.

- Backend: `mvn -f backend/pom.xml test`
  - result: passed
  - tests: 38 passed
  - note: Maven 3.9.16 was installed in the user tool directory and run with Java 21.

## Static Test Inventory

Backend tests:

- `backend/src/test/java/review/backend/ApiSmokeTest.java`
- `backend/src/test/java/review/backend/PersistenceIntegrationTest.java`
- `backend/src/test/java/review/backend/application/AnalysisJobServiceTest.java`
- `backend/src/test/java/review/backend/application/InsightQueryServiceTest.java`
- `backend/src/test/java/review/backend/application/SyncJobServiceTest.java`

Frontend tests:

- `frontend/src/test/App.spec.ts`
- `frontend/src/test/ActionList.spec.ts`
- `frontend/src/test/CompareTable.spec.ts`
- `frontend/src/test/IssueTable.spec.ts`
- `frontend/src/test/ShowcaseAgentArenaPanel.spec.ts`
- `frontend/src/test/ShowcaseChaosPanel.spec.ts`
- `frontend/src/test/ShowcaseExplainabilityPanel.spec.ts`
- `frontend/src/test/ShowcasePipelinePanel.spec.ts`
- `frontend/src/test/ShowcaseReportCenter.spec.ts`
- `frontend/src/test/StatusCard.spec.ts`
- `frontend/src/test/TrendList.spec.ts`
- `frontend/src/test/ValidationList.spec.ts`
- `frontend/src/test/WordCloudPanel.spec.ts`

NLP tests:

- `nlp-service/tests/test_health.py`
- `nlp-service/tests/test_analyze.py`
- `nlp-service/tests/conftest.py`

## Covered Behaviors

- Frontend shell, login interactions, API state rendering, core list/chart components, showcase panels.
- NLP health and analyze response behavior for keyword-based aspect/sentiment logic.
- Backend tests exist for API smoke path, persistence integration, analysis lifecycle/degradation, insight query semantics, and sync jobs.

## Evidence Gaps

- Backend Maven tests passed locally after installing Maven and using Java 21.
- No end-to-end browser test was run in this session.
- Docker Compose full stack startup was not run in this session.
- No production compose validation was run.
