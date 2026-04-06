## Session Notes

- 2026-04-06 cleanup: frontend verification reran successfully, but Java/Vue LSP and Maven remain unavailable in this environment, so backend execution evidence is still environment-blocked.

- 2026-04-06: `lsp_diagnostics` could only validate TypeScript files in this environment; Vue (`vue-language-server`) and Java (`jdtls`) servers are configured but not installed.
- 2026-04-06: Backend Maven verification is environment-blocked because `mvn` is unavailable, no `mvnw` wrapper exists in the repo, and Docker is installed but the daemon is not running.

- 2026-04-06: Task 2 backend verification remains partially environment-blocked: `lsp_diagnostics` cannot run on Java files because `jdtls` is configured but not installed, and targeted backend tests cannot be executed because `mvn` is not installed and there is no `mvnw` wrapper.
