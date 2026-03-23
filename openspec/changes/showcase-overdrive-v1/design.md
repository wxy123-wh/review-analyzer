## Context

The repository already has a three-service skeleton (Spring Boot + FastAPI + Vue) and baseline dashboards, but many "advanced" capabilities are either absent or only implied in documents. The course acceptance context favors demonstrable technical complexity and visual polish over strict product-market validation.

## Goals / Non-Goals

**Goals:**
- Deliver a visibly "high-tech" UI layer including an interactive login scene and animation-rich dashboard modules.
- Add backend placeholder APIs for advanced capabilities so UI can render realistic states through actual network contracts.
- Keep implementation lightweight enough for a 4-person team to complete and present confidently.
- Keep existing baseline modules operational and avoid breaking current API contracts.

**Non-Goals:**
- Implement production-grade authentication, RBAC, or security hardening.
- Implement real distributed workflow orchestration, model explainability engines, or fault-injection infrastructure.
- Replace current baseline insight logic with a full analytics pipeline.

## Decisions

1. **Create a dedicated showcase API namespace**  
Use `/api/v1/showcase/*` to isolate placeholder/preview capabilities from baseline APIs.  
Alternative considered: extending existing endpoints with optional fields. Rejected because it blurs baseline vs staged functionality.

2. **Introduce a login gate as a visual entrance layer**  
A single-page login gate with mascot/cursor interaction gives immediate visual impact with limited backend dependency.  
Alternative considered: full router + auth flow. Rejected to avoid scope explosion.

3. **Use deterministic placeholder contracts**  
All placeholder endpoints will return `status` and `implemented` markers plus rich mock payloads.  
Alternative considered: random data generation. Rejected because deterministic payloads are easier to test and demo.

4. **Split showcase UI into focused components**  
Each showcase module will have an isolated component and type contract to avoid giant monolithic UI files.  
Alternative considered: one large `App.vue`. Rejected for maintainability and team parallel work.

## Risks / Trade-offs

- **[Risk] Visual complexity increases CSS/interaction bugs** -> Mitigation: isolate interaction logic in dedicated components and add focused UI tests.
- **[Risk] Placeholder features may be questioned as "fake"** -> Mitigation: explicitly label placeholder state in UI and API response with a roadmap message.
- **[Risk] Team spends too much time on polish** -> Mitigation: lock a concrete module list and cap each module to deterministic read-only behavior.
- **[Risk] Acceptance may expect deeper backend depth** -> Mitigation: include real API calls for all showcase modules and keep test evidence for new endpoints.

## Migration Plan

1. Add OpenSpec artifacts and lock scope.
2. Add backend tests for showcase endpoints (expected failing first).
3. Implement showcase DTO/service/controller and deterministic payloads.
4. Add frontend tests for login gate + module switching (expected failing first).
5. Implement login gate, cursor/mascot interaction, and showcase modules.
6. Run available tests and OpenSpec validation.
7. Update README/demo script notes for acceptance presentation.

## Open Questions

- Whether to require demo credentials or allow one-click guest mode at presentation time.
- Whether report-export placeholder should generate downloadable local JSON or remain preview-only.
- Whether to include websocket-like fake timeline playback in this iteration or defer to a follow-up change.
