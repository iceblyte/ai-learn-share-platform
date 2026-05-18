# AI 个性化学习资源分享平台

一个面向大学生的学习资源共享社区，利用 AI 技术实现智能推荐、自然语言搜索和自动摘要。

## 技术栈

- **前端**: Vue 3 + TypeScript + Tailwind CSS + marked (Markdown 渲染)
- **后端**: Spring Boot 3 (Java 17) + MyBatis-Plus
- **数据库**: MySQL 8.0 + Redis
- **AI**: 阿里云百炼 Qwen（OpenAI 兼容 HTTP 接口）
- **文件存储**: 阿里云 OSS (生产) / 本地文件系统 (开发回退)
- **构建**: Vite (前端) / Maven (后端)

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 6.0+

## 快速开始

### 1. 数据库初始化

```powershell
# 建库建表
mysql -u root -p --default-character-set=utf8mb4 < db/init.sql

# 导入统一测试数据
mysql -u root -p --default-character-set=utf8mb4 < db/seed.sql
```

### 2. 后端启动

```powershell
cd backend

# 配置环境变量（可选，也可直接修改 application.yml）
$env:DB_PASSWORD = "your_mysql_password"
$env:REDIS_HOST = "localhost"
$env:JWT_SECRET = "your_jwt_secret_key_at_least_32_chars"
$env:AI_API_KEY = "your_dashscope_api_key"
$env:AI_CONNECT_TIMEOUT_MS = "5000"
$env:AI_READ_TIMEOUT_MS = "20000"
$env:AI_CHAT_TIMEOUT_MS = "120000"

# 阿里云 OSS 配置 (文件存储，密钥填写在 application-local.yml)
$env:STORAGE_TYPE = "oss"   # 或 "local" 使用本地存储

# 编译并运行
mvn clean install
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 3. 前端启动

```powershell
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 `http://localhost:5173`

### 4. 访问平台

- 前端页面: http://localhost:5173
- API 文档: http://localhost:8080/swagger-ui.html
- 管理员账号: admin / admin123
- 测试发布者: zhangsan / 123456 (需先执行 seed.sql)
- 测试普通用户: zhaoliu / 123456 (需先执行 seed.sql)

## 项目结构

```
AI个性化学习资源分享平台/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/learning/platform/
│   │   ├── config/            # 配置类 (Security, Redis, MyBatis, CORS)
│   │   ├── common/            # 通用类 (Result, PageResult, BusinessException)
│   │   ├── controller/        # 控制器层
│   │   ├── service/           # 服务层
│   │   ├── mapper/            # 数据访问层
│   │   ├── entity/            # 实体类
│   │   ├── dto/               # 数据传输对象
│   │   ├── vo/                # 视图对象
│   │   ├── security/          # 安全模块 (JWT Filter)
│   │   ├── interceptor/       # 拦截器
│   │   └── util/              # 工具类 (JWT, File)
│   └── src/main/resources/
│       └── application.yml    # 应用配置
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/               # API 调用层
│   │   ├── components/        # 公共组件
│   │   ├── views/             # 页面组件
│   │   ├── router/            # 路由配置
│   │   ├── store/             # Pinia 状态管理
│   │   ├── types/             # TypeScript 类型定义
│   │   ├── utils/             # 工具函数
│   │   └── assets/            # 静态资源
│   └── package.json
│
├── db/                         # 数据库脚本
│   ├── init.sql               # 建库建表脚本
│   └── seed.sql               # 统一测试数据脚本
│
├── docs/                       # 最终交付文档
│   ├── PRD.md                 # 产品需求文档
│   ├── 设计文档.md            # 系统设计与数据库/API说明
│   └── 交付清单.md            # 交付物总览
│
├── AI_Dev_Log.md               # AI 开发日志
├── docs/archive/               # 历史过程文档归档
└── README.md
```

## 核心功能

1. **用户与权限管理**: JWT + RefreshToken 认证, Spring Security RBAC (USER/PUBLISHER/ADMIN), 头像上传 (阿里云 OSS)
2. **资源管理**: 发布、浏览、搜索学习资源 (文件上传/外部链接), 分类树, 标签系统, Markdown 渲染 (marked)
3. **AI 智能摘要**: 基于阿里云百炼 Qwen 自动生成约100字摘要，支持 Redis 缓存 24h、HTTP 超时和未配置密钥快速降级
4. **自然语言搜索**: 支持自然语言查询，AI 解析优先，本地规则兜底并在空结果时自动放宽过严筛选
5. **个性化推荐**: 混合推荐算法 (标签权重 0.6 + 协同过滤 0.4)，推荐接口返回分页结构，AI 推荐理由失败时使用本地可解释理由兜底
6. **AI 悬浮聊天窗**: 全局可拖动聊天按钮与悬浮窗，支持流式输出、页面上下文透传和失败重试
7. **社区互动**: 点赞/收藏状态高亮、星级评分悬停预览、分享链接复制、多级评论点赞，评论提交与回复具备明确错误提示
8. **管理后台**: 用户管理 (分页+角色修改)、资源审核 (确认弹窗+查看详情)、分类/标签 CRUD、平台统计仪表盘
9. **文件存储**: 阿里云 OSS (生产环境) / 本地文件系统 (开发回退), 通过 `storage.type` 配置切换
10. **Redis 缓存策略**: AI 响应缓存 + 推荐缓存 + 搜索历史 + Token 黑名单, 所有 Redis 操作均优雅降级

## API 接口

启动后端后可访问 Swagger 文档: http://localhost:8080/swagger-ui.html

AI 相关接口：

- `POST /api/v1/ai/summary`: 生成或刷新资源摘要。
- `POST /api/v1/search/nl`: 自然语言搜索，返回解析意图、结果列表和总数。
- `GET /api/v1/ai/recommendations?page=1&size=10`: 获取分页个性化推荐。
- `POST /api/v1/ai/recommendations/reasons`: 批量生成推荐理由，AI 不可用时返回本地理由。
- `POST /api/v1/ai/chat/stream`: AI 聊天流式输出接口，前端悬浮聊天窗使用。

## 运行时说明

- 如果 DashScope 账号对当前 `AI_MODEL` 没有额度或权限，聊天接口会返回明确失败提示，不再伪装为 AI 已回答。
- 推荐、摘要、搜索都带本地降级路径；聊天功能会优先等待真实 AI 回复，并按配置进行重试。

## 交付文档

- 产品需求文档：`docs/PRD.md`
- 系统设计文档：`docs/设计文档.md`
- 交付物清单：`docs/交付清单.md`
- 需求原型：`prototypes/README.md`
- Spec 文档：`openspec/proposal.md`、`openspec/design.md`、`openspec/tasks.md`
- 测试资产：`tests/test_cases.md`、`tests/test_api.py`、`tests/test_ui.py`、`tests/test_report.md`

## 许可证

MIT License
