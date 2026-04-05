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
| Backend | `BACKEND_PORT` `NLP_BASE_URL` `ONEBOUND_BASE_URL` `ONEBOUND_API_KEY` `ONEBOUND_API_SECRET` `ONEBOUND_DEFAULT_PLATFORM` |
| NLP | `NLP_PORT` |
| Frontend | `FRONTEND_PORT` `VITE_API_BASE_URL` `VITE_SHOW_CHAOS_MODULE` `PUBLIC_PORT` |

### 2.2 默认值
- backend 端口：`8080`
- frontend 开发端口：`5175`
- nlp-service 端口：`8000`
- postgres 端口：`5432`
- redis 端口：`6379`
- 前端默认 API 基地址：`http://localhost:8080`
- chaos 模块默认：隐藏（`VITE_SHOW_CHAOS_MODULE=false`）

### 2.3 OneBound 配置
- 要走真实 OneBound 同步，必须配置 `ONEBOUND_API_KEY`。
- `ONEBOUND_API_SECRET` 为空时，后端会退回使用 API Key 作为 secret。
- 如果缺少 key，`provider=onebound` 的同步会失败。

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

## 4. 使用

### 4.1 初始化演示数据

```bash
curl -X POST http://localhost:8080/api/v1/demo-data/init \
  -H "Content-Type: application/json" \
  -d '{"productCode":"demo-earphone"}'
```

关键行为：
- 目标评论数固定为 `100`
- 可重复执行
- 重复执行时会更新已有演示评论，而不是无限新增

### 4.2 查看后端健康状态

```bash
curl http://localhost:8080/api/v1/health
```

### 4.3 演示登录
- 前端演示账号：`wxy`
- 前端演示密码：`123456`
- 这是前端硬编码演示登录，不是后端鉴权。

### 4.4 真实 OneBound 同步

```bash
curl -X POST http://localhost:8080/api/v1/sync/start \
  -H "Content-Type: application/json" \
  -d '{"provider":"onebound","platform":"taobao","targetProductCode":"600530677643"}'
```

注意：
- 只有 `provider=onebound` 会触发真实外部调用。
- 其他 provider 当前只会创建 `QUEUED` 任务。

### 4.5 看板已实现模块
- 总览
- 问题
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
- `compare` 当前返回静态数据。
- 所有 `showcase/*` 接口当前返回占位演示数据，`implemented=false`。
- analysis job 当前只会创建 `QUEUED` 记录。

## 5. 排障

### 5.1 前端打不开或接口全失败
检查：
- backend 是否已启动：访问 `GET /api/v1/health`
- `VITE_API_BASE_URL` 是否指向正确后端
- frontend 请求默认超时为 10 秒；超时会在趋势/词云等模块显示 timeout 提示

### 5.2 OneBound 同步失败
检查：
- `.env` 是否配置 `ONEBOUND_API_KEY`
- `ONEBOUND_API_SECRET` 是否为空或错误
- `provider` 是否确实传了 `onebound`
- 查询 `GET /api/v1/sync/jobs/{id}` 查看 `status`、`fetchedCount`、`errorMessage`

### 5.3 趋势图或词云没有数据
检查：
- 是否先执行了 `POST /api/v1/demo-data/init`
- 查询参数 `productCode`、`aspect` 是否落在当前代码支持范围内
- 若无数据，后端会返回空数组并带 `notice`

### 5.4 验证模块没有结果
检查：
- 是否已经创建动作
- `actionId` 是否存在
- 演示评论是否足够形成前后对比

### 5.5 登录失败
检查：
- 账号是否为 `wxy`
- 密码是否为 `123456`
- 账号长度是否至少 3，密码长度是否至少 6

## 6. 日志

当前代码中可确认的日志点：
- `DemoDataInitializationService`：初始化演示数据时输出 `info`
- `InsightQueryService`：聚合失败时输出 `warn`
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
- analysis
- demo-data init
- compare
- trends
- wordcloud
- actions
- validation
- showcase 占位接口

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

### 9.1 演示数据维护
- 需要刷新演示数据时，重复调用 `POST /api/v1/demo-data/init` 即可。
- 该操作是幂等更新，不是无限追加。

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
