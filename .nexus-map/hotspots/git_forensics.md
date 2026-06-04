> generated_by: nexus-mapper v2
> verified_at: 2026-06-03
> provenance: Generated from `.nexus-map/raw/git_stats.json` covering the last 90 days.

# Git Forensics

## Summary

- analysis_period_days: 90
- total_commits: 28
- total_authors: 2

## Top Hotspots

| path | changes | risk |
|---|---:|---|
| `frontend/src/test/App.spec.ts` | 11 | medium |
| `openspec/changes/pm-demo-chinese-overhaul-v1/tasks.md` | 9 | medium |
| `README.md` | 8 | medium |
| `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java` | 7 | medium |
| `frontend/src/App.vue` | 7 | medium |
| `frontend/src/components/LoginGate.vue` | 7 | medium |
| `frontend/src/api/client.ts` | 6 | medium |
| `openspec/changes/showcase-overdrive-v1/tasks.md` | 6 | medium |
| `backend/src/test/java/com/wh/review/backend/PersistenceIntegrationTest.java` | 5 | medium |
| `frontend/src/components/ShowcaseChaosPanel.vue` | 5 | medium |

## Interpretation

The active change surface is not the NLP algorithm. It is the frontend shell and API contract:

- frontend application shell and login gate
- frontend API client and component states
- README and OpenSpec task docs
- backend smoke, integration, insight-query, and UX-change tests
- historical showcase panels, which are now backend-compatible legacy rather than current frontend main navigation

This supports a conservative refactor order: stabilize the acceptance path and API contract before rewriting algorithms or moving large directories.

## Coupling Signals

High co-change pairs include:

- `frontend/src/App.vue` <-> `frontend/src/test/App.spec.ts`
- `frontend/src/components/LoginGate.vue` <-> `frontend/src/test/App.spec.ts`
- `frontend/src/App.vue` <-> `frontend/src/api/client.ts`
- `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java` <-> `backend/src/test/java/com/wh/review/backend/PersistenceIntegrationTest.java`
- `backend/src/main/java/com/wh/review/backend/service/InsightQueryService.java` <-> `backend/src/test/java/com/wh/review/backend/ApiSmokeTest.java`

## Refactor Risk Notes

- Changing `frontend/src/api/client.ts` can affect many panels at once because it normalizes response contracts.
- Changing `frontend/src/App.vue` can break login, navigation, initial data loading, and module visibility.
- Changing `InsightQueryService` can break multiple read APIs: issues, positive insights, compare, trends, wordcloud, and legacy validation/showcase consumers.
- Changing `UxChangeComparisonService` or `ux_change_checkpoints` can break the前后对比 module.
- Changing `ProductRepository` or product-name fields in compare/UX-change DTOs can affect whether前端显示真实商品名 or falls back to `productCode`.
- Changing backend persistence schema must be validated against both `infra/db/init/001_init.sql` and `PersistenceSchemaInitializer`.
