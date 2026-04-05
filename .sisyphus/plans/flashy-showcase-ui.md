# Flashy Showcase UI Upgrade Plan

## TL;DR
> **Summary**: Rebuild the current Vue single-shell frontend into a cohesive dark premium showcase experience by extracting reusable shell structure, introducing a tokenized visual system, and applying controlled high-impact motion across login, navigation, panels, charts, and data surfaces.
> **Deliverables**:
> - full-site dark premium visual system for the existing frontend shell
> - upgraded login/auth experience aligned with current animated baseline
> - redesigned shell, sidebar, cards, tables, charts, and showcase panels
> - motion/accessibility/performance hardening and Vitest coverage updates
> **Effort**: XL
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 2 → 3/4/5/6/7/8 → 9/10/11 → 12

## Context
### Original Request
在 GitHub 上寻找相似的炫技项目，把优秀代码思路加装到当前项目中，并把前端做得十分华丽、充满动图，整体效果至少达到现有登录界面的视觉水准。

### Interview Summary
- Scope: upgrade the **entire frontend**, not just login or first screen
- Borrowing strategy: adapt concrete implementation patterns, but convert them to the repo’s Vue architecture; no direct copying
- Visual direction: **dark premium futuristic**
- Performance mode: **balanced** — heavy motion only in focal regions, restrained elsewhere

### Metis Review (gaps addressed)
- Defaulted to **keep the existing single-shell app model**; do not introduce router/store/auth rewrites in this plan
- Defaulted to **responsive support across desktop/tablet/mobile**, with desktop receiving the richest motion treatment
- Defaulted to **mandatory reduced-motion support** and deterministic test-mode rendering
- Defaulted to **truthful demo-auth presentation**; do not imply production-grade security/trust beyond current demo behavior
- Guarded against over-animating dense data areas, GSAP lifecycle leaks, and style-token/scoped-CSS conflicts

## Work Objectives
### Core Objective
Transform the current Vue frontend into a visually unified dark premium showcase product while preserving existing module behavior, demo authentication flow, and data-panel functionality.

### Deliverables
- Tokenized dark theme foundation usable by all existing Vue components
- Extracted shell/layout structure from the monolithic `frontend/src/App.vue`
- Upgraded `LoginGate.vue` and `AnimatedCharacters.vue` composition with premium auth-shell treatment
- Upgraded sidebar, dashboard shell, shared cards/lists/tables, charts, and showcase panels
- Reusable motion utilities/patterns for focal hero/panel transitions
- Reduced-motion, responsive, and performance hardening
- Expanded frontend tests for shell/auth/state regressions

### Definition of Done (verifiable conditions with commands)
- `npm --prefix frontend test` passes with updated component/integration coverage
- `npm --prefix frontend run build` completes successfully
- Unauthenticated state renders only the upgraded auth experience
- Authenticated state preserves module switching and existing data-fetch/error behavior
- Reduced-motion mode disables non-essential motion without breaking layout or interactions
- All existing modules render in the upgraded dark premium style without unreadable tables/charts

### Must Have
- Reuse existing Vue/Vite stack and current data contracts
- Keep login as the visual flagship and extend its language across the full app
- Use inspiration sources selectively:
  - Dub / Infisical for auth shell and premium dark composition
  - Trigger.dev for login trust-layout microinteraction patterns
  - Appwrite for hero/marquee/motion primitive ideas
- Introduce a clear motion hierarchy with heavy motion limited to focal areas
- Keep every implementation task coupled with automated verification and explicit agent QA scenarios

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- No router migration, store migration, backend changes, or auth model rewrite
- No direct cloning of external repo DOM, branding, wording, or CSS
- No over-animation of tables, dense data regions, or persistent navigation
- No misleading “secure enterprise” trust styling that exceeds the repo’s demo-auth truth
- No one-off premium components that bypass the shared token system

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.
- Test decision: **tests-after** using existing **Vitest + Vue Test Utils + jsdom** infrastructure
- QA policy: Every task includes agent-executed scenarios plus deterministic evidence artifacts
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: architecture foundation, design tokens, motion foundation, regression test scaffold

Wave 2: login/auth redesign, shell/sidebar redesign, shared primitives restyle, data surfaces restyle, showcase panel restyle

Wave 3: responsive/reduced-motion hardening, performance cleanup, regression verification, evidence capture

### Dependency Matrix (full, all tasks)
- 1 blocks 3, 4, 5, 6, 7, 8, 11
- 2 blocks 3, 4, 5, 6, 7, 8, 9, 10
- 3 depends on 1, 2, 9
- 4 depends on 1, 2, 9
- 5 depends on 1, 2
- 6 depends on 1, 2, 5
- 7 depends on 1, 2, 5, 9
- 8 depends on 1, 2, 5, 9
- 9 depends on 2
- 10 depends on 3, 4, 5, 6, 7, 8, 9
- 11 depends on 1, 3, 4, 5, 6, 7, 8, 10
- 12 depends on 10, 11

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 4 tasks → unspecified-high, quick
- Wave 2 → 6 tasks → visual-engineering, unspecified-high
- Wave 3 → 2 tasks → deep, unspecified-high

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Extract shell structure from `App.vue`

  **What to do**: Split the current monolithic shell in `frontend/src/App.vue` into explicit layout regions and reusable shell components/composables while preserving the existing single-shell interaction model. Keep current module activation, auth gating, `allModules`, `activeModule`, `activateModule()`, and `ensureModuleData()` behavior unchanged; the purpose is to create safe extension points for the redesign, not to introduce routing or state rewrites.
  **Must NOT do**: Do not add `vue-router`, global store migration, backend API changes, or new business logic.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: architectural extraction across the current app shell
  - Skills: `[]` — why needed: existing repo patterns are sufficient
  - Omitted: `["code-simplifier"]` — why not needed: this is structure-preserving extraction, not open-ended refactoring

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: [3, 4, 5, 6, 7, 8, 11] | Blocked By: []

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/App.vue` — current auth gate, module registry, module-switch rendering, and data-loading behavior to preserve
  - Entry: `frontend/src/main.ts` — mount path and global CSS import
  - API/Type: `frontend/src/api/client.ts` — module data dependencies and request boundaries
  - Test: `frontend/src/test/App.spec.ts` — current auth/shell behavior that must keep passing

  **Acceptance Criteria** (agent-executable only):
  - [ ] `frontend/src/App.vue` no longer contains the full visual shell implementation inline, but still owns the top-level auth/module orchestration
  - [ ] `npm --prefix frontend test -- App.spec.ts` passes or equivalent scoped Vitest run covering app-shell behavior passes
  - [ ] `npm --prefix frontend run build` succeeds after extraction

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Existing auth gate still controls shell visibility
    Tool: Playwright
    Steps: Launch frontend dev server; open app; confirm only login screen renders before authentication; submit demo credentials; confirm shell appears without intermediate broken state
    Expected: No shell flash before login; successful transition into authenticated shell
    Evidence: .sisyphus/evidence/task-1-shell-structure.png

  Scenario: Module switching still loads the correct content
    Tool: Playwright
    Steps: After login, click each sidebar/module control that existed before refactor; observe active-state changes and corresponding panel region updates
    Expected: Every module still renders the correct branch with no blank or duplicated panel state
    Evidence: .sisyphus/evidence/task-1-shell-structure-error.png
  ```

  **Commit**: YES | Message: `refactor(frontend): extract app shell structure for premium redesign` | Files: [`frontend/src/App.vue`, `frontend/src/components/**`, `frontend/src/composables/**`, `frontend/src/test/App.spec.ts`]

- [x] 2. Introduce a tokenized dark premium visual foundation

  **What to do**: Establish a shared theme/token layer for colors, surfaces, borders, glow treatments, gradients, spacing, radii, typography scale, shadows, z-index, and motion timings. Apply tokens in a way that works with the repo’s current Vue SFC scoped-CSS approach and `frontend/src/styles.css`, so later tasks can restyle components consistently without ad hoc values.
  **Must NOT do**: Do not add Tailwind, replace the styling model, or hardcode one-off premium values directly into each component.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: design-system and theming foundation work
  - Skills: `[]` — why needed: local patterns are custom and lightweight
  - Omitted: `["brand-guidelines"]` — why not needed: this is visual token work, not copywriting

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [3, 4, 5, 6, 7, 8, 9, 10] | Blocked By: []

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/LoginGate.vue` — current highest-quality visual palette and gradient/glow language
  - Pattern: `frontend/src/components/AnimatedCharacters.vue` — current premium accent/motion styling baseline
  - Pattern: `frontend/src/components/ShowcasePipelinePanel.vue` — panel, badge, and card treatment patterns
  - Pattern: `frontend/src/components/ShowcaseChaosPanel.vue` — richer premium panel composition
  - Styling: `frontend/src/styles.css` — current global style entry point
  - External: `https://github.com/dubinc/dub` — auth-shell gradients and masked background inspiration
  - External: `https://github.com/Infisical/infisical` — premium dark auth background/card composition inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] A single shared token/theme source exists and is consumed by at least login, shell chrome, and one shared primitive
  - [ ] No newly redesigned component introduces unexplained duplicate raw color/gradient constants when a token could be used instead
  - [ ] `npm --prefix frontend run build` succeeds with the new theme layer in place

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Dark premium tokens propagate consistently
    Tool: Playwright
    Steps: Open login, authenticated shell, and one showcase panel; inspect the visible surfaces, borders, badges, and backgrounds across all three regions
    Expected: Shared dark palette, surface hierarchy, glow treatment, and border language appear consistent rather than page-specific
    Evidence: .sisyphus/evidence/task-2-theme-foundation.png

  Scenario: Theme layer does not break baseline rendering
    Tool: Bash
    Steps: Run `npm --prefix frontend run build`
    Expected: Build succeeds with no CSS/module resolution failures introduced by the token layer
    Evidence: .sisyphus/evidence/task-2-theme-foundation.txt
  ```

  **Commit**: YES | Message: `feat(frontend): add premium dark theme foundation` | Files: [`frontend/src/styles.css`, `frontend/src/components/**`, `frontend/src/theme/**`]

- [x] 3. Redesign the login/auth experience as the flagship entry flow

  **What to do**: Upgrade `frontend/src/components/LoginGate.vue` and related auth presentation into a dark premium, high-drama login experience. Keep the current demo-auth semantics truthful, but recompose the page using Dub/Infisical/Trigger.dev-inspired layout ideas: stronger side-panel composition, layered backgrounds, social-proof/trust-style framing adapted to demo context, and refined transitions. Keep `AnimatedCharacters.vue` as part of the flagship visual identity, either by elevating it or integrating it into the new composition.
  **Must NOT do**: Do not imply real enterprise trust/security guarantees, do not replace the existing demo-auth behavior with new backend/auth flows, and do not make the login form inaccessible or motion-dependent.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: flagship auth redesign with controlled animation and layout work
  - Skills: `[]` — why needed: repo-specific adaptation is more important than generic frontend patterns
  - Omitted: `["brand-guidelines"]` — why not needed: copy changes are secondary and should remain minimal

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/LoginGate.vue` — current auth form structure and demo credential behavior
  - Pattern: `frontend/src/components/AnimatedCharacters.vue` — existing animation identity to preserve/elevate
  - Test: `frontend/src/test/App.spec.ts` — login behavior expectations
  - External: `https://github.com/dubinc/dub` — auth layout, side panel, gradient background inspiration
  - External: `https://github.com/triggerdotdev/trigger.dev` — login trust-layout and microinteraction inspiration
  - External: `https://github.com/Infisical/infisical` — premium dark auth card/background inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] Demo login with current credentials still works end-to-end
  - [ ] Login screen visually matches the new dark premium system while remaining clearly usable with keyboard only
  - [ ] Reduced-motion mode leaves the auth page readable and functional without animation dependencies
  - [ ] `npm --prefix frontend test -- App.spec.ts` passes or equivalent auth-gate coverage passes

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Premium login flow remains usable
    Tool: Playwright
    Steps: Open app; verify dark premium auth shell; type `wxy` / `123456`; submit; wait for shell transition
    Expected: Login succeeds, transition is polished, and no visual overlap or broken layered background blocks interaction
    Evidence: .sisyphus/evidence/task-3-login-flow.png

  Scenario: Invalid credentials still fail cleanly
    Tool: Playwright
    Steps: Open app; enter invalid username/password; submit
    Expected: Auth remains on the login screen, error state is readable, and no success transition occurs
    Evidence: .sisyphus/evidence/task-3-login-flow-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): redesign premium auth experience` | Files: [`frontend/src/components/LoginGate.vue`, `frontend/src/components/AnimatedCharacters.vue`, `frontend/src/test/App.spec.ts`]

- [x] 4. Rebuild the authenticated shell and navigation chrome

  **What to do**: Apply the new dark premium system to the post-login shell: sidebar, navigation state, top-level dashboard framing, section headers, and module-transition experience. Keep the current module selection model intact but make the shell feel like a coherent product surface rather than a collection of stitched panels. Use subtle motion for nav and shell transitions rather than login-level spectacle.
  **Must NOT do**: Do not hide functionality behind ornamental navigation, do not animate persistent chrome aggressively, and do not break module discoverability.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: shell UX, layout, and navigation polish
  - Skills: `[]` — why needed: execution depends on this repo’s current shell structure
  - Omitted: `["code-simplifier"]` — why not needed: clarity matters, but this task is primarily visual/systemic

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/App.vue` — current authenticated shell and module navigation behavior
  - Pattern: `frontend/src/components/StatusCard.vue` — dashboard surface tone reference to upgrade
  - External: `https://github.com/dubinc/dub` — premium app-shell and auth-adjacent chrome inspiration
  - External: `https://github.com/calcom/cal.com` — subtle premium enterprise-app microinteraction inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] Sidebar/navigation remains fully usable and correctly highlights active modules
  - [ ] Authenticated shell uses the shared premium dark token language across frame, nav, section headers, and panel surfaces
  - [ ] Module transitions are visually improved without introducing sluggishness or interaction lag
  - [ ] `npm --prefix frontend run build` succeeds after shell redesign

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Navigation chrome stays readable and responsive
    Tool: Playwright
    Steps: Log in; click through every shell navigation item; observe active state, panel container transitions, and header consistency
    Expected: Navigation remains clear, active state is obvious, and shell styling stays consistent across modules
    Evidence: .sisyphus/evidence/task-4-shell-chrome.png

  Scenario: Persistent chrome is not over-animated
    Tool: Playwright
    Steps: Log in; repeatedly switch between multiple modules in quick succession
    Expected: Sidebar/header transitions remain subtle and do not stack, lag, or leave ghost states
    Evidence: .sisyphus/evidence/task-4-shell-chrome-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): redesign app shell and navigation chrome` | Files: [`frontend/src/App.vue`, `frontend/src/components/**`]

- [x] 5. Restyle shared cards, lists, and small dashboard primitives

  **What to do**: Upgrade reusable primitives such as `StatusCard.vue`, `ActionList.vue`, and `ValidationList.vue` to the tokenized dark premium system. Standardize panel surfaces, badge treatments, hierarchy, iconography spacing, and subtle hover/entrance effects so downstream screens inherit a premium baseline without each panel inventing its own style.
  **Must NOT do**: Do not introduce one-off visual rules per panel, and do not sacrifice scanability for decorative effects.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: shared primitive redesign with reusable styling impact
  - Skills: `[]` — why needed: repo-local component system is custom
  - Omitted: `["brand-guidelines"]` — why not needed: this is primarily structure and styling

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [6, 7, 8, 10, 11] | Blocked By: [1, 2]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/StatusCard.vue` — status-card baseline
  - Pattern: `frontend/src/components/ActionList.vue` — list/action styling baseline
  - Pattern: `frontend/src/components/ValidationList.vue` — state/result list baseline
  - Pattern: `frontend/src/components/ShowcasePipelinePanel.vue` — premium badge/card composition reference
  - Pattern: `frontend/src/utils/showcaseCopy.ts` — wording/state normalization constraints

  **Acceptance Criteria** (agent-executable only):
  - [ ] Shared primitives visibly use the common premium token system rather than local ad hoc styles
  - [ ] Hover/focus/selection states remain readable and keyboard-accessible
  - [ ] Existing loading/empty/error/result semantics remain intact after restyling
  - [ ] Relevant Vitest coverage for affected shared primitives passes

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Shared primitives feel visually unified
    Tool: Playwright
    Steps: Navigate to screens showing status cards, action lists, and validation results; inspect surface hierarchy, spacing, and state styling
    Expected: These primitives share one dark premium visual language and remain easy to scan
    Evidence: .sisyphus/evidence/task-5-shared-primitives.png

  Scenario: Focus and dense text remain usable
    Tool: Playwright
    Steps: Use keyboard navigation to tab through interactive list elements and buttons
    Expected: Focus states are visible and decorative styling does not obscure labels or actions
    Evidence: .sisyphus/evidence/task-5-shared-primitives-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): restyle shared dashboard primitives` | Files: [`frontend/src/components/StatusCard.vue`, `frontend/src/components/ActionList.vue`, `frontend/src/components/ValidationList.vue`, `frontend/src/components/**`]

- [x] 6. Redesign dense data tables for premium readability

  **What to do**: Restyle `IssueTable.vue` and `CompareTable.vue` so they fit the dark premium system while preserving dense data readability. Improve headers, row states, spacing, contrast, separators, and empty/error framing. Use motion only for small state transitions, not for persistent table content.
  **Must NOT do**: Do not animate table rows continuously, reduce text contrast, or turn data surfaces into decorative set pieces.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: dense data styling requires careful readability tradeoffs
  - Skills: `[]` — why needed: current table contracts are repo-specific
  - Omitted: `["code-simplifier"]` — why not needed: design/readability decisions dominate

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 5]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/IssueTable.vue` — issue table contract and current table structure
  - Pattern: `frontend/src/components/CompareTable.vue` — comparison table contract and current data density
  - Pattern: `frontend/src/components/StatusCard.vue` — surrounding panel tone reference
  - External: `https://github.com/calcom/cal.com` — subtle enterprise-grade UI polish inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] Tables are visually aligned with the premium dark system without reducing row/header readability
  - [ ] Empty/error/data states still render correctly
  - [ ] No persistent heavy animation is introduced into dense data areas
  - [ ] `npm --prefix frontend run build` succeeds after table redesign

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Data tables remain readable after redesign
    Tool: Playwright
    Steps: Open modules rendering issue/comparison tables; inspect headers, row stripes/separators, badges, and long text handling
    Expected: Rows remain easy to scan, contrast is sufficient, and premium styling does not obscure data
    Evidence: .sisyphus/evidence/task-6-data-tables.png

  Scenario: Empty or error table states remain truthful
    Tool: Playwright
    Steps: Trigger or simulate a module state with no data or failed data load for a table-backed panel
    Expected: Empty/error presentation matches the premium theme but still clearly communicates the underlying state
    Evidence: .sisyphus/evidence/task-6-data-tables-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): redesign premium data tables` | Files: [`frontend/src/components/IssueTable.vue`, `frontend/src/components/CompareTable.vue`, `frontend/src/components/**`]

- [x] 7. Upgrade chart panels and data-viz containers

  **What to do**: Bring `TrendList.vue` and `WordCloudPanel.vue` into the dark premium system, including chart container framing, headings, legends/support text, loading/empty/error states, and restrained entrance transitions. Preserve G2Plot lifecycle correctness and test fallback behavior. Add animation only where it supports the reveal of the panel rather than competing with the chart itself.
  **Must NOT do**: Do not stack external animation systems on top of chart lifecycles, break resize handling, or reduce label readability.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: chart-shell design plus lifecycle-sensitive motion
  - Skills: `[]` — why needed: existing G2Plot integration is local to this repo
  - Omitted: `["code-simplifier"]` — why not needed: visual/lifecycle fidelity is the core task

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 5, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/TrendList.vue` — existing line-chart integration and fallback handling
  - Pattern: `frontend/src/components/WordCloudPanel.vue` — existing word-cloud integration and fallback handling
  - Test: `frontend/src/test/TrendList.spec.ts` — chart panel behavior expectations
  - Test: `frontend/src/test/WordCloudPanel.spec.ts` — chart panel behavior expectations
  - External: `https://github.com/appwrite/website` — restrained motion-primitives inspiration for reveal layers

  **Acceptance Criteria** (agent-executable only):
  - [ ] Chart panels visually match the premium dark system while preserving chart clarity
  - [ ] Existing chart fallback/test behavior remains deterministic under Vitest/jsdom
  - [ ] Resizing, loading, empty, and error paths continue to work correctly
  - [ ] `npm --prefix frontend test -- TrendList.spec.ts WordCloudPanel.spec.ts` passes or equivalent scoped chart tests pass

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Chart panels feel premium without visual conflict
    Tool: Playwright
    Steps: Navigate to trend and word-cloud modules; observe panel reveal, chart framing, and final readability once mounted
    Expected: Panel motion is tasteful, chart content stays legible, and no overlay blocks the plot area
    Evidence: .sisyphus/evidence/task-7-chart-panels.png

  Scenario: Chart fallback path remains stable
    Tool: Bash
    Steps: Run `npm --prefix frontend test -- TrendList.spec.ts WordCloudPanel.spec.ts`
    Expected: Tests pass without flaking from animation or lifecycle regressions
    Evidence: .sisyphus/evidence/task-7-chart-panels-error.txt
  ```

  **Commit**: YES | Message: `feat(frontend): upgrade premium chart panels` | Files: [`frontend/src/components/TrendList.vue`, `frontend/src/components/WordCloudPanel.vue`, `frontend/src/test/TrendList.spec.ts`, `frontend/src/test/WordCloudPanel.spec.ts`]

- [x] 8. Recompose all showcase panels into staged premium experiences

  **What to do**: Upgrade `ShowcasePipelinePanel.vue`, `ShowcaseAgentArenaPanel.vue`, `ShowcaseChaosPanel.vue`, and `ShowcaseReportCenter.vue` into the most theatrical post-login modules. Use the shared token system plus Appwrite-inspired motion primitives to create staged reveals, spotlighted metrics, polished cards, and high-end section framing. Keep content truthful to the current demo/product semantics.
  **Must NOT do**: Do not make every showcase panel visually unrelated, and do not introduce motion that obscures content or makes the panels feel like marketing pages disconnected from the app shell.

  **Recommended Agent Profile**:
  - Category: `visual-engineering` — Reason: richest module-level showcase redesign in the app
  - Skills: `[]` — why needed: component-level adaptation is specific to current repo structure
  - Omitted: `["brand-guidelines"]` — why not needed: only minor wording adjustments should be made

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 5, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/ShowcasePipelinePanel.vue` — staged card composition baseline
  - Pattern: `frontend/src/components/ShowcaseAgentArenaPanel.vue` — showcase table/panel baseline
  - Pattern: `frontend/src/components/ShowcaseChaosPanel.vue` — richest current dramatic panel reference
  - Pattern: `frontend/src/components/ShowcaseReportCenter.vue` — interactive showcase module baseline
  - Utility: `frontend/src/utils/showcaseCopy.ts` — copy/state normalization constraints
  - External: `https://github.com/appwrite/website` — marquee/hero/motion primitive inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] All showcase panels visibly belong to one premium system while each retains a distinct focal treatment
  - [ ] Interactive states and content hierarchy remain understandable after the redesign
  - [ ] Showcase panels do not visually drift away from the shell theme
  - [ ] `npm --prefix frontend run build` succeeds after showcase redesign

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Showcase panels become the dramatic core of the app
    Tool: Playwright
    Steps: Open each showcase module in sequence; capture first-view impression, card hierarchy, and transition consistency
    Expected: Panels feel premium and theatrical, but still read as product modules rather than detached landing pages
    Evidence: .sisyphus/evidence/task-8-showcase-panels.png

  Scenario: Interactive showcase states remain understandable
    Tool: Playwright
    Steps: In any showcase module with selectable/report-preview behavior, interact with the controls repeatedly
    Expected: State changes remain clear, transitions complete cleanly, and no decorative effect hides interaction results
    Evidence: .sisyphus/evidence/task-8-showcase-panels-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): redesign showcase panels as premium experiences` | Files: [`frontend/src/components/ShowcasePipelinePanel.vue`, `frontend/src/components/ShowcaseAgentArenaPanel.vue`, `frontend/src/components/ShowcaseChaosPanel.vue`, `frontend/src/components/ShowcaseReportCenter.vue`, `frontend/src/utils/showcaseCopy.ts`]

- [x] 9. Add reusable motion primitives and lifecycle-safe animation hooks

  **What to do**: Centralize motion patterns used across the redesigned shell so heavy effects are intentional and maintainable. Define reusable reveal, hover, spotlight, subtle shell-transition, and optional marquee/text-emphasis primitives that can be applied to Vue components without duplicating GSAP setup or leaving unmanaged timelines/listeners. Ensure motion can be disabled in reduced-motion and test environments.
  **Must NOT do**: Do not sprinkle one-off GSAP instances across the app, do not create motion that only works in one component, and do not tie core usability to animation completion.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: cross-cutting motion architecture and lifecycle safety
  - Skills: `[]` — why needed: the repo already uses GSAP and needs disciplined extension
  - Omitted: `["code-simplifier"]` — why not needed: this is a reusable systems task, not a local cleanup task

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [3, 4, 7, 8, 10] | Blocked By: [2]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/AnimatedCharacters.vue` — current GSAP usage and lifecycle considerations
  - Pattern: `frontend/src/components/LoginGate.vue` — auth transition context where premium motion begins
  - External: `https://github.com/appwrite/website` — reusable motion primitive inspiration
  - External: `https://github.com/calcom/cal.com` — subtle premium microinteraction inspiration

  **Acceptance Criteria** (agent-executable only):
  - [ ] Motion helpers/primitives exist in a reusable form rather than as copy-pasted component logic
  - [ ] Reduced-motion and test mode can suppress non-essential animation deterministically
  - [ ] No leaked timelines/listeners occur during auth transition or module switching
  - [ ] `npm --prefix frontend run build` succeeds with the motion layer in place

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Shared motion primitives work across multiple surfaces
    Tool: Playwright
    Steps: Trigger login transition, shell module switch, and one showcase panel reveal in a single session
    Expected: Motion style feels consistent and no transition leaves duplicated layers or stale animated states
    Evidence: .sisyphus/evidence/task-9-motion-primitives.png

  Scenario: Reduced-motion path disables non-essential motion
    Tool: Playwright
    Steps: Emulate `prefers-reduced-motion: reduce`; reload app; exercise login and one module transition
    Expected: Layout and interactions still work, while decorative motion is removed or heavily reduced
    Evidence: .sisyphus/evidence/task-9-motion-primitives-error.png
  ```

  **Commit**: YES | Message: `feat(frontend): add reusable premium motion primitives` | Files: [`frontend/src/components/**`, `frontend/src/motion/**`, `frontend/src/utils/**`]

- [x] 10. Harden responsiveness, accessibility, and performance boundaries

  **What to do**: Tune the redesigned experience for desktop, tablet, and mobile; enforce visible focus states, sufficient contrast, reduced-motion support, and interaction safety under decorative layers. Remove or tone down any effect that makes dense content unreadable or hurts shell responsiveness. Ensure heavy motion remains limited to focal areas under the chosen balanced-performance mode.
  **Must NOT do**: Do not treat accessibility/performance as post-hoc polish, and do not keep visually impressive effects that break real usage.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: cross-cutting hardening requiring careful tradeoff resolution
  - Skills: `[]` — why needed: repo behavior must stay correct under multiple constraints
  - Omitted: `["security-review"]` — why not needed: this is UX/perf/accessibility hardening, not a security audit

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [11, 12] | Blocked By: [3, 4, 5, 6, 7, 8, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/components/LoginGate.vue` — flagship auth responsiveness and keyboard usability
  - Pattern: `frontend/src/App.vue` — shell responsiveness and module switching
  - Pattern: `frontend/src/components/IssueTable.vue` — dense content readability constraints
  - Pattern: `frontend/src/components/TrendList.vue` — chart container readability constraints
  - Pattern: `frontend/src/components/ShowcaseChaosPanel.vue` — highest drama panel that must still remain usable

  **Acceptance Criteria** (agent-executable only):
  - [ ] Desktop, tablet, and mobile layouts remain functional and visually coherent across auth, shell, tables, charts, and showcase panels
  - [ ] Reduced-motion, visible focus states, and acceptable contrast are present in redesigned surfaces
  - [ ] Decorative layers do not block clicks, text selection, or chart/table readability
  - [ ] `npm --prefix frontend run build` and full frontend test suite both succeed after hardening

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Responsive premium layout remains usable
    Tool: Playwright
    Steps: Capture auth screen, shell, a table module, and a showcase module at desktop, tablet, and mobile viewport sizes
    Expected: Layout adapts cleanly, no critical overlap occurs, and the premium style remains coherent at each size
    Evidence: .sisyphus/evidence/task-10-hardening.png

  Scenario: Accessibility and interaction safety hold under decorative layers
    Tool: Playwright
    Steps: Tab through auth and shell controls; verify focus visibility; test reduced-motion; interact with chart/table areas under layered visuals
    Expected: Keyboard navigation stays visible, reduced-motion works, and visual overlays never block core interactions
    Evidence: .sisyphus/evidence/task-10-hardening-error.png
  ```

  **Commit**: YES | Message: `fix(frontend): harden premium ui responsiveness and accessibility` | Files: [`frontend/src/components/**`, `frontend/src/styles.css`, `frontend/src/motion/**`]

- [x] 11. Expand Vitest coverage for redesigned shell, auth, and visual states

  **What to do**: Update and expand frontend tests so the redesign is protected by deterministic component/integration checks. Cover auth gating, module switching, shared primitive states, chart fallbacks, and reduced-motion or motion-suppression logic where practical. Prefer assertions on structure, state, and accessibility hooks over fragile animation timing details.
  **Must NOT do**: Do not rely on snapshots that flake due to motion, and do not write tests that depend on real browser animation timing.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: broad regression coverage tied to the redesign
  - Skills: `[]` — why needed: local Vitest patterns already exist in the repo
  - Omitted: `["find-bugs"]` — why not needed: this task is to codify expected behavior, not branch-review bug hunting

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [12] | Blocked By: [1, 3, 4, 5, 6, 7, 8, 10]

  **References** (executor has NO interview context — be exhaustive):
  - Test: `frontend/src/test/App.spec.ts` — app-shell and auth-gate baseline
  - Test: `frontend/src/test/TrendList.spec.ts` — chart behavior pattern
  - Test: `frontend/src/test/WordCloudPanel.spec.ts` — chart behavior pattern
  - Pattern: `frontend/src/components/LoginGate.vue` — redesigned auth state expectations
  - Pattern: `frontend/src/App.vue` — module-switching expectations
  - Pattern: `frontend/src/components/IssueTable.vue` — dense state rendering expectations

  **Acceptance Criteria** (agent-executable only):
  - [ ] Tests cover redesigned auth gate, authenticated shell/module switching, at least one shared primitive family, and chart fallback behavior
  - [ ] Tests avoid animation-timing flake by asserting state/DOM outcomes instead of effect timing
  - [ ] `npm --prefix frontend test` passes in full

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Full frontend suite passes after redesign
    Tool: Bash
    Steps: Run `npm --prefix frontend test`
    Expected: Full Vitest suite passes with the redesigned UI in place
    Evidence: .sisyphus/evidence/task-11-vitest.txt

  Scenario: Reduced-motion/test-mode logic is deterministic
    Tool: Bash
    Steps: Run the relevant scoped Vitest tests covering auth/shell/motion-suppression helpers
    Expected: Tests pass without depending on real-time animation completion
    Evidence: .sisyphus/evidence/task-11-vitest-error.txt
  ```

  **Commit**: YES | Message: `test(frontend): expand redesign regression coverage` | Files: [`frontend/src/test/App.spec.ts`, `frontend/src/test/TrendList.spec.ts`, `frontend/src/test/WordCloudPanel.spec.ts`, `frontend/src/test/**`]

- [x] 12. Capture final evidence and align the full experience to one visual language

  **What to do**: Perform the final executor-owned pass that resolves residual inconsistencies across auth, shell, primitives, tables, charts, and showcase modules. Capture final evidence artifacts for the redesigned journey and ensure the experience reads as one product, not a sequence of individually upgraded components.
  **Must NOT do**: Do not introduce new visual motifs in the cleanup pass; only resolve inconsistencies, polish, and evidence capture gaps.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: cross-cutting final alignment and evidence pass
  - Skills: `[]` — why needed: this is repo-specific final integration work
  - Omitted: `["create-pr"]` — why not needed: PR creation is outside execution of this plan

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [] | Blocked By: [10, 11]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `frontend/src/App.vue` — final shell coherence check
  - Pattern: `frontend/src/components/LoginGate.vue` — flagship entry coherence check
  - Pattern: `frontend/src/components/ShowcaseChaosPanel.vue` — highest-drama module coherence check
  - Test: `frontend/src/test/App.spec.ts` — baseline regression safety

  **Acceptance Criteria** (agent-executable only):
  - [ ] Final walkthrough evidence exists for unauthenticated entry, authenticated shell, at least one dense data screen, and one showcase-heavy screen
  - [ ] No visible premium-theme inconsistency remains across the main experience path
  - [ ] `npm --prefix frontend test` and `npm --prefix frontend run build` both succeed in the final state

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: End-to-end premium walkthrough is coherent
    Tool: Playwright
    Steps: Capture a full journey from login screen to shell overview to a table-heavy module to a showcase-heavy module
    Expected: The experience feels visually continuous, and no section appears stylistically left behind
    Evidence: .sisyphus/evidence/task-12-final-alignment.png

  Scenario: Final verification commands stay green
    Tool: Bash
    Steps: Run `npm --prefix frontend test`; then run `npm --prefix frontend run build`
    Expected: Both commands complete successfully in the final integrated state
    Evidence: .sisyphus/evidence/task-12-final-alignment.txt
  ```

  **Commit**: YES | Message: `chore(frontend): finalize premium showcase alignment and evidence` | Files: [`frontend/src/**`, `.sisyphus/evidence/**`]

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [ ] F1. Plan Compliance Audit — oracle
- [ ] F2. Code Quality Review — unspecified-high
- [ ] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [ ] F4. Scope Fidelity Check — deep

## Commit Strategy
- Commit 1: extract shell/layout primitives with no visual behavior change
- Commit 2: add token/theme foundation and shared visual helpers
- Commit 3: redesign auth/login experience
- Commit 4: redesign shell/sidebar/top chrome
- Commit 5: redesign shared cards/lists/tables
- Commit 6: redesign charts and showcase panels
- Commit 7: motion/performance/accessibility hardening
- Commit 8: tests, evidence, and cleanup

## Success Criteria
- The app feels like one cohesive dark premium product instead of a collection of panels
- Login remains the visual flagship, but the post-login shell now matches its quality bar
- Motion adds drama in focal areas without making data-heavy screens noisy or sluggish
- Existing functionality and demo flows remain correct under the new presentation
- The implementation can be executed file-by-file without architectural ambiguity
