# AI 个性化学习资源分享平台 - 开发任务拆解

## 任务总览

本文档将项目拆解为细粒度的开发任务，按模块和优先级组织。每个任务标注预估工时和依赖关系。

---

## Phase 1: 项目脚手架与基础设施 (P0)

### 1.1 后端项目初始化
- [x] 1.1.1 使用 Spring Initializr 创建 Spring Boot 3 项目（Java 17, Maven）
- [x] 1.1.2 配置 pom.xml 依赖（Spring Web, Spring Security, MyBatis-Plus, Redis, JWT, Lombok, Validation）
- [x] 1.1.3 创建标准分层目录结构（controller/service/mapper/entity/dto/vo/config/common/util）
- [x] 1.1.4 配置 application.yml（数据库连接、Redis、文件上传、JWT 密钥等）
- [x] 1.1.5 配置 MyBatis-Plus 分页插件和自动填充处理器
- [x] 1.1.6 配置全局异常处理器 (@RestControllerAdvice)
- [x] 1.1.7 配置统一响应封装 (Result<T>)
- [x] 1.1.8 配置 CORS 跨域
- [x] 1.1.9 配置 SpringDoc OpenAPI (Swagger)

### 1.2 前端项目初始化
- [x] 1.2.1 使用 Vite 创建 Vue 3 + TypeScript 项目
- [x] 1.2.2 安装并配置 Tailwind CSS
- [x] 1.2.3 安装核心依赖（vue-router, pinia, axios, @vueuse/core）
- [x] 1.2.4 创建目录结构（views/components/router/store/api/utils/assets/styles）
- [x] 1.2.5 配置路径别名 (@/)
- [x] 1.2.6 配置 Axios 实例（baseURL, 拦截器）
- [x] 1.2.7 配置 Vue Router（路由表、路由守卫）
- [x] 1.2.8 配置 Pinia Store（用户状态）
- [x] 1.2.9 创建基础布局组件（Header, Footer, Sidebar）
- [x] 1.2.10 创建全局样式和 Tailwind 自定义配置

### 1.3 数据库初始化
- [x] 1.3.1 创建数据库和用户
- [x] 1.3.2 执行建表 SQL（user, category, tag, resource, resource_tag, resource_file, comment, like_record, favorite, rating, search_history, user_interest, recommendation_log）
- [x] 1.3.3 创建索引
- [x] 1.3.4 插入初始数据（默认分类、管理员账号、常用标签）

---

## Phase 2: 用户认证与权限模块 (P0)

### 2.1 后端 - 用户认证
- [x] 2.1.1 创建 User 实体类和 UserMapper
- [x] 2.1.2 实现 UserService（注册、登录、获取用户信息）
- [x] 2.1.3 实现密码加密（BCryptPasswordEncoder）
- [x] 2.1.4 实现 JWT 工具类（生成Token、解析Token、刷新Token）
- [x] 2.1.5 实现 JwtAuthenticationFilter
- [x] 2.1.6 配置 Spring Security（SecurityFilterChain, 请求授权规则）
- [x] 2.1.7 实现 AuthController（register, login, refresh, logout, me）
- [x] 2.1.8 实现参数校验（@Valid, 自定义校验注解）

### 2.2 后端 - RBAC 权限控制
- [x] 2.2.1 实现角色注解 @PreAuthorize（类级+方法级）
- [x] 2.2.2 实现权限拦截器（基于 Spring Security @EnableMethodSecurity）
- [x] 2.2.3 配置不同角色的 API 访问权限（ADMIN/PUBLISHER/USER）

### 2.3 前端 - 认证模块
- [x] 2.3.1 创建 AuthStore（登录状态、Token 管理）
- [x] 2.3.2 实现 API 调用层（authApi: register, login, refresh, logout）
- [x] 2.3.3 实现登录页面（表单验证、错误处理）
- [x] 2.3.4 实现注册页面
- [x] 2.3.5 实现路由守卫（未登录跳转登录页）
- [x] 2.3.6 实现 Token 自动刷新机制（Axios 响应拦截器）
- [x] 2.3.7 实现导航栏用户菜单（登录状态切换）

---

## Phase 3: 资源管理模块 (P0)

### 3.1 后端 - 分类与标签
- [x] 3.1.1 创建 Category 实体、Mapper、Service、Controller
- [x] 3.1.2 实现分类树查询（递归构建）
- [x] 3.1.3 创建 Tag 实体、Mapper、Service、Controller
- [x] 3.1.4 实现标签搜索（自动补全）
- [x] 3.1.5 实现热门标签查询

### 3.2 后端 - 资源 CRUD
- [x] 3.2.1 创建 Resource 实体、ResourceTag 实体、ResourceFile 实体
- [x] 3.2.2 创建 ResourceMapper（含关联查询）
- [x] 3.2.3 实现 ResourceService（发布、更新、删除、查询详情、列表分页）
- [x] 3.2.4 实现文件上传服务（FileService: 本地存储 / MinIO）
- [x] 3.2.5 实现 ResourceController（完整 CRUD 接口）
- [x] 3.2.6 实现资源筛选（分类、标签、评分、时间、排序）
- [x] 3.2.7 实现全文搜索（MySQL FULLTEXT）
- [x] 3.2.8 实现资源热度计算（HotScore 定时任务）

### 3.3 前端 - 资源模块
- [x] 3.3.1 实现 resourceApi（CRUD + 搜索 + 筛选）
- [x] 3.3.2 实现首页布局（分类导航 + 推荐区域 + 资源列表）
- [x] 3.3.3 实现资源卡片组件（封面、标题、摘要、标签、评分、热度）
- [x] 3.3.4 实现资源列表页（分页、排序切换）
- [x] 3.3.5 实现资源详情页（Markdown 渲染、文件下载）
- [x] 3.3.6 实现发布资源页（表单、文件上传、标签选择）
- [x] 3.3.7 实现搜索结果页（搜索框 + 筛选面板 + 结果列表）

---

## Phase 4: 社区互动模块 (P1)

### 4.1 后端 - 互动功能
- [x] 4.1.1 创建 LikeRecord 实体、Mapper、Service
- [x] 4.1.2 实现点赞/取消点赞（资源 + 评论，幂等操作）
- [x] 4.1.3 创建 Favorite 实体、Mapper、Service
- [x] 4.1.4 实现收藏/取消收藏
- [x] 4.1.5 创建 Rating 实体、Mapper、Service
- [x] 4.1.6 实现评分功能（更新平均分和评分人数）
- [x] 4.1.7 创建 Comment 实体、Mapper、Service
- [x] 4.1.8 实现评论 CRUD（支持多级回复）
- [x] 4.1.9 实现评论列表查询（一级评论 + 回复分页）
- [x] 4.1.10 实现 InteractionController（统一互动接口）

### 4.2 前端 - 互动功能
- [x] 4.2.1 实现点赞按钮组件（动画反馈、计数显示）
- [x] 4.2.2 实现收藏按钮组件
- [x] 4.2.3 实现星级评分组件
- [x] 4.2.4 实现评论区组件（评论列表、回复、发表）
- [x] 4.2.5 集成到资源详情页

---

## Phase 5: AI 增强功能模块 (P1)

### 5.1 后端 - AI 基础设施
- [x] 5.1.1 创建 AI 配置类（API Key、模型名称、超时配置）
- [x] 5.1.2 实现 AiService 接口和 Gemini 实现类
- [x] 5.1.3 实现 AI 调用层（Spring AI ChatClient + Google GenAI Starter）
- [x] 5.1.4 实现 AI 调用异常处理和降级策略
- [x] 5.1.5 实现 AI 调用日志记录

### 5.2 后端 - AI 智能摘要
- [x] 5.2.1 实现摘要生成 Prompt 模板
- [x] 5.2.2 实现 ResourceAiService.generateSummary()
- [x] 5.2.3 集成到资源发布流程（异步生成）
- [x] 5.2.4 实现摘要缓存（Redis，24h TTL）

### 5.3 后端 - 自然语言搜索 (NL2API)
- [x] 5.3.1 实现 NL2API Prompt 模板（解析自然语言为结构化查询）
- [x] 5.3.2 实现 QueryParser 解析 LLM 返回的 JSON
- [x] 5.3.3 实现 SearchService.naturalLanguageSearch()
- [x] 5.3.4 实现 SearchController.nlSearch()
- [x] 5.3.5 实现搜索历史记录

### 5.4 后端 - 个性化推荐
- [x] 5.4.1 实现用户兴趣标签提取（基于浏览/点赞/收藏历史）
- [x] 5.4.2 实现基于标签的内容推荐算法
- [x] 5.4.3 实现基于用户行为的协同过滤算法（item-based CF + 混合推荐）
- [x] 5.4.4 实现热度推荐算法
- [x] 5.4.5 实现推荐结果合并与排序
- [x] 5.4.6 实现推荐理由 Prompt 模板
- [x] 5.4.7 实现 RecommendationService.getRecommendations()
- [x] 5.4.8 实现 RecommendationController
- [x] 5.4.9 实现推荐结果缓存（Redis，30min TTL）

### 5.5 前端 - AI 功能
- [x] 5.5.1 实现 AI 摘要展示组件
- [x] 5.5.2 实现自然语言搜索输入框（带 AI 标识）
- [x] 5.5.3 实现 NL 搜索结果展示（解析意图 + 结果列表）
- [x] 5.5.4 实现推荐区域组件（推荐卡片 + 推荐理由）
- [x] 5.5.5 集成到首页和详情页

---

## Phase 6: 用户中心与管理后台 (P2)

### 6.1 后端 - 用户中心
- [x] 6.1.1 实现用户信息更新接口
- [x] 6.1.2 实现头像上传（POST /users/avatar，支持 JPG/PNG/GIF/WEBP）
- [x] 6.1.3 实现我的收藏列表
- [x] 6.1.4 实现我的发布列表
- [x] 6.1.5 实现用户统计数据接口

### 6.2 后端 - 管理后台
- [x] 6.2.1 实现用户管理接口（列表、角色修改、启用/禁用）
- [x] 6.2.2 实现资源审核接口（待审核列表、通过/拒绝）
- [x] 6.2.3 实现平台统计数据接口
- [x] 6.2.4 实现分类管理 CRUD
- [x] 6.2.5 实现标签管理 CRUD

### 6.3 前端 - 用户中心
- [x] 6.3.1 实现个人中心页面布局
- [x] 6.3.2 实现个人信息编辑
- [x] 6.3.3 实现我的收藏列表
- [x] 6.3.4 实现我的发布列表
- [x] 6.3.5 实现学习统计展示

### 6.4 前端 - 管理后台
- [x] 6.4.1 实现管理后台布局（侧边栏导航）
- [x] 6.4.2 实现仪表盘页面（统计图表）
- [x] 6.4.3 实现用户管理页面（表格 + 操作）
- [x] 6.4.4 实现资源审核页面
- [x] 6.4.5 实现分类管理页面（树形结构）
- [x] 6.4.6 实现标签管理页面

### 6.5 体验优化与修复
- [x] 6.5.1 迁移 AI 服务至 Spring AI 框架（OkHttp → spring-ai-starter-model-google-genai 1.1.6）
- [x] 6.5.2 修复 seed.sql 字符编码问题（添加 SET NAMES utf8mb4）和重复执行问题（INSERT IGNORE）
- [x] 6.5.3 搜索无结果时显示当前筛选条件（关键词 + 分类名）和"清除所有筛选"按钮
- [x] 6.5.4 NL 搜索未登录时前端 Toast 提示并跳转登录页（替代 alert）
- [x] 6.5.5 Toast 通知组件（页面内嵌、动画、自动消失、颜色区分）

---

## Phase 7: 测试与优化 (P2)

### 7.1 后端测试
- [ ] 7.1.1 编写单元测试（Service 层）
- [ ] 7.1.2 编写集成测试（Controller 层，MockMvc）
- [ ] 7.1.3 编写 API 接口测试（Python + Pytest / Postman Collection）
- [ ] 7.1.4 编写 AI 模块测试（Mock LLM 响应）

### 7.2 前端测试
- [ ] 7.2.1 编写组件单元测试（Vitest）
- [ ] 7.2.2 编写 E2E 测试（Playwright 核心流程）

### 7.3 性能优化
- [ ] 7.3.1 后端接口性能优化（N+1 查询、缓存策略）
- [ ] 7.3.2 前端性能优化（懒加载、图片优化、虚拟滚动）
- [ ] 7.3.3 Redis 缓存策略优化

### 7.4 安全加固
- [ ] 7.4.1 SQL 注入防护验证
- [ ] 7.4.2 XSS 防护验证
- [ ] 7.4.3 文件上传安全验证
- [ ] 7.4.4 敏感词过滤实现

---

## 任务依赖关系

```
Phase 1 (脚手架) ──→ Phase 2 (认证) ──→ Phase 3 (资源CRUD) ──→ Phase 4 (互动)
                                                        │
                                                        └──→ Phase 5 (AI) ──→ Phase 6 (管理)
                                                                                 │
                                                                                 └──→ Phase 7 (测试)
```

## 里程碑

| 里程碑 | 包含阶段 | 预估工时 | 交付物 |
|--------|---------|---------|--------|
| M1: 基础框架 | Phase 1 | 2天 | 可运行的前后端脚手架 |
| M2: 核心功能 | Phase 2 + 3 | 5天 | 用户认证 + 资源 CRUD |
| M3: 社区互动 | Phase 4 | 2天 | 点赞/收藏/评分/评论 |
| M4: AI 增强 | Phase 5 | 3天 | 摘要/NL搜索/推荐 |
| M5: 管理功能 | Phase 6 | 2天 | 用户中心 + 管理后台 |
| M6: 测试交付 | Phase 7 | 2天 | 测试用例 + 自动化脚本 |
