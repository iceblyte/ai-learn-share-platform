# AI 个性化学习资源分享平台

一个面向大学生的学习资源共享社区，利用 AI 技术实现智能推荐、自然语言搜索和自动摘要。

## 技术栈

- **前端**: Vue 3 + TypeScript + Tailwind CSS + marked (Markdown 渲染)
- **后端**: Spring Boot 3 (Java 17) + MyBatis-Plus
- **数据库**: MySQL 8.0 + Redis
- **AI**: Spring AI 1.1.6 (阿里云百炼 Qwen)
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
# 建表 + 初始数据 (分类、标签、管理员账号)
mysql -u root -p --default-character-set=utf8mb4 < db/init.sql

# 导入测试种子数据 (可选, 22个资源 + 评论/点赞/评分)
mysql -u root -p --default-character-set=utf8mb4 < db/seed.sql

# 更新种子数据的在线图片/头像 URL (可选)
mysql -u root -p --default-character-set=utf8mb4 < db/seed_images.sql
```

### 2. 后端启动

```powershell
cd backend

# 配置环境变量（可选，也可直接修改 application.yml）
$env:DB_PASSWORD = "your_mysql_password"
$env:REDIS_HOST = "localhost"
$env:JWT_SECRET = "your_jwt_secret_key_at_least_32_chars"
$env:AI_API_KEY = "your_dashscope_api_key"

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
│   ├── init.sql               # 建表 + 初始数据
│   ├── seed.sql               # 测试种子数据 (可选, 支持重复执行)
│   └── seed_images.sql        # 更新种子数据为在线图片 URL (可选)
│
├── openspec/                   # 项目文档
│   ├── proposal.md            # PRD 产品需求文档
│   ├── design.md              # 系统设计文档
│   └── tasks.md               # 开发任务拆解 (Phase 1-6 已完成, Phase 7 待完成)
│
└── README.md
```

## 核心功能

1. **用户与权限管理**: JWT + RefreshToken 认证, Spring Security RBAC (USER/PUBLISHER/ADMIN), 头像上传 (阿里云 OSS)
2. **资源管理**: 发布、浏览、搜索学习资源 (文件上传/外部链接), 分类树, 标签系统, Markdown 渲染 (marked)
3. **AI 智能摘要**: 基于 Spring AI (阿里云百炼 Qwen) 自动生成约100字的精准资源摘要 (Redis 缓存 24h)
4. **自然语言搜索**: 支持自然语言查询，AI 解析为结构化搜索 (NL2API, 需登录)
5. **个性化推荐**: 混合推荐算法 (标签权重 0.6 + 协同过滤 0.4), AI 生成推荐理由, Redis 缓存 30min
6. **社区互动**: 点赞/收藏状态高亮、星级评分悬停预览、分享链接复制、多级评论点赞
7. **管理后台**: 用户管理 (分页+角色修改)、资源审核 (确认弹窗+查看详情)、分类/标签 CRUD、平台统计仪表盘
8. **文件存储**: 阿里云 OSS (生产环境) / 本地文件系统 (开发回退), 通过 `storage.type` 配置切换
9. **Redis 缓存策略**: AI 响应缓存 + 推荐缓存 + 搜索历史 + Token 黑名单, 所有 Redis 操作均优雅降级

## API 接口

详见 [design.md](openspec/design.md) 中的 RESTful API 设计部分。

启动后端后可访问 Swagger 文档: http://localhost:8080/swagger-ui.html

## 许可证

MIT License
