# 操作手册

本文只依据当前仓库代码、配置、脚本和测试编写。

配套文档：
- 代码文档：`docs/代码文档.md`
- 接口文档：`docs/接口文档.md`

## 1. 安装

### 1.1 代码仓库内已确认的运行时
- Docker / Docker Compose：由 `docker-compose.yml` 和 `docker-compose.prod.yml` 可确认。
- Java 21：由 `backend/pom.xml` 可确认。
- Maven：由 `backend/pom.xml` 与 `backend/Dockerfile` 可确认。
- Node.js 20：由 `frontend/Dockerfile` 与 `frontend/Dockerfile.prod` 可确认。
- Python 3.11：由 `nlp-service/Dockerfile` 可确认。

### 1.2 本地安装依赖命令
- backend
  - `mvn -f backend/pom.xml test`
- frontend
  - `npm --prefix frontend install`
  - `npm --prefix frontend test`
- nlp-service
  - `python -m pip install -r nlp-service/requirements.txt`
  - `python -m pytest nlp-service/tests -q`

## 2. 配置

### 2.1 环境变量
复制模板：

```bash
cp .env.example .env
```

或 PowerShell：

```powershell
Copy-Item .env.example .env
```

当前代码中可确认的变量如下：

| 分类 | 变量 |
|---|---|
| Postgres | `POSTGRES_DB` `POSTGRES_USER` `POSTGRES_PASSWORD` `POSTGRES_PORT` |
| Redis | `REDIS_PORT` |
| Backend | `BACKEND_PORT` `NLP_BASE_URL` `CRAWLER_BASE_URL` `ONEBOUND_BASE_URL` `ONEBOUND_API_KEY` `ONEBOUND_API_SECRET` `ONEBOUND_DEFAULT_PLATFORM` |
| NLP | `NLP_PORT` |
| Frontend | `FRONTEND_PORT` `VITE_API_BASE_URL` `VITE_SHOW_CHAOS_MODULE` `VITE_INTERNAL_ACCESS_USERNAME` `VITE_INTERNAL_ACCESS_PASSWORD` `VITE_INTERNAL_ACCESS_DISPLAY_NAME` `VITE_INTERNAL_ACCESS_HINT` `PUBLIC_PORT` |

### 2.2 默认值
- backend 端口：`8080`
- frontend 开发端口：`5175`
- nlp-service 端口：`8000`
- crawler 服务端口：`8010`
- postgres 端口：`5432`
- redis 端口：`6379`
- 前端默认 API 基地址：`http://localhost:8080`
- chaos 模块默认：隐藏（`VITE_SHOW_CHAOS_MODULE=false`）
- 内部访问门禁默认账号：`wxy`
- 内部访问门禁默认密码：`123456`

### 2.3 OneBound 配置
- 要走真实 OneBound 同步，必须配置 `ONEBOUND_API_KEY`。
- `ONEBOUND_API_SECRET` 为空时，后端会退回使用 API Key 作为 secret。
- 如果缺少 key，`provider=onebound` 的同步会失败。

### 2.4 内部访问门禁
- 前端登录页使用 `VITE_INTERNAL_ACCESS_*` 环境变量控制展示账号、密码、显示名和提示语。
- 这是一层面向内部首发验收与真实数据环境的前端门禁，不是后端鉴权。
- 如果未配置这些变量，前端才会回退到默认值。

## 3. 启动

### 3.1 开发栈启动

```bash
docker compose up --build
```

当前代码中的服务启动顺序可确认如下：
1. `postgres`
2. `redis`
3. `nlp-service`
4. `backend`
5. `frontend`

### 3.2 开发栈访问地址
- Frontend：`http://localhost:5175`
- Backend Health：`http://localhost:8080/api/v1/health`
- NLP Health：`http://localhost:8000/health`

### 3.3 生产栈启动

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

生产栈中：
- 只有 frontend 对外暴露端口，默认 `80`
- backend 与 nlp-service 只在容器网络内部暴露
- Nginx 会把 `/api/` 代理到 `http://backend:8080`

### 3.4 首发路径
当前首发路径仍以真实导入数据为主：
1. 启动整套开发栈。
2. 用 `crawler/jd_reviews.py` 采集真实评论，或准备同结构的真实评论 JSONL。
3. 在前端“采集配置”里输入商品链接、商品编码和 UX 标签组合，或使用 `crawler/jd_reviews.py` 直接采集。
4. 调用 `crawler/import_reviews.py` 或 `POST /api/v1/reviews/import` 把真实评论写入同一个 `productCode`。
5. 调用 `POST /api/v1/analysis/start` 触发同步分析与结果物化。
6. 打开前端，通过内部访问门禁进入看板。
7. 在问题、卖点、对比、趋势图、词云、动作、验证与 showcase 模块查看结果。

外部来源接入仍是第二轨，主要提供同步透明度、原始评论入库与后续 handoff 准备，不是当前首发必经路径。

## 4. 使用

### 4.0 前端商品采集、UX 标签配置与导入

参考京东采集仓库已下载到：

```text
.tmp/external/Crawling-User-Reviews-from-JD.com
```

当前项目新增了自己的采集接入目录：

```text
crawler/
```

推荐新主链路是：

```text
商品链接
  -> 前端“采集配置”
  -> 绑定商品 UX 标签组合
  -> POST /api/v1/crawl/start
  -> crawler 服务采集 JSONL
  -> POST /api/v1/reviews/import 导入同一 productCode
  -> POST /api/v1/analysis/start
  -> 前端看板按 UX 二级标签展示
```

先启动 crawler 服务，后端才能从前端请求转发真实采集任务：

```powershell
python -m uvicorn crawler.service:app --host 127.0.0.1 --port 8010
```

前端“采集配置”模块做三件事：

- 读取或编辑当前商品绑定的 UX 一级/二级标签组合。
- 把商品链接和 `productCode` 提交给后端 `/api/v1/crawl/start`。
- 采集完成后，继续把 JSONL 导入同一个 `productCode`，新评论会 upsert 到旧商品评论集合中。

重要边界：当前后端会启动和刷新 crawler 服务任务，但采集完成后的 JSONL 仍需要通过 `crawler/import_reviews.py` 或 `POST /api/v1/reviews/import` 导入数据库。导入后再启动分析，才能让新评论进入问题、趋势、词云和卖点结果。

安装采集依赖：

```powershell
python -m pip install -r crawler/requirements.txt
```

采集京东评论：

```powershell
python crawler/jd_reviews.py `
  --product-id 100127936932 `
  --product-code jd-100127936932 `
  --category bluetooth-earphone `
  --max-packets 20
```

脚本会打开浏览器，需要人工正常登录并进入评论区域。出现验证码、安全验证或风控提示时，脚本只会停止或提示人工处理，不会绕过平台机制。

保真清洗评论：

```powershell
python pipeline/clean_reviews.py `
  --input crawler/output/raw_reviews.jsonl `
  --output pipeline/output/cleaned_reviews.jsonl `
  --removed-output pipeline/output/removed_reviews.jsonl `
  --summary-output pipeline/output/cleaning_summary.json
```

清洗只去除无效 JSON、空评论、HTML/实体/异常空白和精确重复，不过滤默认好评、短评论、低信息评论，也不按情感或评分过滤。这样可以去掉技术噪声，同时不改变真实评论频率。

导入后端：

```powershell
python crawler/import_reviews.py `
  --input pipeline/output/cleaned_reviews.jsonl `
  --cleaning-summary pipeline/output/cleaning_summary.json `
  --backend http://localhost:8080 `
  --product-code jd-100127936932
```

导入后触发分析：

```powershell
curl -X POST http://localhost:8080/api/v1/analysis/start `
  -H "Content-Type: application/json" `
  -d "{\"productCode\":\"jd-100127936932\",\"taxonomyId\":1}"
```

### 4.1 直接导入真实评论

```bash
curl -X POST http://localhost:8080/api/v1/reviews/import \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "local-jsonl",
    "platform": "jd",
    "productCode": "jd-100127936932",
    "cleaningSummary": {
      "rawCount": 1,
      "cleanedCount": 1,
      "removedCount": 0,
      "htmlCleanedCount": 0,
      "exactDuplicateCount": 0,
      "emptyContentCount": 0,
      "invalidJsonCount": 0
    },
    "reviews": [
      {
        "source": "jd",
        "sourceReviewId": "jd-001",
        "category": "bluetooth-earphone",
        "rating": 2,
        "content": "蓝牙连接偶尔断开，通话也有杂音。",
        "reviewTime": "2026-06-01T10:20:00Z",
        "skuInfo": "黑色 标准版"
      }
    ]
  }'
```

关键行为：
- 这是当前首发主路径的数据入口，数据必须来自真实采集脚本、第三方数据源或人工整理的真实评论。
- `reviews` 必填且不能为空；后端不会自动生成评论。
- 可重复执行，同一 `sourceReviewId` 会更新已有评论，而不是无限新增。
- `cleaningSummary` 可选；携带后会写入 `data_quality_runs`，可通过 `GET /api/v1/data-quality` 查看清洗统计。
- 导入只写 `reviews_raw`，不会自动补跑分析；如需让问题、对比、趋势图、词云进入最新窗口，还要再调用 `POST /api/v1/analysis/start`。
- 启动分析后，系统会把每条评论的情感、UX 一级标签、UX 二级标签、标准原因和证据写入语义标签表，并通过 `GET /api/v1/positive-insights` 从正面评论的 UX 高频标签中生成卖点。`aspect` 字段仍保留为旧图表和旧接口兼容字段，新的聚合主键是 `uxSecondaryLabel`。

### 4.2 查看后端健康状态

```bash
curl http://localhost:8080/api/v1/health
```

### 4.3 内部访问门禁
- 默认前端访问账号：`wxy`
- 默认前端访问密码：`123456`
- 默认显示名：`内部体验账号`
- 默认提示语：`仅用于内部首发验收与真实数据环境访问。`
- 这些值可由 `VITE_INTERNAL_ACCESS_*` 环境变量覆盖。
- 这是前端门禁，不是后端鉴权。

### 4.4 真实 OneBound 同步

```bash
curl -X POST http://localhost:8080/api/v1/sync/start \
  -H "Content-Type: application/json" \
  -d '{"provider":"onebound","platform":"taobao","targetProductCode":"600530677643"}'
```

注意：
- `provider=onebound` 会立即尝试外部调用，并把同步透明度写入 `sync_jobs`。
- `legacy-seed（已不支持）` 仍停留在真实导入数据第一轨，不会自动触发外部抓取。
- 外部来源链路当前重点是原始评论持久化、状态透明和分析 handoff 准备，不代表已经具备首发级自动化闭环。

### 4.5 看板已实现模块
- 总览
- 问题
- 卖点
- 对比
- 趋势图
- 词云
- 动作
- 验证
- 流水线
- 智能体
- 可解释性
- 报告中心
- 韧性演练（默认隐藏，需显式开启）

### 4.6 当前范围说明
- 真实导入数据首发仍是主路径，问题、对比、趋势图、词云、验证与大部分 showcase 语义都围绕真实导入数据分析结果展开。
- 数据清洗是保真清洗，只解释数据质量和技术噪声处理，不改变情感分布和评论样本结构。
- analysis job 现在会在 `POST /api/v1/analysis/start` 内同步经历 `QUEUED -> RUNNING -> SUCCEEDED/FAILED`，并在成功时物化查询结果；若 NLP 不可用或返回无效载荷，会以降级成功方式回退到真实评论本地规则回退。
- 正面卖点来自 `sentiment=POSITIVE` 且有有效 `uxSecondaryLabel` 的评论聚合，默认好评等评论仍保留在原始数据和情感统计中；如果没有识别到有效 UX 标签，只是不进入卖点候选。
- 相同 `productCode` 可以重复导入新评论；同一 `source + sourceReviewId` 会更新已有评论。为了避免 UX 标签配置或新评论无法映射到旧结果，分析任务会重新执行并按当前商品绑定的 taxonomy 重新物化。
- `compare` 现在读取物化后的真实对比数据，不再是静态返回。
- `showcase/*` 接口已经实现，返回 `implemented=true`，并基于 v1 运行态、物化结果和查询结果给出状态与说明。
- 可解释性当前是 `LIVE`，解释的是固定权重问题得分拆解，不是模型归因。
- 韧性演练模块反映最近同步、分析、物化运行态信号，可能因 `VITE_SHOW_CHAOS_MODULE` 被隐藏，也可能在后端无可用信号时显示运行态不可用。
- 外部来源 / OneBound 仍是第二轨骨架，强调同步透明度、原始评论落库与 handoff 准备，不承诺自动接入首发主链路。

## 5. 排障

### 5.1 前端打不开或接口全失败
检查：
- backend 是否已启动：访问 `GET /api/v1/health`
- `VITE_API_BASE_URL` 是否指向正确后端
- `VITE_INTERNAL_ACCESS_*` 是否与当前门禁配置一致
- frontend 请求默认超时为 10 秒；超时会在趋势/词云等模块显示 timeout 提示

### 5.2 OneBound 同步失败
检查：
- `.env` 是否配置 `ONEBOUND_API_KEY`
- `ONEBOUND_API_SECRET` 是否为空或错误
- `provider` 是否确实传了 `onebound`
- 查询 `GET /api/v1/sync/jobs/{id}` 查看 `status`、`fetchedCount`、`errorMessage`、`analysisHandoffStatus`、`analysisHandoffNote`

### 5.3 趋势图或词云没有数据
检查：
- 是否先执行了 `POST /api/v1/reviews/import`
- 导入后是否执行了 `POST /api/v1/analysis/start`，因为趋势图、词云和对比都读取物化结果
- 查询参数 `productCode`、`uxSecondaryLabel` 是否落在当前商品绑定的 UX 标签组合内；`aspect` 仍可作为旧兼容参数，但不再是主分析维度
- 若无数据或运行降级，后端会返回显式 `state` 与 `notice`，前端再按该语义显示空态、降级态、超时态或错误态

### 5.4 验证模块没有结果
检查：
- 是否已经创建动作
- `actionId` 是否存在
- 真实评论是否足够形成前后对比
- 若样本不足，接口仍可能成功返回，但 `summary` 会明确提示暂无法形成稳定结论

### 5.5 登录失败
检查：
- 账号密码是否与当前 `VITE_INTERNAL_ACCESS_*` 配置一致
- 账号长度是否至少 3，密码长度是否至少 6

## 6. 日志

当前代码中可确认的日志点：
- `ReviewImportService`：导入真实评论时会创建并更新 `sync_jobs` 状态记录
- `InsightQueryService`：物化查询失败时输出 `warn`
- 前端禁用词检查脚本会向控制台输出违规项

无法从当前代码确认：
- 日志文件路径
- 日志轮转
- 日志保留策略
- 集中式日志采集

## 7. 部署

### 7.1 开发部署
- 直接使用 `docker-compose.yml`。

### 7.2 生产部署
- 直接使用 `docker-compose.prod.yml`。
- frontend 生产镜像会先构建静态文件，再用 Nginx 提供服务。
- `/api/` 由 Nginx 反向代理到 backend。

### 7.3 部署前检查
- `.env` 是否存在
- `PUBLIC_PORT` 是否符合目标环境要求
- `VITE_INTERNAL_ACCESS_*` 是否符合内部真实数据环境要求
- 若要使用 OneBound，相关凭据是否已配置

无法从当前代码确认：
- Kubernetes / Helm / Terraform / ECS 任务定义
- CI/CD 流水线
- 云厂商安全组或负载均衡配置

## 8. 调试

### 8.1 backend

```bash
mvn -f backend/pom.xml test
```

测试可确认覆盖：
- health
- sync
- analysis 同步执行、降级成功、结果复用与物化
- reviews import
- compare 物化读取与状态语义
- trends
- wordcloud
- actions
- validation
- showcase 真实 v1 状态接口

### 8.2 frontend

```bash
npm --prefix frontend test
```

注意：
- 该命令会先执行禁用词检查脚本
- `README.md` 也在检查范围内；具体禁用词请以 `frontend/scripts/check-banned-copy-terms.mjs` 中的当前实现为准，避免把被禁用词直接写回本文档

### 8.3 nlp-service

```bash
python -m pytest nlp-service/tests -q
```

## 9. 维护

### 9.1 真实评论数据维护
- 需要刷新真实评论数据时，重复调用 `POST /api/v1/reviews/import` 即可。
- 该操作是幂等更新，不是无限追加。
- 若希望查询结果同步刷新，再调用一次 `POST /api/v1/analysis/start`。

### 9.2 文案维护
- 修改 frontend 文案或本文件后，运行：

```bash
npm --prefix frontend test
```

- 以确保禁用词检查通过。

### 9.3 数据库维护
- 当前代码依赖 PostgreSQL 表结构与 backend 启动自检。
- 如果数据库为空，可通过 compose 挂载 SQL 和 backend 启动自建双重确保核心表存在。

### 9.4 无法从当前代码确认
- 无法从当前代码确认备份恢复方案。
- 无法从当前代码确认定时清理、归档、自动巡检任务。
- 无法从当前代码确认监控、告警、指标采集方案。
