## Session Notes

- 2026-04-06 cleanup: reverted all out-of-scope tool state, screenshots, evidence deletions, and unrelated product files; retained only Task 1 contract files plus notepad notes.

- 2026-04-06: Wave-1 contract now fixes canonical aspect taxonomy to `battery` / `bluetooth` / `noise-canceling` / `comfort` / `microphone`; frontend client normalizes legacy aliases (`connectivity`, `noise_canceling`, `call_quality`) before consumption.
- 2026-04-06: `frontend/src/App.vue` now carries an explicit module inventory covering state, data source, and keep/replace/hide strategy for every core/showcase module.
- 2026-04-06: `showcase/explainability` is documented and coded as `controlled-data-only` rather than a full placeholder because it currently explains deterministic score weights, not model introspection.

- 2026-04-06: Task 2 now executes analysis jobs synchronously through `QUEUED -> RUNNING -> SUCCEEDED/FAILED`, using demo-seeded `reviews_raw` as the controlled source and persisting materialized outputs into `review_aspects`, `issue_clusters`, and `issue_scores`.
- 2026-04-06: Explicit reuse semantics for controlled analysis are: return the latest successful job for the same product only when `reviews_raw.fetched_at` has not advanced past that job's `finished_at` and persisted aspect rows still exist for the product.
