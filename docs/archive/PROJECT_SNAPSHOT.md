# PROJECT SNAPSHOT - AI 个性化学习资源分享平台

> 快照时间: 2026-05-18 | 分支: main | 最新提交: 29fd4ea

---

## 1. 项目概览

### 1.1 核心业务目标

构建面向大学生的学习资源共享社区，利用 AI 技术（阿里云百炼 Qwen + OpenAI 兼容 HTTP 接口）实现：

| 目标 | 说明 |
|------|------|
| **智能发现** | 基于标签 + 协同过滤的个性化推荐，AI 生成推荐理由文案 |
| **自然交互** | NL2API 自然语言搜索，AI 解析为结构化查询参数 |
| **高效预览** | 资源发布时自动生成 ~100 字 AI 摘要 |
| **社区共建** | 点赞、收藏、1-5 星评分、多级评论 |

### 1.2 当前开发阶段

```
Phase 1: 脚手架          [████████████████████] 100%  ✅
Phase 2: 认证权限         [████████████████████] 100%  ✅  (RBAC @PreAuthorize 已实现)
Phase 3: 资源管理         [████████████████████] 100%  ✅
Phase 4: 社区互动         [████████████████████] 100%  ✅
Phase 5: AI 增强          [████████████████████] 100%  ✅  (缓存/协同过滤已完成)
Phase 6: 用户中心/管理     [████████████████████] 100%  ✅  (头像上传已实现)
Phase 7: 测试与优化        [░░░░░░░░░░░░░░░░░░░░]   0%  (14 个待办任务)
```

**总体进度: ~95%** (Phase 1-6 全部完成，剩余测试与优化)

---

## 2. 目录结构与关键模块

```
AI个性化学习资源分享平台/
├── backend/                          # Spring Boot 3 后端 (Java 17 + Maven)
│   ├── pom.xml                       # 依赖管理 (Spring Boot 3.2.5, MyBatis-Plus 3.5.5, JJWT 0.12.5)
│   └── src/main/java/com/learning/platform/
│       ├── LearningPlatformApplication.java   # 启动类
│       ├── config/                   # 配置层
│       │   ├── SecurityConfig.java           # Spring Security + JWT 过滤器链
│       │   ├── CorsConfig.java               # 跨域配置
│       │   ├── MyBatisPlusConfig.java        # 分页插件 + 自动填充
│       │   ├── RedisConfig.java              # Redis 序列化配置
│       │   └── GlobalExceptionHandler.java   # 全局异常处理
│       ├── common/                   # 通用封装
│       │   ├── Result.java                   # 统一响应 {code, message, data}
│       │   ├── PageResult.java               # 分页响应
│       │   └── BusinessException.java        # 业务异常
│       ├── entity/                   # 实体类 (10 个)
│       │   ├── User, Resource, Category, Tag
│       │   ├── Comment, LikeRecord, Favorite, Rating
│       │   └── ResourceTag, ResourceFile
│       ├── mapper/                   # MyBatis-Plus Mapper 接口 (10 个)
│       ├── service/                  # 业务逻辑层 (11 个)
│       │   ├── AuthService.java              # 注册/登录/JWT 签发
│       │   ├── ResourceService.java          # 资源 CRUD + 筛选 + 全文搜索
│       │   ├── AiService.java                # DashScope OpenAI 兼容调用 (摘要/NL解析/推荐理由) + Redis 缓存
│       │   ├── ResourceSummaryAsyncService.java # 资源摘要异步刷新
│       │   ├── SearchService.java            # 关键词搜索 + NL2API 搜索 + Redis 热搜/历史
│       │   ├── RecommendationService.java    # 标签推荐 + 协同过滤 + 热度推荐 + Redis 缓存
│       │   ├── InteractionService.java       # 点赞/收藏/评分
│       │   ├── CommentService.java           # 多级评论 CRUD
│       │   ├── FileService.java              # 文件上传 + 头像上传
│       │   └── CategoryService, TagService
│       ├── controller/               # REST 控制器 (9 个)
│       │   ├── AuthController                # POST /auth/login, /register, /refresh, /logout, GET /auth/me
│       │   ├── ResourceController            # CRUD /resources + 筛选 (@PreAuthorize)
│       │   ├── SearchController              # GET /search + POST /search/nl
│       │   ├── AiController                  # AI 摘要/推荐/聊天流接口
│       │   ├── InteractionController         # 点赞/收藏/评分 (@PreAuthorize)
│       │   ├── UserController                # 个人信息 + 头像上传 (@PreAuthorize)
│       │   ├── AdminController               # 管理后台 (@PreAuthorize hasRole ADMIN)
│       │   └── CategoryController, TagController
│       ├── dto/                      # 请求 DTO (8 个)
│       ├── security/                 # JWT 认证过滤器
│       ├── task/                     # 定时任务 (热度分计算)
│       └── util/                     # JWT 工具类
│
├── frontend/                         # Vue 3 前端 (Vite + TypeScript + Tailwind CSS)
│   ├── package.json                  # 依赖: vue 3.4, vue-router 4.3, pinia 2.1, axios 1.6
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   └── src/
│       ├── main.ts                   # 入口
│       ├── App.vue                   # 根组件 (Header + RouterView + Footer)
│       ├── api/                      # API 调用层 (7 个模块)
│       │   ├── request.ts                    # Axios 实例 (baseURL: /api/v1, JWT 拦截器, 401 自动刷新)
│       │   ├── auth.ts                       # 认证 API
│       │   ├── resource.ts                   # 资源 CRUD + 点赞/收藏/评分
│       │   ├── search.ts                     # 搜索 + NL 搜索
│       │   ├── ai.ts                         # AI 摘要/推荐
│       │   ├── comment.ts                    # 评论 API
│       │   ├── category.ts                   # 分类 + 标签 API (含管理 CRUD)
│       │   └── user.ts                       # 用户信息 API (含头像上传)
│       ├── views/                    # 页面组件 (13 个)
│       │   ├── Home.vue                      # 首页 (分类导航 + AI推荐 + 热门/最新)
│       │   ├── Search.vue                    # 搜索页 (关键词/NL切换 + 侧边筛选 + AI意图 + 空结果提示 + Toast通知)
│       │   ├── ResourceDetail.vue            # 资源详情 (面包屑 + 侧边栏 + 评论)
│       │   ├── Publish.vue                   # 发布页 (Markdown编辑器 + 文件/封面上传 + AI摘要)
│       │   ├── Profile.vue                   # 个人中心 (侧边栏 + 4 Tab + 头像上传 + 编辑/删除)
│       │   ├── Login.vue / Register.vue      # 登录/注册
│       │   ├── Admin.vue                     # 管理后台布局 (深色侧边栏 slate-900)
│       │   └── admin/                        # 管理子页面
│       │       ├── Dashboard.vue             # 仪表盘 (4统计卡片 + 待审资源 + 最近用户 + 分类标签)
│       │       ├── Users.vue                 # 用户管理
│       │       ├── Resources.vue             # 资源审核 (卡片式布局)
│       │       ├── Categories.vue            # 分类管理 (树形 CRUD)
│       │       └── Tags.vue                  # 标签管理 (标签云 CRUD)
│       ├── components/               # 公共组件
│       │   ├── AppHeader.vue                 # 顶部导航 (Logo + 搜索 + 用户菜单)
│       │   ├── AppFooter.vue                 # 底部
│       │   ├── AiChatWidget.vue              # 全局 AI 悬浮聊天窗 (可拖动 + 流式输出)
│       │   ├── AppToast.vue                  # 全局 Toast 通知 (4种类型, 自动消失)
│       │   └── AppModal.vue                  # 通用弹窗 (Teleport, 缩放动画, 插槽)
│       ├── router/index.ts           # 路由 (含路由守卫: requiresAuth/requiresAdmin/requiresPublisher)
│       ├── store/user.ts             # Pinia 用户状态 (token 持久化 + 角色计算属性)
│       └── types/index.ts            # TypeScript 类型定义
│
├── db/                               # 数据库脚本
│   ├── init.sql                      # 建库建表脚本
│   └── seed.sql                      # 统一测试数据脚本 (默认账号/分类标签/资源/互动/封面/文件记录)
│
├── docs/                             # 最终交付文档
│   ├── PRD.md
│   ├── 设计文档.md
│   └── 交付清单.md
│
├── prototypes/                       # HTML 页面原型 (UI 设计参考)
│   ├── index.html / search.html / detail.html
│   ├── publish.html / profile.html / admin.html
│   └── README.md
│
├── tests/                            # 测试脚本
│   ├── test_cases.md                 # 测试用例表
│   ├── test_report.md                # 测试报告
│   ├── test_api.py                   # API 接口测试 (Python + Pytest)
│   └── test_ui.py                    # UI 自动化测试 (Playwright)
│
├── openspec/                         # 项目文档 (OpenSpec 规范)
│   ├── proposal.md                   # PRD 产品需求文档
│   ├── design.md                     # 系统设计文档 (架构图 + ER 图 + API 设计)
│   ├── tasks.md                      # 开发任务拆解 (7 Phase, ~80 个子任务)
│   └── changes/                      # 变更记录
│
├── .claude/                          # Claude Code 配置 (OpenSpec Skills)
├── .gemini/                          # Gemini CLI 配置
├── .trae/                            # Trae 配置
├── README.md                         # 项目说明 + 启动指南
├── docs/archive/                     # 过程文档归档
└── AI_Dev_Log.md                     # AI 辅助开发日志
```

---

## 3. 已实现功能清单

### 3.1 用户与认证

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| 用户注册 (用户名+密码) | ✅ | `AuthController.java` → `AuthService.java` |
| 用户登录 (JWT 签发) | ✅ | 同上, `JwtUtil.java` |
| Token 刷新 (7天有效期) | ✅ | `POST /auth/refresh` |
| 密码 BCrypt 加密 | ✅ | `SecurityConfig.passwordEncoder()` |
| 路由守卫 (角色检查) | ✅ | `router/index.ts` beforeEach |
| 401 自动刷新 Token | ✅ | `api/request.ts` 响应拦截器 |
| RBAC 方法级权限控制 | ✅ | `@PreAuthorize` on Admin/Resource/User/InteractionController |

### 3.2 资源管理

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| 资源发布 (标题/分类/标签/描述/文件/链接) | ✅ | `Publish.vue` → `ResourceController` |
| Markdown 编辑器 (工具栏+预览) | ✅ | `Publish.vue` insertMarkdown() |
| 文件上传 (PDF/DOCX/PPT/MP4/ZIP, 500MB) | ✅ | `FileService.java` |
| 封面图片上传 | ✅ | `Publish.vue` coverFile + coverPreview |
| 资源列表 (分页/排序) | ✅ | `Home.vue`, `ResourceController` |
| 资源详情 (面包屑+侧边栏+相关推荐+热门标签) | ✅ | `ResourceDetail.vue` |
| 分类树查询 | ✅ | `CategoryService.java` |
| 标签搜索+热门标签 | ✅ | `TagService.java` |
| 全文搜索 (MySQL FULLTEXT) | ✅ | `ResourceMapper` ft_resource_title_desc |
| 多维筛选 (分类/标签/评分/时间/排序) | ✅ | `ResourceService.java` |
| 热度分定时计算 | ✅ | `HotScoreTask.java` |

### 3.3 AI 增强

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| AI 智能摘要生成 | ✅ | `AiService.generateSummary()` |
| 摘要 Redis 缓存 (24h TTL) | ✅ | `AiService` + Redis `ai:summary:{md5}` |
| 自然语言搜索 (NL2API) | ✅ | `AiService.parseNaturalLanguageQuery()` + 本地规则兜底 → `SearchService` (需登录, 前端 Toast 提示) |
| NL 搜索 Redis 缓存 (1h TTL) | ✅ | `AiService` + Redis `ai:nl:{md5}` |
| AI 搜索意图展示 | ✅ | `Search.vue` parsedIntent |
| 个性化推荐 (标签+协同过滤+热度) | ✅ | `RecommendationService.getRecommendations()` |
| 协同过滤算法 (item-based CF) | ✅ | `RecommendationService.collaborativeFilter()` |
| 混合推荐 (标签0.6 + CF0.4) | ✅ | `RecommendationService` mergedScores |
| AI 推荐理由生成 + 缓存 (6h) | ✅ | `AiService.generateRecommendReason()` |
| 推荐结果 Redis 缓存 (30min) | ✅ | `RecommendationService` + Redis `recommend:user:{id}` |
| DashScope OpenAI 兼容调用 | ✅ | `AiService.callDashscope()` (阿里云百炼 Qwen, 可配置超时和生成参数) |
| AI 悬浮聊天窗 | ✅ | `AiChatWidget.vue` + `POST /api/v1/ai/chat/stream` |
| 聊天真实回复优先 + 重试 | ✅ | `AiService.streamChat()` |

### 3.4 社区互动

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| 点赞/取消点赞 (资源+评论) | ✅ | `InteractionService.java` |
| 收藏/取消收藏 | ✅ | `InteractionService.java` |
| 1-5 星评分 (含平均分更新) | ✅ | `InteractionService.java` |
| 多级评论 (一级+回复) | ✅ | `CommentService.java` |
| 评论列表 (嵌套回复) | ✅ | `ResourceDetail.vue` |
| 评论失败提示与回复反馈 | ✅ | `ResourceDetail.vue` |
| 交付文档目录 | ✅ | `docs/PRD.md`、`docs/设计文档.md`、`docs/交付清单.md` |

### 3.5 管理后台

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| 深色侧边栏布局 (slate-900) | ✅ | `Admin.vue` |
| 仪表盘 (4统计卡片+待审+最近用户+分类标签) | ✅ | `admin/Dashboard.vue` |
| 用户管理 (列表/角色/启禁用) | ✅ | `admin/Users.vue` |
| 资源审核 (待审/通过/拒绝，卡片式) | ✅ | `admin/Resources.vue` |
| 分类管理 (树形 CRUD) | ✅ | `admin/Categories.vue` |
| 标签管理 (标签云 CRUD) | ✅ | `admin/Tags.vue` |

### 3.6 个人中心

| 功能 | 状态 | 关键文件 |
|------|------|---------|
| 侧边栏布局 (头像/统计/导航) | ✅ | `Profile.vue` |
| 我的发布 / 我的收藏 | ✅ | `Profile.vue` tabs |
| 学习统计 (浏览/点赞/评分/收藏) | ✅ | `Profile.vue` 学习统计 tab |
| 个人信息编辑 | ✅ | `Profile.vue` 编辑资料 tab |
| 头像上传 | ✅ | `Profile.vue` + `UserController.uploadAvatar()` |
| 资源编辑/删除按钮 | ✅ | `Profile.vue` 我的发布列表 |

### 3.7 前端 UI 原型对齐

| 页面 | 状态 | 关键变更 |
|------|------|---------|
| Home (首页) | ✅ | 分类导航栏、渐变卡片、时间格式化 |
| Search (搜索) | ✅ | 评分/类型筛选、排序按钮、结果缩略图 |
| ResourceDetail (详情) | ✅ | 侧边栏推荐+热门标签、操作栏、评论徽章 |
| Publish (发布) | ✅ | 封面上传、AI摘要提示、草稿/预览/发布按钮 |
| Profile (个人中心) | ✅ | 头像上传、编辑/删除按钮 |
| Admin (管理后台) | ✅ | 深色侧边栏、统计卡片、完整CRUD子页面 |

---

## 4. 未完成任务列表

### Phase 7: 测试与优化 (全部待办)

| # | 任务 | 说明 |
|---|------|------|
| 1 | 后端单元测试 (Service 层) | Phase 7.1.1 |
| 2 | 后端集成测试 (Controller 层) | Phase 7.1.2 |
| 3 | API 接口测试 (Pytest) | Phase 7.1.3, tests/test_api.py 框架已就绪 |
| 4 | AI 模块测试 (Mock LLM) | Phase 7.1.4 |
| 5 | 前端组件单元测试 (Vitest) | Phase 7.2.1 |
| 6 | E2E 测试 (Playwright) | Phase 7.2.2, tests/test_ui.py 框架已就绪 |
| 7 | 后端性能优化 (N+1 查询/缓存) | Phase 7.3.1 |
| 8 | 前端性能优化 (懒加载/虚拟滚动) | Phase 7.3.2 |
| 9 | 安全加固 (SQL注入/XSS/敏感词) | Phase 7.4.1 - 7.4.4 |

---

## 5. 数据库设计

### 5.1 表结构总览 (13 张表)

```
┌─────────────────────────────────────────────────────────────────┐
│                        数据库: learning_platform                  │
├──────────────────┬──────────────────────────────────────────────┤
│  user            │  用户表 (id, username, email, password_hash,  │
│                  │  nickname, avatar_url, bio, role, points)     │
├──────────────────┼──────────────────────────────────────────────┤
│  category        │  分类表 (id, name, parent_id, sort_order)     │
│                  │  支持二级分类树                                 │
├──────────────────┼──────────────────────────────────────────────┤
│  tag             │  标签表 (id, name, usage_count)               │
├──────────────────┼──────────────────────────────────────────────┤
│  resource        │  资源表 (id, title, category_id, publisher_id,│
│                  │  description, ai_summary, resource_type,      │
│                  │  external_url, view/like/fav/comment/rating,  │
│                  │  hot_score, status)                           │
│                  │  索引: FULLTEXT(title, description)           │
├──────────────────┼──────────────────────────────────────────────┤
│  resource_tag    │  资源-标签关联 (resource_id, tag_id)           │
│  resource_file   │  资源文件 (resource_id, file_name/url/size)   │
├──────────────────┼──────────────────────────────────────────────┤
│  comment         │  评论表 (id, resource_id, user_id, parent_id, │
│                  │  content, like_count, status)                 │
│                  │  支持二级嵌套 (parent_id 自引用)               │
├──────────────────┼──────────────────────────────────────────────┤
│  like_record     │  点赞记录 (user_id, target_id, target_type)   │
│                  │  target_type: RESOURCE / COMMENT              │
├──────────────────┼──────────────────────────────────────────────┤
│  favorite        │  收藏表 (user_id, resource_id)                │
│  rating          │  评分表 (user_id, resource_id, score 1-5)     │
├──────────────────┼──────────────────────────────────────────────┤
│  search_history  │  搜索历史 (user_id, keyword, search_type)     │
│  user_interest   │  用户兴趣标签 (user_id, tag_id, weight)        │
│  recommendation_log │ 推荐日志 (user_id, resource_id, reason,   │
│                  │  algorithm)                                   │
└──────────────────┴──────────────────────────────────────────────┘
```

### 5.2 关键索引策略

- `resource`: FULLTEXT 索引 `ft_resource_title_desc(title, description)` 使用 ngram 解析器
- `resource`: `hot_score DESC` 用于热门排序
- `like_record`: 唯一索引 `(user_id, target_id, target_type)` 保证幂等
- `comment`: `parent_id` 索引加速嵌套查询

---

## 6. 核心代码逻辑说明

### 6.1 认证流程

```
前端 Login.vue                    后端 AuthController
     │                                  │
     ├── POST /auth/login ──────────────►├── AuthService.login()
     │   {username, password}            │   ├── 校验密码 (BCrypt)
     │                                   │   ├── 生成 JWT Token (24h)
     │◄── {token, refreshToken, user} ───┤   └── 生成 RefreshToken (7d)
     │                                   │
     ├── 存储 token + refreshToken       │
     │   到 localStorage                 │
     │                                   │
     ├── 后续请求自动附加               │
     │   Authorization: Bearer <token>   │
     │                                   │
     ├── 401 响应时                      │
     │   ├── POST /auth/refresh ─────────►├── 验证 refreshToken
     │   └── 重试原始请求                 │   └── 签发新 token
```

**关键代码**: `api/request.ts` - Axios 拦截器自动处理 JWT 注入和 401 刷新

### 6.2 RBAC 权限控制

```
Spring Security @EnableMethodSecurity
│
├── AdminController        @PreAuthorize("hasRole('ADMIN')")
│   ├── GET  /admin/users
│   ├── PUT  /admin/users/{id}/role   (角色值校验: USER/PUBLISHER/ADMIN)
│   ├── GET  /admin/resources/pending
│   ├── PUT  /admin/resources/{id}/audit
│   └── GET  /admin/statistics
│
├── ResourceController
│   ├── POST   /resources    @PreAuthorize("hasAnyRole('PUBLISHER', 'ADMIN')")
│   └── DELETE /resources/{id} @PreAuthorize("hasAnyRole('PUBLISHER', 'ADMIN')")
│
├── UserController          @PreAuthorize("isAuthenticated()")
│   ├── GET  /users/profile
│   ├── PUT  /users/profile
│   ├── POST /users/avatar
│   └── GET  /users/favorites, /resources, /statistics
│
└── InteractionController
    ├── POST   /resources/{id}/like      @PreAuthorize("isAuthenticated()")
    ├── POST   /resources/{id}/favorite  @PreAuthorize("isAuthenticated()")
    ├── POST   /resources/{id}/rating    @PreAuthorize("isAuthenticated()")
    ├── POST   /resources/{id}/comments  @PreAuthorize("isAuthenticated()")
    ├── DELETE /comments/{id}            @PreAuthorize("isAuthenticated()")
    └── POST   /comments/{id}/like       @PreAuthorize("isAuthenticated()")
```

### 6.3 AI 调用链路 (含 Redis 缓存)

```
AiService.java (核心 AI 服务 + Redis 缓存)
│
├── generateSummary(title, description)
│   ├── 缓存检查: ai:summary:{md5(title+desc)} → 命中则返回
│   └── 缓存未命中 → callDashscope(prompt) → 写入缓存 (24h TTL)
│
├── parseNaturalLanguageQuery(query)
│   ├── 缓存检查: ai:nl:{md5(query)} → 命中则返回
│   └── 缓存未命中 → callDashscope(prompt) → 写入缓存 (1h TTL)
│
└── generateRecommendReason(userInterests, title, desc)
    ├── 缓存检查: ai:reason:{md5(interests+title)} → 命中则返回
    └── 缓存未命中 → callDashscope(prompt) → 写入缓存 (6h TTL)
```

**调用方式**: OpenAI 兼容 HTTP 调用阿里云百炼 Qwen，默认模型由 `AI_MODEL` 配置。
**超时配置**: `AI_CONNECT_TIMEOUT_MS` 默认 3000ms，`AI_READ_TIMEOUT_MS` 默认 8000ms。
**降级策略**: Redis 不可用时跳过缓存；AI key 未配置、超时或调用失败时，自然语言搜索使用本地意图解析并放宽空结果筛选，推荐理由使用本地可解释文案，聊天接口返回明确失败原因。

### 6.4 推荐算法 (RecommendationService - 混合推荐)

```
用户请求推荐
    │
    ├── 缓存检查: recommend:user:{userId}:{limit} → 命中则返回 (30min TTL)
    │
    ├── 提取用户交互历史 (点赞+收藏的资源ID)
    │
    ├── 无交互历史? → 返回全局热门资源 (HOT)
    │
    ├── 路径 A: 标签推荐 (权重 0.6)
    │   ├── 从交互历史提取兴趣标签 (点赞+3, 收藏+5)
    │   ├── 取 Top 5 标签 → 查找匹配资源 (排除已交互)
    │   └── 按标签匹配度打分
    │
    ├── 路径 B: 协同过滤 (权重 0.4)
    │   ├── 找到与目标用户交互过相同资源的相似用户
    │   ├── 取 Top 20 相似用户
    │   ├── 收集相似用户喜欢的资源 (排除已交互)
    │   └── 按相似用户投票数打分
    │
    ├── 合并分数: tag_score * 0.6 + cf_score * 0.4
    │
    ├── 取 Top N → 按合并分数排序
    │
    ├── 前 5 条调用 AI 生成推荐理由 (缓存 6h)，失败则使用本地兴趣/评分/热度理由
    │
    └── 写入 Redis 缓存 (30min TTL)
```

### 6.5 Redis 缓存策略

| 缓存项 | Key 格式 | TTL | 说明 |
|--------|----------|-----|------|
| AI 摘要 | `ai:summary:{md5}` | 24h | 避免重复调用 LLM 生成摘要 |
| NL 搜索解析 | `ai:nl:{md5}` | 1h | 缓存自然语言解析结果 |
| 推荐理由 | `ai:reason:{md5}` | 6h | 缓存个性化推荐文案 |
| 推荐列表 | `recommend:user:{id}:{limit}` | 30min | 缓存用户推荐结果 |
| 热搜榜 | `search:hot` | 7d | 热门搜索关键词 (List) |
| 搜索历史 | `search:history:{userId}` | 30d | 用户个人搜索历史 (List) |
| Token 黑名单 | `token:blacklist:{token}` | 24h | 已注销的 JWT Token |

**降级策略**: 所有 Redis 操作均有 try-catch 保护，Redis 不可用时优雅降级（跳过缓存/历史记录）

### 6.6 前端状态管理 (Pinia)

```typescript
// store/user.ts - 用户状态
token: string           // JWT Token (持久化到 localStorage)
refreshToken: string    // 刷新 Token
userInfo: User | null   // 用户信息
isLoggedIn: computed    // !!token
isAdmin: computed       // role === 'ADMIN'
isPublisher: computed   // role === 'PUBLISHER' || isAdmin
```

### 6.7 路由守卫

```typescript
// router/index.ts
beforeEach:
  requiresAuth → 未登录 → redirect /login
  requiresAdmin → 非管理员 → redirect /
  requiresPublisher → 非发布者 → redirect /
  设置 document.title
```

---

## 7. 环境依赖与启动

### 7.1 环境要求

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Spring Boot 3 要求 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 (Vite) |
| MySQL | 8.0+ | 数据库 (需要 ngram 全文解析器) |
| Redis | 6.0+ | 缓存 (可选，无 Redis 时优雅降级) |
| Python | 3.9+ | 测试脚本 (可选) |

### 7.2 环境变量

```bash
# 后端 (application.yml 中均有默认值)
DB_PASSWORD=root            # MySQL 密码
REDIS_HOST=localhost        # Redis 地址 (可选)
JWT_SECRET=<32+字符密钥>     # JWT 签名密钥
AI_API_KEY=<DashScope API Key> # AI 服务密钥 (阿里云百炼 Qwen)
AI_CONNECT_TIMEOUT_MS=3000     # AI 连接超时
AI_READ_TIMEOUT_MS=8000        # AI 读取超时
AI_TEMPERATURE=0.2             # AI 生成温度
AI_MAX_TOKENS=512              # AI 最大输出 token
STORAGE_TYPE=local          # 文件存储类型 (local 或 oss)
STORAGE_PATH=./uploads      # 本地存储路径
OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com  # 阿里云 OSS Endpoint
OSS_BUCKET=ai-learn-share-platform        # OSS Bucket 名称
OSS_ACCESS_KEY_ID=         # 阿里云 AccessKey ID (填写在 application-local.yml)
OSS_ACCESS_KEY_SECRET=     # 阿里云 AccessKey Secret (填写在 application-local.yml)
```

### 7.3 快速启动

```bash
# 1. 数据库
mysql -u root -p < db/init.sql      # 建库建表
mysql -u root -p < db/seed.sql      # 统一测试数据

# 2. 后端
cd backend
mvn clean install
mvn spring-boot:run                  # http://localhost:8080

# 3. 前端
cd frontend
npm install
npm run dev                          # http://localhost:5173

# 4. 测试账号
# 管理员: admin / admin123
# 发布者: publisher / publisher123
# 测试用户: zhangsan / 123456 (seed.sql)
```

### 7.4 API 文档

后端启动后访问: `http://localhost:8080/swagger-ui.html`

---

## 8. 后续开发注意事项

### 8.1 架构约束

- **无状态认证**: JWT + RefreshToken 模式，不使用 Session，适合水平扩展
- **逻辑删除**: 所有核心表使用 `is_deleted` 字段，MyBatis-Plus 全局配置自动过滤
- **统一响应**: 所有接口返回 `Result<T>` 格式 `{code, message, data}`
- **分页统一**: 使用 `PageResult<T>` 包装 MyBatis-Plus 分页结果
- **前后端分离**: 前端通过 `/api/v1` 前缀反代到后端 8080 端口
- **Redis 可选**: 所有 Redis 操作均有 try-catch 保护，无 Redis 时降级运行

### 8.2 已知风险点

| 风险 | 说明 | 建议 |
|------|------|------|
| N+1 查询 | RecommendationService 中循环查询标签 | 批量查询 + 内存关联 |
| 浏览量并发 | `getDetail` 中 viewCount 非原子递增 | 使用 Redis 计数器或 SQL `view_count = view_count + 1` |
| Markdown XSS | `v-html` 直接渲染用户输入 | 生产环境需引入 DOMPurify |
| 敏感词过滤缺失 | 评论/描述未做敏感词过滤 | Phase 7.4.4 待实现 |

### 8.3 扩展建议

- **消息通知**: 用户被回复、资源被审核等场景缺少通知
- **积分系统**: `user.points` 字段已定义但未与行为关联
- **文件存储**: 已集成阿里云 OSS (Bucket: ai-learn-share-platform)，通过 `storage.type` 配置切换 local/oss
- **AI 模型切换**: 当前通过 OpenAI 兼容接口对接阿里云百炼，后续可继续扩展其他兼容模型

---

## 9. Git 提交历史

```
29fd4ea fix: 修复管理后台全部功能
c591974 fix: 修复个人中心和发布资源功能
a000553 feat: 集成阿里云OSS文件存储
d57e34a fix: 重写资源详情页交互功能
48d43c8 fix: 修复首页交互和搜索筛选功能
15af760 feat: 添加种子数据图片和链接更新脚本
774818f docs: 更新全部文档反映最新项目状态
38dc7df fix: 替换 alert 为页面内嵌 Toast 提示条
0e764c3 fix: NL 搜索未登录时前端提示并跳转登录页
d0527f3 fix: 搜索无结果时显示当前筛选条件和清除按钮
070d8c8 fix: 修复 seed.sql 字符编码和重复键问题
a93e2e1 docs: 更新文档反映 Spring AI 迁移
d16692b refactor: 迁移 AI 服务从 OkHttp 到 Spring AI 框架
8f3d896 docs: 更新项目文档匹配最新项目状态
496f458 fix: 所有 Redis 调用添加异常保护，支持无 Redis 环境运行
3152267 fix: 修复 RecommendationService 编译类型错误
7484778 docs: 标记已完成的后端任务（RBAC/头像/缓存/协同过滤）
4c65e07 feat: 实现推荐结果 Redis 缓存
455e13a feat: 实现协同过滤推荐算法
329eb96 feat: 实现 AI 摘要/搜索/推荐理由 Redis 缓存
f5612e1 feat: 实现头像上传后端接口
fed0752 feat: 实现 RBAC 方法级权限控制
3ec6c1a feat: 对齐管理后台页与产品原型设计
866b8d6 feat: 对齐个人中心页与产品原型设计
b4e3dd7 feat: 对齐发布资源页与产品原型设计
9c087b6 feat: 对齐资源详情页与产品原型设计
a48dc97 feat: 对齐搜索页与产品原型设计
14957de feat: 对齐首页与产品原型设计
a377f44 feat: fix Phase 6 user center gaps
9b4f10b feat: fix Phase 5 AI module gaps
91dd343 docs: mark Phase 2/3/4 tasks as complete in tasks.md
7ddd6d4 feat: fix Phase 3 resource module gaps
270f7bc feat: add HTML page prototypes and update configs
af42c1b feat: init AI personalized learning resource sharing platform
```

---

## 10. 关键依赖版本锁定

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.5 | 后端框架 |
| MyBatis-Plus | 3.5.5 | ORM |
| JJWT | 0.12.5 | JWT 库 |
| Hutool | 5.8.26 | Java 工具库 |
| SpringDoc | 2.4.0 | Swagger UI |
| DashScope OpenAI Compatible API | - | 阿里云百炼 Qwen 接入方式 |
| Aliyun OSS SDK | 3.17.4 | 阿里云对象存储 |
| Lettuce | 6.3.2 | Redis 客户端 |
| Vue | 3.4.21 | 前端框架 |
| Vue Router | 4.3.0 | 路由 |
| Pinia | 2.1.7 | 状态管理 |
| Axios | 1.6.8 | HTTP 客户端 |
| marked | latest | Markdown 渲染 |
| Tailwind CSS | 3.4.3 | CSS 框架 |
| Vite | 5.2.8 | 构建工具 |
| TypeScript | 5.4.0 | 类型系统 |
