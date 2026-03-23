# 最终回归与验收记录（T8-9.6）

## 1. 基本信息

- 执行时间：`2026-03-24 07:26:00 +08:00`
- 工作区：`D:/code/fanbianyi/.worktrees/wh-t8-delivery`
- 分支：`codex/pmcov-t8-delivery`
- 执行基线提交：`7efb686`

## 2. 已执行命令与结果摘要

| 序号 | 命令 | 结果 | 摘要 |
| --- | --- | --- | --- |
| 1 | `npm --prefix frontend test` | FAIL（已处理） | `check:copy` 通过，但缺少 `vitest` 可执行文件。 |
| 2 | `npm --prefix frontend install` | PASS | 安装前端依赖（新增 368 packages）。 |
| 3 | `npm --prefix frontend test` | PASS | 前端测试通过：`3 files / 9 tests`。 |
| 4 | `mvn -f backend/pom.xml test` | FAIL（已处理） | 当前环境无本机 Maven（`mvn` 未识别）。 |
| 5 | `docker run --rm -v ${PWD}/backend:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn test` | PASS | 后端测试通过：`Tests run: 13, Failures: 0, Errors: 0`。 |
| 6 | `python -m pip install -r nlp-service/requirements.txt` | PASS | 依赖已满足（无阻断错误）。 |
| 7 | `python -m pytest nlp-service/tests -q` | PASS | NLP 测试通过：`3 passed`，有 12 条 Deprecation warning（不阻断）。 |
| 8 | `openspec validate pm-demo-chinese-overhaul-v1` | PASS | OpenSpec 校验通过：`Change ... is valid`。 |

## 3. 验收结论

- 回归结论：通过（`frontend + backend + nlp + openspec` 均有可执行通过记录）。
- 风险备注：
  - NLP 依赖存在 Python 3.16 前瞻性弃用告警，建议后续统一升级 `starlette/fastapi/pytest-asyncio` 配置。
  - 9.5 截图当前为“可复现步骤 + 占位清单”方案，待补齐真实前后截图文件。
