# 电商口碑分析系统 - 部署指南

## 项目说明

这是一个**前后端打包运行**的Spring Boot项目，前端Vue应用会被打包并集成到后端的静态资源目录中。同时集成了Python NLP服务用于聚类和主题分析。

## 环境要求

- **Java**: 17+
- **Node.js**: 18+
- **Python**: 3.8+ （后端会自动管理NLP服务）
- **MySQL**: 8.0+

> [!NOTE]
> **NLP服务自动启动**: 从 v0.0.3 版本开始，Python NLP服务已集成到后端中。
> 当您启动Spring Boot应用时，NLP服务会自动启动，无需额外操作！

## 数据库配置

1. 创建数据库和用户：
```sql
CREATE DATABASE reputation_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'repu'@'localhost' IDENTIFIED BY 'repu123456';
GRANT ALL PRIVILEGES ON reputation_mvp.* TO 'repu'@'localhost';
FLUSH PRIVILEGES;
```

2. 数据库表会在启动时自动创建（通过`schema.sql`）

## 🚀 生产环境部署（推荐）

使用一键打包脚本创建完整发布包：

### 1. 执行打包
```powershell
./build-release.ps1
```

该脚本会自动完成：
- ✅ 构建前端
- ✅ 部署前端到后端
- ✅ 打包后端JAR
- ✅ 集成NLP服务
- ✅ 生成启动/停止脚本
- ✅ 创建完整发布包

### 2. 部署发布包
打包完成后会生成 `release` 目录，包含：
```
release/
  ├── backend.jar              # Spring Boot可执行jar
  ├── nlp_service/             # Python NLP服务
  │   ├── main.py
  │   ├── requirements.txt
  │   └── ...
  ├── start.ps1                # 启动脚本
  ├── stop.ps1                 # 停止脚本
  └── README.md                # 部署说明
```

### 3. 启动应用
```powershell
cd release
./start.ps1
```

启动脚本会自动：
- 检查Python环境
- 安装NLP服务依赖
- 后台启动NLP服务
- 启动Spring Boot应用

### 4. 访问应用
打开浏览器访问：
```
http://localhost:8081
```

### 5. 停止应用
```powershell
./stop.ps1
```

---

## 🛠️ 开发模式运行

适用于开发调试，前后端分离运行：

### 1. 启动NLP服务
```powershell
cd nlp_service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py
# NLP服务运行在 http://localhost:8000
```

### 2. 启动后端
```powershell
cd backend
./mvnw.cmd spring-boot:run
# 后端运行在 http://localhost:8081
```

### 3. 启动前端开发服务器
```powershell
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173
```

---

## ⚡ 自动聚类功能

### 功能说明
从 v0.0.2 版本开始，应用启动后会**自动为所有产品执行聚类分析**，无需手动触发。

### 配置选项
在 `backend/src/main/resources/application.yml` 中配置：

```yaml
clustering:
  auto-run-on-startup: true  # 是否在应用启动时自动执行聚类
```

### 工作流程
1. 应用完全启动后触发聚类任务（异步执行，不阻塞启动）
2. 查询所有产品
3. 逐个产品执行聚类分析
4. 结果存储到数据库
5. 查看日志了解执行情况

### 查看日志
```
应用已就绪，准备执行自动聚类任务...
找到 3 个产品，开始批量聚类
正在为产品 1 执行聚类...
产品 1 聚类完成
...
自动聚类任务完成！成功: 3, 失败: 0, 总计: 3
```

### 关闭自动聚类
如不需要自动聚类，可在配置文件中设置：
```yaml
clustering:
  auto-run-on-startup: false
```

---

## 🔧 配置说明

### 后端配置文件
位置：`backend/src/main/resources/application.yml`

主要配置项：
- **服务端口**: 默认8081
- **数据库连接**: MySQL配置
- **OpenAI API**: AI分析服务配置
- **NLP服务**: Python服务地址（默认 http://localhost:8000）
- **聚类配置**: 自动聚类开关

### 前端环境变量
位置：`frontend/.env`

```env
VITE_APP_TITLE=电商口碑分析系统
VITE_API_BASE_URL=/api
```

---

## 📝 测试账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| pm | 123456 | PM | 产品经理看板 |
| market | 123456 | MARKET | 市场分析看板 |
| ops | 123456 | OPS | 运营看板 |

---

## ❓ 常见问题

### Q: NLP服务启动失败？
**A:** 
1. 检查Python版本是否为3.8+
2. 确保安装了所有依赖：`pip install -r requirements.txt`
3. 检查8000端口是否被占用

### Q: 聚类功能不工作？
**A:** 
1. 确认NLP服务已启动（访问 http://localhost:8000/health）
2. 检查后端日志，查看是否有错误信息
3. 确认数据库中有评论数据

### Q: 登录显示400错误？
**A:** 确保后端已启动，且前端静态文件已正确部署到`backend/src/main/resources/static/`目录。

### Q: 如何验证前端是否正确部署？
**A:** 
1. 检查`backend/src/main/resources/static/index.html`是否存在
2. 访问`http://localhost:8081`应该显示登录页面
3. 查看浏览器Network标签，确认静态资源从8081端口加载

### Q: 数据库连接失败？
**A:** 检查MySQL服务是否运行，数据库名、用户名、密码是否正确。

---

## 🏗️ 技术栈

### 后端
- Spring Boot 3.2.5
- Spring Data JPA
- MySQL
- JWT身份验证
- OpenAI API (LLM分析)

### 前端
- Vue 3
- Vite
- Element Plus
- ECharts
- PWA支持

### NLP服务
- FastAPI
- scikit-learn
- jieba分词

---

## 📋 更新日志

### 2026-01-15 v0.0.2
- ✅ 新增自动聚类功能（应用启动时自动执行）
- ✅ 集成NLP服务到打包流程
- ✅ 创建一键打包部署脚本
- ✅ 优化NLP服务日志和健康检查

### 2026-01-15 v0.0.1
- ✅ 修复登录400错误
- ✅ 验证前后端打包部署流程
- ✅ 更新部署文档

