# Showcase Overdrive Design

## Goal

Build a "high-tech presentation layer" that intentionally emphasizes engineering breadth and visual impact for course acceptance, while keeping delivery realistic for a 4-person team.

## Scope

1. Keep baseline dashboard functionality unchanged.
2. Add a cinematic login entry experience (interactive cursor + mascot response).
3. Add advanced-looking dashboard modules backed by deterministic placeholder APIs.
4. Keep explicit labels for placeholder state to avoid architectural ambiguity.

## Architecture

1. Backend adds a dedicated `showcase` slice:
   1. `ShowcaseController` for `/api/v1/showcase/*`.
   2. `ShowcaseService` for deterministic placeholder payloads.
   3. Typed DTO responses for each module.
2. Frontend adds a split showcase shell:
   1. `LoginGate` as pre-dashboard interaction layer.
   2. New module components: pipeline, agent arena, explainability, chaos drill, report center.
   3. Extended `api/client.ts` and `types/domain.ts` to consume showcase endpoints.

## Data Flow

1. User opens app -> sees login gate.
2. User submits demo credentials -> app enters dashboard state.
3. User opens showcase tabs -> frontend calls `/api/v1/showcase/*`.
4. Backend returns structured placeholder payloads with `implemented=false`.
5. Frontend renders metrics/cards/timeline and displays "placeholder" badges.

## Error Handling

1. All showcase fetchers return safe fallback payload on error.
2. UI surfaces failure with visible hint instead of blank screen.
3. Placeholder endpoints remain deterministic for repeatable demo.

## Testing Strategy

1. Frontend: test login-gate visibility, successful login transition, showcase navigation.
2. Backend: smoke tests for new showcase endpoints and required fields.
3. Existing NLP and compose checks remain part of regression verification.

## Out of Scope

1. Real authentication and token flow.
2. Real queue/orchestrator integration.
3. Real explainability computation and report export jobs.

## Acceptance Signals

1. Visual first impression is significantly upgraded.
2. New modules are reachable and data-backed (even if placeholder).
3. Endpoints and UI contracts are explicit enough for follow-up real implementation.
