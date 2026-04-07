## Session Notes

- 2026-04-06 cleanup: frontend verification reran successfully, but Java/Vue LSP and Maven remain unavailable in this environment, so backend execution evidence is still environment-blocked.

- 2026-04-06: `lsp_diagnostics` could only validate TypeScript files in this environment; Vue (`vue-language-server`) and Java (`jdtls`) servers are configured but not installed.
- 2026-04-06: Backend Maven verification is environment-blocked because `mvn` is unavailable, no `mvnw` wrapper exists in the repo, and Docker is installed but the daemon is not running.

- 2026-04-06: Task 2 backend verification remains partially environment-blocked: `lsp_diagnostics` cannot run on Java files because `jdtls` is configured but not installed, and targeted backend tests cannot be executed because `mvn` is not installed and there is no `mvnw` wrapper.
- 2026-04-06: Task 3 backend verification remains environment-blocked for the same reason: Java diagnostics cannot run without `jdtls`, and `mvn -f backend/pom.xml test` cannot execute because Maven is not installed and the repository has no `mvnw` wrapper.
- 2026-04-06: Task 4 frontend verification passed, but Vue-file diagnostics remain unavailable because `vue-language-server` is not installed, so `.vue` validation in this session is limited to Vitest + Vite build evidence.
- 2026-04-06: Task 4 backend verification is still environment-blocked: Java diagnostics cannot run without `jdtls`, and `mvn -f backend/pom.xml test` still fails immediately because `mvn` is not installed in this environment.
- 2026-04-06: Task 5 backend verification is still environment-blocked: `jdtls` is not installed for Java diagnostics, Maven is unavailable (`mvn` command not found), there is no `mvnw` wrapper, and the locally installed Java runtime is 17 while `backend/pom.xml` declares Java 21.
- 2026-04-06: Task 7 frontend verification passed (`npm test`, `npm run build`), but Vue diagnostics remain blocked because `vue-language-server` is not installed; Java diagnostics remain blocked because `jdtls` is not installed.
- 2026-04-06: Task 7 backend execution is still environment-blocked because `mvn test` fails immediately with `mvn : The term 'mvn' is not recognized`, and the repo still has no `mvnw` wrapper.
- 2026-04-06: Task 8 frontend verification passed with TypeScript diagnostics, Vitest, and Vite build, but Vue-file diagnostics remain environment-blocked because `vue-language-server` is not installed in this session.
- 2026-04-06: Task 10 frontend verification passed (`npm test`, `npm run build`) and TypeScript diagnostics are clean, but Java diagnostics still cannot run because `jdtls` is not installed, and Vue-file diagnostics still cannot run because `vue-language-server` is not installed.
- 2026-04-06: Task 10 backend execution is still blocked because `mvn -f backend/pom.xml test` fails with `mvn` command not found, there is no `mvnw` wrapper in the repo, and the locally available Java runtime is 17 while `backend/pom.xml` targets Java 21.
- 2026-04-06: Task 11 verification passed for TypeScript/Python diagnostics, `npm test`, `npm run build`, and `python -m pytest nlp-service/tests -q`, but backend Java execution remains blocked because `mvn test` still fails with command-not-found, `backend/` still has no `mvnw*`, and the installed JRE remains 17 while `backend/pom.xml` targets Java 21.
- 2026-04-07: Task 12 Java verification remains environment-blocked in the same way: `lsp_diagnostics` cannot run because `jdtls` is not installed, `mvn -f backend/pom.xml test` fails with command-not-found, there is still no `backend/mvnw`, and the locally installed JRE is 17 while the backend still targets Java 21.

- 2026-04-07: Task 13 is doc-only, but Markdown files still cannot be validated with `lsp_diagnostics` in this environment because no `.md` LSP server is configured; consistency checking in-session is limited to direct file review plus stale-wording grep.
