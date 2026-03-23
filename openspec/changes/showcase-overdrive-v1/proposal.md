## Why

The current repository is stable but still looks like a framework demo, which is risky for final course acceptance where visible technical depth matters. We need a high-impact showcase layer that visibly raises UI complexity and feature breadth while keeping implementation scope controllable for a 4-person team.

## What Changes

- Add a cinematic login gate with interactive mascot and animated cursor effects.
- Add multiple high-visibility dashboard modules focused on "advanced capability" storytelling.
- Add explicit backend placeholder APIs for unimplemented capabilities so frontend can render realistic modules without fake static wiring.
- Keep existing core APIs intact while introducing a separate `/api/v1/showcase/*` namespace for staged expansion.
- Add OpenSpec artifacts and a delivery plan to align work with spec-driven process.

## Capabilities

### New Capabilities
- `showcase-ui`: High-visual frontend shell including login interaction, animation system, and additional showcase modules.
- `showcase-placeholder-apis`: Backend placeholder endpoints that expose deterministic, structured, non-production responses for unimplemented advanced features.

### Modified Capabilities
- `<none>`: No existing requirement-level capability is redefined in this change.

## Impact

- Frontend: `App.vue`, API client, domain types, and new showcase components.
- Backend: new DTOs/controllers/services and smoke-test coverage for showcase endpoints.
- Documentation: new design and implementation plan documents plus OpenSpec change artifacts.
