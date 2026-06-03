# 代码结构优化建议与改进计划

本文档记录了项目中的代码结构分析、废弃代码识别以及未来的优化建议，旨在保持代码库的整洁和可维护性。

## 1. 废弃与冗余代码清理 (Cleanup)

以下文件或目录被识别为不再使用或属于临时文件，建议清理：

### 1.1 `nlp_service/` (AI 分析服务)
- **状态**：**活跃 (Active)**
- **描述**：Python NLP 分析服务（FastAPI + Scikit-learn）。
- **原因**：后端 `NlpClient` 依赖此服务进行聚类和主题分析。
- **注意**：此前误判为废弃并删除，现已完全重构恢复。

### 1.2 `zh-CN.diff`
- **状态**：~~临时文件~~ **已删除**
- **描述**：根目录下的差异补丁文件。
- **原因**：通常是 git 操作或代码迁移产生的临时文件，不应包含在版本库中。
- **清理结果**：已于 2026-01-11 删除。

### 1.3 工具生成遗留物
- **`.VSCodeCounter/`**：~~代码统计工具生成的报告目录，建议加入 `.gitignore` 或删除。~~ **已删除**

## 2. 代码结构优化 (Refactoring)

### 2.1 前端结构 (Frontend)

#### **Utils 目录整合**
- 当前 `frontend/src/utils/` 包含了一些配置性质的文件 (`echarts.js`, `nprogress.js`, `chartMobileConfig.js`)。
- **建议**：
    - 将配置类文件移动到 `frontend/src/config/` (新建) 目录，使 `utils/` 专注于纯函数工具。
    - 保持 `highlight.js` 在 `utils/` 中。

#### **Styles 统一管理**
- **建议**：确保所有全局样式（如 `style.css`）和变量统一定义在 `frontend/src/assets/styles/` 下，并在入口文件中引入，保持根目录整洁。

### 2.2 后端结构 (Backend)

#### **Common 模块细分**
- `com.wh.reputation.common` 目前承载了较多职责（异常、API响应、Web配置、工具类）。
- **建议**：
    - 将异常相关类 (`*Exception.java`, `GlobalExceptionHandler`) 移动到 `common.exception`。
    - 将工具类 (`DateRangeParser`) 移动到 `common.util`。

## 3. 部署与运维 (Deployment)

### 3.1 前后端打包部署流程

项目采用**前后端打包运行**模式：

#### **开发模式**
- 前端：`npm run dev` → localhost:5173
- 后端：`./mvnw.cmd spring-boot:run` → localhost:8081
- CORS已配置，支持跨域开发

#### **生产模式**
1. 前端打包：`npm run build` → 生成`frontend/dist`
2. 部署静态文件：复制到`backend/src/main/resources/static/`
3. 启动后端：只需运行Spring Boot应用
4. 访问：`http://localhost:8081`（前后端统一端口）

详细部署指南请参考：[DEPLOYMENT.md](../DEPLOYMENT.md)

### 3.2 登录功能修复记录 (2026-01-15)

**问题**：用户报告登录显示400错误

**诊断**：
- 后端服务正常运行（8081端口）
- 开发模式（5173）登录功能正常
- 生产模式（8081）显示旧版角色选择界面 ❌
- **根因**：`backend/src/main/resources/static/` 中的前端打包文件未更新

**解决方案**：
```powershell
# 1. 清空旧版静态文件
Remove-Item -Path "backend\src\main\resources\static\*" -Recurse -Force

# 2. 重新打包前端
cd frontend
npm run build

# 3. 部署新版
Copy-Item -Path "dist\*" -Destination "..\backend\src\main\resources\static\" -Recurse -Force
```

**验证结果**：
- ✅ 8081端口显示新的账号密码登录表单
- ✅ JWT认证流程正常工作
- ✅ 登录成功后正确跳转到看板
- ✅ 前后端打包运行模式验证通过

## 4. 下一步行动计划

1. [x] **执行清理**：删除 `zh-CN.diff` 和 `.VSCodeCounter`。
2. [x] **恢复服务**：`nlp_service` 已重建并恢复。
3. [x] **修复登录**：解决400错误，验证前后端打包部署流程。
4. [x] **完善文档**：创建`DEPLOYMENT.md`部署指南。
5. [ ] **重构前端 Utils**：按建议分类代码。

---
**最后更新时间**：2026-01-15

### 3.3 NLP 服务启动修复 (2026-01-19)

**问题**：用户报告服务无法启动。

**诊断**：
- Python 环境版本为 3.14，但 `requirements.txt` 锁定了旧版 `numpy` (1.26.3)，导致编译失败 ❌
- 旧的后端进程（PID 2176）占用了 8081 端口，导致新服务无法启动 ❌
- `start.ps1` 启动 NLP 服务时依赖系统 PATH，未强制使用虚拟环境 Python。

**解决方案**：
1. **解除依赖锁定**：修改 `nlp_service/requirements.txt`，移除版本号以支持 Python 3.14+。
2. **清理僵尸进程**：终止占用端口的 Java 进程。
3. **优化启动脚本**：更新 `start.ps1`，显式指定 `.venv` 路径下的 Python 解释器。

**验证结果**：
- ✅ NLP 服务依赖安装成功。
- ✅ 8000 和 8081 端口正常监听。
