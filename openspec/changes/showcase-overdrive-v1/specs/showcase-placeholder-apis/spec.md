## ADDED Requirements

### Requirement: Showcase Placeholder API Namespace
The backend MUST expose dedicated placeholder endpoints under `/api/v1/showcase/*`.

#### Scenario: Client requests pipeline placeholder
- **WHEN** the client calls `GET /api/v1/showcase/pipeline`
- **THEN** the response includes deterministic pipeline-stage payload and explicit placeholder flags

#### Scenario: Client requests multi-agent placeholder
- **WHEN** the client calls `GET /api/v1/showcase/agent-arena`
- **THEN** the response includes deterministic multi-agent status payload and explicit placeholder flags

### Requirement: Explicit Placeholder Contract
Each showcase endpoint MUST provide machine-readable placeholder state metadata.

#### Scenario: Placeholder metadata is present
- **WHEN** the client receives any `/api/v1/showcase/*` response
- **THEN** the payload includes `status` and `implemented` fields for UI labeling

#### Scenario: Placeholder report preview call succeeds
- **WHEN** the client calls `POST /api/v1/showcase/reports/preview`
- **THEN** the backend returns preview-ready sections without generating real artifacts
