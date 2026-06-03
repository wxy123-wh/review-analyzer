# 电商口碑分析系统

一个基于Spring Boot + Vue 3的智能化电商评论分析系统，集成AI驱动的情感分析、聚类分析和主题挖掘功能。

## ✨ 核心功能

- 🎯 **智能评论分析**：自动分析评论情感、关键词、话题
- 🔍 **维度分析**：多维度产品口碑分析（质量、服务、物流等）
- 📊 **聚类分析**：基于机器学习的评论自动聚类
- 🤖 **AI总结**：使用大语言模型生成智能分析报告
- 📈 **趋势分析**：时间序列情感趋势可视化
- 🔎 **语义搜索**：基于向量数据库的智能评论检索
- 📱 **PWA支持**：移动端适配，支持离线访问

## 🆕 最新更新 (v0.0.2)

### 自动聚类功能
- ✅ 应用启动时自动为所有产品执行聚类分析
- ✅ 异步执行，不阻塞应用启动
- ✅ 支持配置开关控制

### 统一打包部署
- ✅ 一键打包脚本，集成前端、后端和NLP服务
- ✅ 自动生成启动/停止脚本
- ✅ 完整的发布包结构

详见 [DEPLOYMENT.md](./DEPLOYMENT.md)

## 🚀 快速开始

### 环境要求

- **Java**: 17+
- **Node.js**: 18+
- **Python**: 3.8+
- **MySQL**: 8.0+
- **PowerShell**: Windows PowerShell 5.1+ 或 PowerShell Core 7+

### 📋 数据库初始化（首次运行必须）

在开始之前，需要先配置 MySQL 数据库：

```sql
-- 1. 创建数据库
CREATE DATABASE reputation_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 创建用户
CREATE USER 'repu'@'localhost' IDENTIFIED BY 'repu123456';

-- 3. 授权
GRANT ALL PRIVILEGES ON reputation_mvp.* TO 'repu'@'localhost';
FLUSH PRIVILEGES;
```

> **注意**: 数据库表结构会在应用首次启动时自动创建，无需手动导入 SQL 文件。

---

## 🎯 方式一：生产部署（推荐）

适用于正式部署、演示环境或快速体验完整功能。

### 步骤 1: 一键编译打包

在项目根目录执行：

```powershell
# 执行打包脚本（自动完成前端构建、后端编译、NLP服务集成）
.\build-release.ps1
```

此脚本会自动完成：
- ✅ 安装前端依赖并构建 (npm install + npm run build)
- ✅ 将前端打包结果部署到后端静态资源目录
- ✅ 编译后端 Spring Boot 项目 (Maven clean package)
- ✅ 复制 NLP 服务文件到发布包
- ✅ 生成启动脚本 (`start.ps1`) 和停止脚本 (`stop.ps1`)
- ✅ 在 `release/` 目录生成完整发布包

### 步骤 2: 启动应用

```powershell
# 进入发布目录
cd release

# 启动应用（会自动启动 NLP 服务和后端服务）
.\start.ps1
```

启动脚本会自动：
1. 检查 Python 环境
2. 创建 Python 虚拟环境（如不存在）
3. 安装 NLP 服务依赖
4. 后台启动 NLP 服务 (http://localhost:8000)
5. 启动 Spring Boot 后端 (http://localhost:8081)

### 步骤 3: 访问应用

打开浏览器访问：**http://localhost:8081**

使用默认账号登录（见下方「默认账号」章节）

### 步骤 4: 停止应用

```powershell
# 在 release 目录下执行
.\stop.ps1
```

---

## 🛠️ 方式二：开发模式

适用于前后端分离开发、调试和功能开发。

### 步骤 1: 启动 NLP 服务

打开终端 1，执行：

```powershell
# 进入 NLP 服务目录
cd nlp_service

# 创建虚拟环境
python -m venv .venv

# 激活虚拟环境
.\.venv\Scripts\Activate.ps1

# 安装依赖
pip install -r requirements.txt

# 启动 NLP 服务
python main.py
```

NLP 服务运行在: **http://localhost:8000**

健康检查: http://localhost:8000/health

### 步骤 2: 启动后端服务

**打开新终端 2**，执行：

```powershell
# 进入后端目录
cd backend

# 使用 Maven Wrapper 启动 Spring Boot
.\mvnw.cmd spring-boot:run
```

后端运行在: **http://localhost:8081**

API 文档: http://localhost:8081/doc.html

### 步骤 3: 启动前端开发服务器

**打开新终端 3**，执行：

```powershell
# 进入前端目录
cd frontend

# 安装依赖（首次运行）
npm install

# 启动开发服务器
npm run dev
```

前端运行在: **http://localhost:5173**

### 开发模式访问

- **前端开发页面**: http://localhost:5173
- **后端 API**: http://localhost:8081/api
- **NLP 服务**: http://localhost:8000

---

## 📦 完整编译命令汇总

### 前端编译

```powershell
cd frontend
npm install          # 安装依赖
npm run dev          # 开发模式
npm run build        # 生产构建（输出到 dist/）
npm run preview      # 预览生产构建
```

### 后端编译

```powershell
cd backend
.\mvnw.cmd clean                    # 清理
.\mvnw.cmd compile                  # 编译
.\mvnw.cmd package                  # 打包（生成 JAR）
.\mvnw.cmd package -DskipTests      # 打包（跳过测试）
.\mvnw.cmd spring-boot:run          # 直接运行
```

### NLP 服务

```powershell
cd nlp_service
python -m venv .venv                         # 创建虚拟环境
.\.venv\Scripts\Activate.ps1                 # 激活虚拟环境
pip install -r requirements.txt              # 安装依赖
python main.py                               # 启动服务
```

### 一键打包部署

```powershell
# 项目根目录执行
.\build-release.ps1                          # 生成 release/ 发布包
cd release
.\start.ps1                                  # 启动全部服务
.\stop.ps1                                   # 停止全部服务
```

---

## 🚢 部署到服务器

### 本地打包

```powershell
# 在开发机器上执行打包
.\build-release.ps1
```

### 传输到服务器

将 `release/` 整个目录复制到目标服务器

### 服务器配置

1. **确保已安装环境**：Java 17+、Python 3.8+、MySQL 8.0+
2. **配置数据库**（执行上述数据库初始化 SQL）
3. **修改配置**（如需要）：
   - 数据库连接：编辑 `application.yml`（需要解压 JAR 或通过环境变量覆盖）
   - 端口配置：默认 8081（后端）、8000（NLP）

### 启动服务

```powershell
cd release
.\start.ps1
```

### 使用 Cloudflare Tunnel 或 ngrok 公网访问

```powershell
# 方式 1: 使用 Cloudflare Tunnel
cloudflared tunnel --url http://localhost:8081

# 方式 2: 使用 ngrok
ngrok http 8081
```

完整部署文档: [DEPLOYMENT.md](./DEPLOYMENT.md)

## 📂 项目结构

```
wh/
├── backend/                 # Spring Boot后端
│   ├── src/main/java/      # Java源代码
│   └── src/main/resources/ # 配置文件和静态资源
├── frontend/               # Vue 3前端
│   ├── src/               # 前端源代码
│   └── public/            # 公共资源
├── nlp_service/           # Python NLP服务
│   ├── main.py           # FastAPI服务入口
│   ├── clustering.py     # 聚类算法
│   └── topic_modeling.py # 主题建模
├── build-release.ps1      # 一键打包脚本
└── DEPLOYMENT.md          # 部署文档
```

## 🏗️ 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **数据库**: MySQL 8.0 + Spring Data JPA
- **安全**: JWT身份验证
- **分词**: jieba中文分词
- **AI**: OpenAI API (DeepSeek模型)
- **搜索**: Elasticsearch

### 前端
- **框架**: Vue 3 + Vite
- **UI库**: Element Plus
- **图表**: ECharts
- **状态管理**: Pinia
- **路由**: Vue Router
- **PWA**: Vite PWA Plugin

### NLP服务
- **框架**: FastAPI
- **机器学习**: scikit-learn
- **聚类**: KMeans + TF-IDF
- **分词**: jieba

## 👤 默认账号

| 用户名 | 密码   | 角色   | 权限说明       |
|--------|--------|--------|----------------|
| pm     | 123456 | PM     | 产品经理看板   |
| market | 123456 | MARKET | 市场分析看板   |
| ops    | 123456 | OPS    | 运营看板       |

## ⚙️ 配置说明

### 后端配置

`backend/src/main/resources/application.yml`

```yaml
server:
  port: 8081  # 后端端口

spring:
  datasource:  # 数据库配置
    url: jdbc:mysql://localhost:3306/reputation_mvp
    username: repu
    password: repu123456

nlp:
  service:
    url: http://localhost:8000  # NLP服务地址

clustering:
  auto-run-on-startup: true  # 自动聚类开关
```

### 前端配置

`frontend/.env`

```env
VITE_APP_TITLE=电商口碑分析系统
VITE_API_BASE_URL=/api
```

## 📊 功能模块

### 1. 评论管理
- Excel批量导入评论
- 评论列表查看和筛选
- 情感标注和维度标注

### 2. 分析看板
- **维度分析**: 多维度情感分布统计
- **趋势分析**: 时间序列情感变化趋势
- **关键词分析**: 词云可视化
- **聚类分析**: 评论主题聚类
- **主题建模**: LDA主题提取

### 3. AI分析
- 智能生成分析报告
- 改进建议生成
- 事件影响分析
- 竞品对比分析

### 4. 语义搜索
- 基于Elasticsearch的全文检索
- 向量化语义相似度搜索

## 🔧 开发指南

### 添加新的分析维度

1. 在 `backend/src/main/resources/schema.sql` 中添加维度数据
2. 更新 `AspectEntity.java` 实体类
3. 在前端 `AnalysisService.js` 中更新API调用

### 自定义聚类参数

修改 `nlp_service/clustering.py`:

```python
def cluster_reviews(docs, k_min=2, k_max=8):
    # 调整k_min和k_max改变聚类数量范围
    ...
```

### 禁用自动聚类

编辑 `application.yml`:

```yaml
clustering:
  auto-run-on-startup: false
```

## 📝 API文档

启动后端后访问: http://localhost:8081/doc.html

查看完整的API文档（Knife4j界面）

## 🐛 常见问题

### Q: NLP服务连接失败？
**A**: 确保Python NLP服务已启动在8000端口，访问 http://localhost:8000/health 检查健康状态

### Q: 聚类结果为空？
**A**: 
1. 检查数据库中是否有评论数据
2. 确认NLP服务正常运行
3. 查看后端日志中的聚类执行情况

### Q: 前端页面空白？
**A**: 检查前端是否正确打包到 `backend/src/main/resources/static/` 目录

### Q: Git 提交时卡顿或速度慢？
**A**: 
1. `.gitignore` 已优化，排除以下内容：
   - Python 虚拟环境 (`.venv/`)
   - 发布构建产物 (`release/`)
   - 大型可执行文件 (`cloudflared.exe`)
   - 数据文件 (`data/`)
2. 如果已经提交了这些文件，需要清理历史：
   ```powershell
   # 从 Git 缓存中移除（保留本地文件）
   git rm -r --cached release/ data/ .venv/
   git rm --cached cloudflared.exe
   git commit -m "chore: 移除不应跟踪的大文件"
   ```

更多问题参考: [DEPLOYMENT.md](./DEPLOYMENT.md#常见问题)

## 📦 打包说明

### 生成发布包

```powershell
./build-release.ps1
```

生成的发布包位于 `release/` 目录，包含：
- `backend.jar` - Spring Boot应用（包含前端）
- `nlp_service/` - Python NLP服务
- `start.ps1` - 启动脚本
- `stop.ps1` - 停止脚本
- `README.md` - 部署说明

### 发布包部署

1. 复制 `release/` 目录到目标服务器
2. 配置MySQL数据库
3. 运行 `./start.ps1`

## 📄 License

本项目仅供学习和研究使用。

## 👥 贡献

欢迎提交Issue和Pull Request！

## 📮 联系方式

如有问题或建议，请提交Issue。

---

**版本**: v0.0.2  
**最后更新**: 2026-01-15
