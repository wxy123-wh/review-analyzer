# 改造前后截图对比说明（T8-9.5）

## 1. 当前状态

- 本次提交未附“改造前后”真实截图文件。
- 原因：
  - 当前任务限制在 `codex/pmcov-t8-delivery` 工作区与分支执行，无法在同一工作区内安全切换到改造前版本并稳定复现全套页面。
  - 当前执行环境以终端回归为主，未配置现成的前端可视化自动截图基线脚本。

## 2. 可复现截图步骤

1. 准备两个环境（或两个工作区）
- `before`：改造前基线分支/提交
- `after`：`codex/pmcov-t8-delivery`

2. 各环境启动命令
- `Copy-Item .env.example .env`
- `docker compose up --build`
- `curl -X POST http://localhost:8080/api/v1/demo-data/init -H "Content-Type: application/json" -d '{"productCode":"demo-earphone"}'`
- 打开 `http://localhost:5175`

3. 统一截图规范
- 分辨率：`1920x1080`
- 浏览器缩放：`100%`
- 同一浏览器（建议 Chrome 最新稳定版）
- 文件命名：`before-*.png` / `after-*.png`

4. 指定截图场景
- 登录页：展示登录表单与三怪物区域
- 趋势图：进入“趋势图”并显示图表主体
- 词云：进入“词云”并显示关键词云
- 动作验证：先在“动作”创建演示动作，再切“验证”截图结果

## 3. 占位清单（需补齐）

| 场景 | 改造前 | 改造后 |
| --- | --- | --- |
| 登录页 | `docs/evidence/t8-delivery-screenshots/before-login.png` | `docs/evidence/t8-delivery-screenshots/after-login.png` |
| 趋势图 | `docs/evidence/t8-delivery-screenshots/before-trends.png` | `docs/evidence/t8-delivery-screenshots/after-trends.png` |
| 词云 | `docs/evidence/t8-delivery-screenshots/before-wordcloud.png` | `docs/evidence/t8-delivery-screenshots/after-wordcloud.png` |
| 动作验证 | `docs/evidence/t8-delivery-screenshots/before-validation.png` | `docs/evidence/t8-delivery-screenshots/after-validation.png` |

## 4. 验收判定口径

- 至少补齐上表 8 张截图后，9.5 可视证据视为“完整”。
- 若仅有 `after` 无 `before`，需在评审中明确“缺少基线图”并单独记录风险。
