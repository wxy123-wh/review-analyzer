# 项目模块功能文档

## 项目概述
电商产品口碑监测与迭代优化辅助系统（蓝牙耳机） - 一个基于Spring Boot + Vue3的全栈应用，用于分析电商评论数据，提供维度化口碑指标、问题优先级分析和可视化仪表盘。

**技术栈：**
- 后端：Spring Boot 3.2.5 + MySQL 8.x + JPA + Elasticsearch (Vector Search)
- 前端：Vue 3.5.24 + Element Plus (Tech Theme) + ECharts + Vite + PWA
- AI能力：OpenAI API (Embedding + Chat Completion) - 语义搜索 + 智能分析
- NLP：jieba分词 + TF-IDF + KMeans + LDA主题建模
- 数据处理：Apache POI（Excel处理）

---

## 后端模块 (Backend Modules)

### 1. **认证模块 (auth)**
**包路径：** `com.wh.reputation.auth`

**功能：** 用户认证与权限控制

**核心组件：**
- `AuthController.java` - 认证控制器，处理登录请求
- `AuthService.java` - 认证服务，验证用户名密码，生成token
- `AuthInterceptor.java` - 认证拦截器，验证token并将用户信息注入请求
- `AuthWebConfig.java` - Web配置，注册拦截器
- `LoginRequest.java` - 登录请求DTO
- `LoginResponseDto.java` - 登录响应DTO（包含token和role）

**主要功能：**
- 用户登录（支持PM/MARKET/OPS三种角色）
- Token生成与验证（基于JWT的简化实现）
- 请求拦截与权限校验

**API端点：**
- `POST /api/auth/login` - 用户登录

---

### 2. **评论管理模块 (review)**
**包路径：** `com.wh.reputation.review`

**功能：** 评论数据导入、查询与管理

**核心组件：**
- `ReviewImportController.java` - 评论导入控制器
- `ReviewImportService.java` - 评论导入服务（支持CSV/XLSX/JSON格式）
- `ReviewQueryController.java` - 评论查询控制器
- `ReviewQueryService.java` - 评论查询服务
- `ReviewImportItem.java` - 导入数据项
- `ReviewImportResult.java` - 导入结果DTO
- `ReviewDetailDto.java` - 评论详情DTO
- `ReviewListItemDto.java` - 评论列表项DTO
- `ReviewsPageDto.java` - 分页结果DTO
- `CsvUtils.java` - CSV文件解析工具
- `HashUtils.java` - 评论去重哈希工具
- `TextCleaner.java` - 文本清洗工具

**主要功能：**
- 批量导入评论（CSV/XLSX/JSON文件上传）
- 评论数据清洗（去HTML、规范化空白）
- 评论去重（基于内容哈希）
- 评论分页查询（支持多维度筛选）
- 评论详情查看

**API端点：**
- `POST /api/reviews/import` - 导入评论
- `GET /api/reviews` - 查询评论列表（支持分页和筛选）
- `GET /api/reviews/{id}` - 获取评论详情

---

### 3. **搜索模块 (search) [NEW]**
**包路径：** `com.wh.reputation.search`

**功能：** 基于向量的语义搜索与索引管理

**核心组件：**
- `ReviewSearchController.java` - 搜索控制器（索引触发）
- `ReviewSearchRepository.java` - Elasticsearch 存储库（向量搜索）
- `ReviewIndexingService.java` - 索引服务
- `EmbeddingService.java` - 向量生成服务 (调用 OpenAI)
- `ReviewDocument.java` - Elasticsearch 文档实体
- `SearchConfig.java` - 搜索配置

**主要功能：**
- **向量搜索**：基于 OpenAI Embedding 的语义搜索（Infrastructure ready）
- **索引管理**：全量/增量构建 Elasticsearch 索引
- **混合检索**：支持向量 + 关键词混合检索（底层能力）

**API端点：**
- `POST /api/search/index/all` - 触发全量索引构建

---

### 4. **爬虫模拟模块 (crawl)**
**包路径：** `com.wh.reputation.crawl`

**功能：** 模拟爬取电商平台评论数据

**核心组件：**
- `CrawlController.java` - 爬虫控制器
- `CrawlService.java` - 爬虫服务（读取本地样例文件模拟爬取）
- `CrawlRunRequest.java` - 爬取请求DTO
- `CrawlRunResult.java` - 爬取结果DTO

**主要功能：**
- 模拟从电商平台爬取评论（从`data/crawl_samples/`读取样例）
- 支持指定平台、产品、页数
- 解析评论并写入数据库
- 自动触发后续分析流水线

**API端点：**
- `POST /api/crawl/run` - 执行爬取任务

---

### 4. **分析引擎模块 (analysis)**
**包路径：** `com.wh.reputation.analysis`

**功能：** 核心NLP分析引擎（维度归因、情感分析、分词、聚类、主题建模）

**核心组件：**

**控制器与服务：**
- `AnalysisController.java` - 分析控制器，提供分析结果查询API
- `AnalysisQueryService.java` - 分析查询服务
- `ReviewAnalysisService.java` - 评论分析管道（清洗→归因→情感→分词）
- `ClusterAnalysisService.java` - 聚类分析服务（TF-IDF + KMeans）
- `TopicAnalysisService.java` - 主题分析服务（LDA主题建模）
- `KeywordAnalysisService.java` - 关键词统计服务
- `StartupAnalysisRunner.java` - 启动时自动分析运行器

**分析组件：**
- `SentimentAnalyzer.java` - 情感分析器（基于词典+否定规则）
- `TokenizationService.java` - 分词服务（jieba分词）
- `KeywordExtractor.java` - 关键词提取器

**配置与数据加载：**
- `SentimentLexiconConfig.java` - 情感词典配置加载器
- `SentimentLexicon.java` - 情感词典实体
- `StopwordsConfig.java` - 停用词配置加载器
- `Stopwords.java` - 停用词实体
- `AspectSeeder.java` - 维度词典种子数据加载器
- `SampleReviewsSeeder.java` - 样例评论种子数据加载器
- `DataFileLocator.java` - 数据文件定位工具

**DTO：**
- `AnalysisRunRequest.java` / `AnalysisRunResponseDto.java` - 分析执行请求/响应
- `AspectAnalysisItemDto.java` / `AspectAnalysisResponseDto.java` - 维度分析结果
- `KeywordStatDto.java` / `KeywordsResponseDto.java` - 关键词统计结果
- `TopicItemDto.java` / `TopicsResponseDto.java` - 主题分析结果
- `ClusterListItemDto.java` / `ClustersResponseDto.java` - 聚类列表结果
- `ClusterDetailResponseDto.java` - 聚类详情
- `ClusterRepresentativeReviewDto.java` - 聚类代表评论
- `TrendPointDto.java` / `TrendResponseDto.java` - 趋势分析结果
- `SentimentResult.java` - 情感分析结果

**主要功能：**
- **维度归因**：基于词典匹配，识别评论所属维度（音质/续航/降噪等）
- **情感分析**：基于情感词典+否定词规则，计算情感标签（POS/NEU/NEG）和得分
- **分词处理**：使用jieba进行中文分词，过滤停用词
- **关键词统计**：统计高频关键词及其负向频次
- **主题建模**：LDA主题提取，发现评论中的潜在主题
- **问题聚类**：TF-IDF向量化 + KMeans聚类，发现问题簇
- **趋势分析**：按时间维度统计情感趋势

**API端点：**
- `POST /api/analysis/run` - 手动触发分析
- `GET /api/analysis/aspects` - 获取维度分析结果
- `GET /api/analysis/trend` - 获取趋势分析
- `GET /api/analysis/keywords` - 获取关键词统计
- `GET /api/analysis/topics` - 获取主题分析结果
- `GET /api/analysis/clusters` - 获取聚类列表
- `GET /api/analysis/clusters/{id}` - 获取聚类详情

---

### 5. **分析可视化模块 (analytics)**
**包路径：** `com.wh.reputation.analytics`

**功能：** 提供可视化分析数据（词云、情感趋势图）

**核心组件：**
- `AnalyticsController.java` - 分析可视化控制器
- `WordcloudService.java` - 词云服务
- `SentimentTrendService.java` - 情感趋势服务
- `WordcloudItemDto.java` / `WordcloudResponseDto.java` / `WordcloudMetaDto.java` - 词云数据DTO
- `SentimentTrendPointDto.java` / `SentimentTrendResponseDto.java` / `SentimentTrendMetaDto.java` - 趋势数据DTO

**主要功能：**
- 生成词云数据（支持按维度、情感筛选）
- 生成情感趋势图数据（按天/周聚合）
- 支持时间范围筛选

**API端点：**
- `GET /api/analytics/wordcloud` - 获取词云数据
- `GET /api/analytics/sentiment-trend` - 获取情感趋势数据

---

### 6. **仪表盘模块 (dashboard)**
**包路径：** `com.wh.reputation.dashboard`

**功能：** 提供总览仪表盘数据

**核心组件：**
- `DashboardController.java` - 仪表盘控制器
- `DashboardService.java` - 仪表盘服务
- `DashboardOverviewDto.java` - 总览数据DTO
- `DashboardTrendPointDto.java` - 趋势点DTO

**主要功能：**
- 汇总评论数量、情感分布
- 展示问题优先级Top列表
- 生成趋势概览数据

**API端点：**
- `GET /api/dashboard/overview` - 获取仪表盘总览数据

---

### 7. **决策支持模块 (decision)**
**包路径：** `com.wh.reputation.decision`

**功能：** 提供问题优先级排序和改进建议

**核心组件：**
- `DecisionController.java` - 决策控制器
- `AiAnalysisService.java` - AI 分析服务（LLM 集成）
- `DecisionPriorityService.java` - 优先级计算服务
- `SuggestionService.java` - 建议生成服务
- `PriorityItemDto.java` / `PriorityResponseDto.java` - 优先级结果DTO
- `SuggestionItemDto.java` / `SuggestionsResponseDto.java` - 建议结果DTO
- `AiSummaryRequestDto.java` / `AiSummaryResponseDto.java` - AI 摘要交互DTO
- `AiSummaryResponseDto.java` - AI 总结响应DTO
- `SuggestionEvidenceDto.java` - 建议证据DTO

**主要功能：**
- **优先级计算**：基于NegRate、Growth、Volume计算问题优先级
  - 公式：`Priority = NegRate × Growth × ln(1 + Volume)`
- **AI 智能分析** [NEW]：调用 OpenAI API 生成聚类问题的智能摘要与行动建议
  - 自动分析代表性评论
  - 生成中文质量问题摘要
  - 提供3-5条具体可执行的优化建议
  - 支持问答式JSON响应解析
- **改进建议生成**：基于模板匹配生成改进建议，附带证据评论
- 支持维度级和关键词级优先级

**API端点：**
- `GET /api/decision/priority` - 获取优先级列表
- `GET /api/decision/suggestions` - 获取改进建议
- `POST /api/decision/ai-summary` - 获取AI智能分析结果 [NEW]

---

### 8. **竞品对比模块 (compare)**
**包路径：** `com.wh.reputation.compare`

**功能：** 竞品对比分析

**核心组件：**
- `CompareController.java` - 对比控制器
- `CompareService.java` - 对比服务
- `CompareAspectItemDto.java` / `CompareAspectsResponseDto.java` - 对比结果DTO
- `CompareRateDto.java` - 情感率DTO
- `CompareNormalizedDto.java` - 归一化数据DTO

**主要功能：**
- 按维度对比自有产品与竞品的情感分布
- 计算差值（本品 - 竞品）
- 归一化处理便于可视化

**API端点：**
- `GET /api/compare/aspects` - 获取竞品对比数据

---

### 9. **趋势预警模块 (alert)**
**包路径：** `com.wh.reputation.alert`

**功能：** 监控负向率异常上涨，生成预警

**核心组件：**
- `AlertController.java` - 预警控制器
- `AlertService.java` - 预警服务
- `AlertItemDto.java` / `AlertsResponseDto.java` - 预警列表DTO
- `AlertAckResponseDto.java` - 预警确认响应DTO

**主要功能：**
- 检测negRate异常上涨（窗口环比超过阈值）
- 生成预警记录（status: new）
- 支持预警确认（ack）
- 去重机制（同product+metric+aspect+window不重复）

**API端点：**
- `GET /api/alerts` - 获取预警列表
- `POST /api/alerts/ack` - 确认预警

---

### 10. **活动/版本管理模块 (event)**
**包路径：** `com.wh.reputation.event`

**功能：** 管理活动/版本事件（用于前后对比）

**核心组件：**
- `EventController.java` - 事件控制器
- `EventService.java` - 事件服务
- `CreateEventRequest.java` - 创建事件请求DTO
- `CreateEventResponseDto.java` - 创建事件响应DTO
- `EventItemDto.java` - 事件列表项DTO

**主要功能：**
- 创建活动/版本事件（记录开始/结束日期）
- 查询事件列表
- 为闭环验证提供时间锚点

**API端点：**
- `POST /api/events` - 创建事件
- `GET /api/events` - 获取事件列表

---

### 11. **闭环评估模块 (evaluate)**
**包路径：** `com.wh.reputation.evaluate`

**功能：** 活动/版本前后对比评估

**核心组件：**
- `EvaluateController.java` - 评估控制器
- `EvaluateService.java` - 评估服务
- `BeforeAfterResponseDto.java` - 前后对比结果DTO
- `BeforeAfterEventDto.java` - 事件信息DTO
- `BeforeAfterWindowDto.java` - 时间窗口数据DTO
- `BeforeAfterAspectDto.java` - 维度对比DTO
- `KeywordChangeDto.java` - 关键词变化DTO

**主要功能：**
- 对比事件前后的评论量、负向率
- 对比各维度的情感变化
- 统计关键词频次变化
- 验证改进效果

**API端点：**
- `GET /api/evaluate/before-after` - 获取前后对比数据

---

### 12. **元数据模块 (meta)**
**包路径：** `com.wh.reputation.meta`

**功能：** 提供系统元数据（下拉框选项等）

**核心组件：**
- `MetaController.java` - 元数据控制器
- `AspectMetaDto.java` - 维度元数据DTO
- `PlatformMetaDto.java` - 平台元数据DTO
- `ProductMetaDto.java` - 产品元数据DTO

**主要功能：**
- 提供产品列表（包含竞品标识）
- 提供平台列表（京东、淘宝等）
- 提供维度列表（音质、续航、降噪等）

**API端点：**
- `GET /api/meta/products` - 获取产品列表
- `GET /api/meta/platforms` - 获取平台列表
- `GET /api/meta/aspects` - 获取维度列表

---

### 13. **持久化模块 (persistence)**
**包路径：** `com.wh.reputation.persistence`

**功能：** JPA实体与Repository定义

**核心组件：**

**实体类：**
- `ReviewEntity.java` - 评论实体（核心表）
- `AspectEntity.java` - 维度实体（音质/续航等）
- `ReviewAspectResultEntity.java` - 评论-维度归因结果实体
- `ProductEntity.java` - 产品实体
- `PlatformEntity.java` - 平台实体

**Repository接口：**
- `ReviewRepository.java` - 评论数据访问接口
- `AspectRepository.java` - 维度数据访问接口
- `ReviewAspectResultRepository.java` - 归因结果数据访问接口
- `ProductRepository.java` - 产品数据访问接口
- `PlatformRepository.java` - 平台数据访问接口

**主要功能：**
- 定义数据库表结构（实体类）
- 提供数据访问方法（Repository）
- 支持复杂查询（自定义Query）

---

### 14. **公共模块 (common)**
**包路径：** `com.wh.reputation.common`

**功能：** 全局配置、异常处理、工具类

**核心组件：**
- `GlobalExceptionHandler.java` - 全局异常处理器
- `ApiResponse.java` - 统一响应格式封装
- `CorsConfig.java` - 跨域配置
- `RequestIdFilter.java` - 请求ID过滤器（日志追踪）
- `DateRangeParser.java` - 日期范围解析工具
- `BadRequestException.java` - 400异常
- `NotFoundException.java` - 404异常
- `UnauthorizedException.java` - 401异常

**主要功能：**
- 统一异常处理
- 统一响应格式（code/msg/data）
- CORS配置
- 请求日志追踪
- 日期解析工具

---

### 15. **AI 基础设施模块 (ai)**
**包路径：** `com.wh.reputation.ai`

**功能：** 统一封装 OpenAI 接口调用，提供基础 AI 能力

**核心组件：**
- `OpenAiClient.java` - OpenAI API 客户端（支持 Chat/Embeddings）
- `OpenAiConfig.java` - AI 配置类（API Key, Base URL, Model）
- `OpenAiChatRequest.java` / `OpenAiChatResponse.java` - Chat 接口 DTO

**主要功能：**
- 统一管理 OpenAI API 调用
- 提供 Chat Completion 和 Embedding 生成的基础能力
- 支持模型配置（gpt-3.5/4, text-embedding-3）

---

### 16. **Web配置模块 (config)**
**包路径：** `com.wh.reputation.config`

**功能：** Web服务器配置与静态资源处理

**核心组件：**
- `SpaController.java` - 单页应用(SPA)控制器，用于处理前端路由转发
- `WebStaticConfig.java` - 静态资源配置，确保从classpath加载资源

**主要功能：**
- **SPA路由支持**：将`/login`, `/dashboard/*`等前端路由转发至`index.html`
- **静态资源映射**：配置`/**`映射到`classpath:/static/`

**API端点：**
- `GET /**` - 转发至前端入口

---

### 17. **AI 分析服务模块 (nlp_service)**
**包路径：** `nlp_service/` (Python Service)

**功能：** 核心 NLP 算法服务（Python）

**核心组件：**
- `main.py` - FastAPI 服务入口，提供 HTTP 接口
- `clustering.py` - 聚类算法实现 (TF-IDF + KMeans)
- `topic_modeling.py` - 主题建模实现 (sklearn LDA)

**主要功能：**
- **智能聚类**：`/cluster` 接口，接收评论数据，返回聚类结果
- **主题发现**：`/topics` 接口，分析评论潜在主题

**依赖关系：**
- 被 Java 后端 `NlpClient` 调用

---

### 18. **基础设施模块 (infra)**
**包路径：** `infra/`

**功能：** 系统基础设施

**核心组件：**
- `infra/docker-compose.yml` - MySQL 数据库的 Docker 编排配置

**主要功能：**
- **Docker环境**：快速启动开发数据库

---

## 前端模块 (Frontend Modules)

### 1. **路由模块 (router)**
**文件路径：** `frontend/src/router/index.js`

**功能：** Vue Router路由配置

**主要路由：**
- `/login` - 登录页
- `/overview` - 总览页
- `/reviews` - 评论检索页
- `/analysis` - 维度分析页
- `/topics` - 主题分析页
- `/clusters` - 聚类分析页
- `/clusters/:id` - 聚类详情页
- `/pm` - PM仪表盘
- `/market` - 市场仪表盘
- `/ops` - 运营仪表盘
- `/compare` - 竞品对比页
- `/alerts` - 预警列表页
- `/suggestions` - 改进建议页
- `/events` - 事件管理页
- `/before-after` - 前后对比页

---

### 2. **状态管理模块 (stores)**
**文件路径：** `frontend/src/stores/`

**核心组件：**
- `auth.js` - 认证状态管理（token、role、用户信息）
- `globalFilters.js` - 全局筛选器状态（产品、时间范围等）

**主要功能：**
- 管理登录状态
- 管理全局筛选条件
- 持久化到localStorage

---

### 3. **API客户端模块 (api)**
**文件路径：** `frontend/src/api/`

**核心组件：**
- `http.js` - Axios实例配置（拦截器、token注入）
- `auth.js` - 认证API
- `reviews.js` - 评论API
- `analysis.js` - 分析API
- `analytics.ts` - 分析可视化API（TypeScript）
- `dashboard.js` - 仪表盘API
- `decision.js` - 决策API
- `compare.js` - 对比API
- `alerts.js` - 预警API
- `events.js` - 事件API
- `evaluate.js` - 评估API
- `meta.js` - 元数据API

**主要功能：**
- 封装所有后端API调用
- 统一错误处理
- 自动注入token

---

### 4. **视图组件模块 (views)**
**文件路径：** `frontend/src/views/`

**核心组件：**

**基础页面：**
- `LoginView.vue` - 登录页（角色化卡片选择，直达 PM/MARKET/OPS 看板）
- `OverviewView.vue` - 总览页（评论量、情感分布、Top问题）
- `ReviewsView.vue` - 评论检索页（支持多维度筛选）
- `AnalysisView.vue` - 维度分析页（各维度情感分布）

**NLP分析页面：**
- `TopicsView.vue` - 主题分析页（LDA主题）
- `ClustersView.vue` - 聚类列表页
- `ClusterDetailView.vue` - 聚类详情页（代表评论）

**角色化仪表盘：**
- `DashboardPmView.vue` - PM仪表盘（聚类+优先级+趋势+建议）
- `DashboardMarketView.vue` - 市场仪表盘（正向关键词+主题+竞品对比）
- `DashboardOpsView.vue` - 运营仪表盘（趋势+预警+活动对比）

**决策与对比：**
- `CompareView.vue` - 竞品对比页（雷达图对比各维度）
- `SuggestionsView.vue` - 改进建议页（附证据评论）

**预警与闭环：**
- `AlertsView.vue` - 预警列表页（可确认）
- `EventsView.vue` - 事件管理页（创建活动/版本事件）
- `BeforeAfterView.vue` - 前后对比页（验证改进效果）

**主要功能：**
- 可视化展示分析结果（ECharts图表）
- 支持筛选和下钻
- 角色化展示（PM/MARKET/OPS不同视角）

---

### 5. **可复用组件模块 (components)**
**文件路径：** `frontend/src/components/`

**核心组件：**
- `EChart.vue` - ECharts图表封装组件
- `GlobalFiltersBar.vue` - 全局筛选器组件（产品、时间范围）
- `SentimentTag.vue` - 情感标签组件（POS/NEU/NEG颜色标识）
- `SentimentTrendCard.vue` - 情感趋势卡片组件（折线图）
- `WordCloudCard.vue` - 词云卡片组件（词云图）
- `MobileNav.vue` - 移动端底部导航组件 [NEW]
- `InstallPrompt.vue` - PWA安装提示组件 [NEW]

**主要功能：**
- 封装常用UI组件
- 统一样式和交互（Neumorphism Tech Theme）
- 提高代码复用性
- 移动端响应式适配

---

### 6. **组合式函数模块 (composables)**
**文件路径：** `frontend/src/composables/`

**核心组件：**
- `useAnalysisFilters.js` - 分析页筛选器逻辑复用
- `useInsightsFilters.js` - 洞察页筛选器逻辑复用
- `useMobileDetect.js` - 移动设备检测逻辑 [NEW]
- `useResponsive.js` - 响应式布局状态管理 [NEW]

**主要功能：**
- 封装可复用的响应式逻辑
- 减少代码重复
- 提供设备检测能力

---

### 7. **工具模块 (utils)**
**文件路径：** `frontend/src/utils/`

**核心组件：**
- `highlight.js` - 文本高亮工具（关键词高亮）
- `chartMobileConfig.js` - 移动端图表配置适配 [NEW]
- `echarts.js` - ECharts 按需引入配置 [NEW]
- `nprogress.js` - 页面加载进度条配置 [NEW]

**主要功能：**
- 提供通用工具函数
- 集中管理第三方库配置（ECharts, NProgress）
- 移动端图表适配

---

### 8. **入口与PWA模块 (entry)**
**文件路径：** `frontend/src/`

**核心组件：**
- `registerServiceWorker.js` - PWA Service Worker 注册脚本
- `main.js` - 应用入口，挂载插件
- `App.vue` - 根组件

**主要功能：**
- PWA资源缓存与离线支持
- 全局插件初始化

---

## 数据模块 (Data)

### 1. **词典与配置数据**
**文件路径：** `data/`

**核心文件：**
- `aspects.json` - 维度词典（音质、续航、降噪等维度的关键词）
- `sentiment_lexicon.json` - 情感词典（正向词、负向词、否定词）
- `stopwords.txt` - 停用词表
- `sample_reviews.csv` - 样例评论数据
- `crawl_samples/` - 爬虫样例数据目录

**主要功能：**
- 提供NLP分析所需的词典数据
- 提供初始化样例数据

---

## 脚本模块 (Scripts)

### 1. **数据种子脚本**
**文件路径：** `scripts/seed_event_before_after.ps1`

**功能：** PowerShell脚本，用于创建事件和触发前后对比分析

**主要功能：**
- 自动化创建测试事件
- 批量导入前后评论数据
- 验证前后对比功能

---

### 2. **部署脚本**
**文件路径：** `deploy_local.ps1`

**功能：** 本地一键构建与部署脚本

**主要功能：**
- 自动构建前端 (npm run build)
- 将前端构建产物复制到后端静态资源目录
- 构建后端 (mvn clean package)
- 启动后端服务

---

## 文档模块 (Docs)

### 1. **需求与规格文档**
**文件路径：** `docs/`

**核心文档：**
- `Project_Scope.md` - 项目范围与需求定义
- `Backend_API_Spec.md` - 后端API规格说明
- `Analysis_Algorithm_Spec.md` - 分析算法规格说明
- `Data_Model_Import.md` - 数据模型与导入规范
- `Frontend_UX_Dashboard_Spec.md` - 前端UX与仪表盘规格
- `Clustering_Sentiment_Implementation.md` - 聚类与情感实现文档
- `Extend_To_Abstract.md` - 摘要补齐扩展设计
- `Acceptance_Criteria_TestCases.md` - 验收标准与测试用例

**主要功能：**
- 定义项目需求与约束
- 规范API接口
- 说明算法实现细节
- 提供验收标准

---

## 模块依赖关系

```
┌─────────────────────────────────────────────────┐
│                   Frontend                       │
│  (Views → API Client → Components → Stores)     │
└───────────────────┬─────────────────────────────┘
                    │ HTTP Requests
                    ↓
┌─────────────────────────────────────────────────┐
│              Backend (Spring Boot)               │
├─────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │   Auth   │→│ Review   │→│ Analysis │       │
│  └──────────┘  └────┬─────┘  └────┬─────┘      │
│                     │              │            │
│  ┌──────────┐  ┌───↓────┐  ┌──────↓────┐      │
│  │   Crawl  │→│  Meta  │  │ Analytics │       │
│  └──────────┘  └────────┘  └───────────┘      │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │Dashboard │←│ Decision │  │ Compare  │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │  Alert   │  │  Event   │←│ Evaluate │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│                     ↑                           │
│              ┌──────┴───────┐                  │
│              │ Persistence  │                  │
│              └──────┬───────┘                  │
└─────────────────────┼─────────────────────────┘
                      ↓
              ┌───────────────┐
              │  MySQL 8.x    │
              └───────────────┘
```

---

## 核心业务流程

### 1. **评论导入与分析流程**
```
用户上传文件 (CSV/XLSX/JSON)
    ↓
ReviewImportService 解析与清洗
    ↓
写入 review 表（去重）
    ↓
自动触发 ReviewAnalysisService
    ↓
├─ 维度归因 (AspectSeeder 词典匹配)
├─ 情感分析 (SentimentAnalyzer 词典+否定规则)
├─ 分词处理 (TokenizationService jieba)
└─ 写入 review_aspect_result 表
    ↓
自动触发扩展分析
    ↓
├─ TopicAnalysisService (LDA主题建模)
├─ ClusterAnalysisService (TF-IDF + KMeans)
├─ AlertService (趋势预警检测)
└─ SuggestionService (生成改进建议)
```

### 2. **角色化仪表盘流程**
```
用户登录 (PM/MARKET/OPS)
    ↓
前端根据 role 显示对应菜单
    ↓
PM → DashboardPmView
  ├─ 聚类结果 (ClustersView)
  ├─ 优先级列表 (DecisionPriorityService)
  ├─ 改进前后趋势 (SentimentTrendService)
  └─ 改进建议 (SuggestionService)
    ↓
MARKET → DashboardMarketView
  ├─ 正向关键词 (WordcloudService, sentiment=POS)
  ├─ 主题分布 (TopicAnalysisService)
  └─ 竞品对比 (CompareService)
    ↓
OPS → DashboardOpsView
  ├─ 趋势图 (SentimentTrendService)
  ├─ 预警列表 (AlertService)
  └─ 活动前后对比 (EvaluateService)
```

### 3. **闭环验证流程**
```
创建事件 (POST /api/events)
  ↓
记录 event (start_date/end_date)
  ↓
运营导入活动后评论数据
  ↓
查看前后对比 (GET /api/evaluate/before-after)
  ↓
EvaluateService 计算:
├─ before窗口 (start_date前等长时间)
├─ after窗口 (start_date ~ end_date)
└─ 对比：reviewCount, negRate, aspects, keywords
  ↓
前端展示对比结果（BeforeAfterView）
```

---

## 技术特点

1. **全栈一体化**：Spring Boot后端 + Vue3前端，前后端分离架构
2. **AI智能增强**：
   - OpenAI集成：Chat Completion生成智能摘要和行动建议
   - 向量搜索基础设施：Elasticsearch + OpenAI Embedding（待启用）
   - 智能问题分析：自动提取核心质量问题和优化方向
3. **NLP能力**：集成jieba分词、LDA主题建模、TF-IDF+KMeans聚类
4. **可解释性**：所有分析结果可追溯到证据评论（reviewId + snippet）
5. **角色化展示**：PM/MARKET/OPS三角色差异化仪表盘 + 无刷新角色切换
6. **闭环验证**：支持活动/版本前后对比，验证改进效果
7. **实时预警**：自动检测负向率异常上涨
8. **竞品对比**：支持自有产品与竞品多维度对比
9. **可视化丰富**：ECharts图表（折线图、柱状图、雷达图、词云图等）
10. **现代UI/UX**：
    - Neumorphism Tech Theme（新拟态科技风格）
    - 响应式设计（桌面 + 平板 + 移动端完整适配）
    - PWA支持（可安装、离线缓存、Service Worker）
    - 移动端底部导航 + 触摸优化
11. **性能优化**：
    - 页面过渡动画
    - 懒加载和代码分割
    - 移动端卡片布局

---

## 扩展说明

本系统设计为可扩展架构，支持以下扩展方向：

1. **多产品类型**：当前聚焦蓝牙耳机，可扩展至其他3C产品（通过修改`aspects.json`）
2. **真实爬虫**：当前为模拟爬取，可替换为真实电商API或爬虫框架
3. **外部NLP服务**：可将NLP模块（LDA/KMeans）抽离为独立Python服务（FastAPI）
4. **更多角色**：可扩展更多角色（如研发、客服等）及其专属视图
5. **语义搜索启用**：当前向量搜索基础设施已就绪，可接入前端实现自然语言查询
6. **多模态分析**：可扩展图片评论、视频内容分析能力
7. **实时流式更新**：可接入WebSocket实现实时数据更新和通知

---

**文档版本：** v1.1  
**更新日期：** 2026-01-11  
**维护者：** 项目团队  

---

## 最近更新记录

### v1.1 (2026-01-11)
- ✨ 新增：AI智能分析服务（OpenAI集成）
- ✨ 新增：语义搜索基础设施（Elasticsearch向量搜索）
- 🎨 优化：前端Tech主题UI/UX（Neumorphism风格）
- 📱 新增：PWA支持（Service Worker + 安装提示）
- 📱 优化：移动端完整适配（底部导航 + 卡片布局）
- 🔄 优化：无刷新角色切换功能
- 🎯 优化：登录页改为角色卡片直选模式
- 🎯 优化：登录页改为角色卡片直选模式
- 📝 文档：新增 Config、Infra、Frontend Utils 等模块说明
### v1.0 (2026-01-09)
- 🎉 初始版本发布
- ✅ 完成核心NLP分析引擎
- ✅ 完成角色化仪表盘
- ✅ 完成闭环验证功能
