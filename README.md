# AI 个性化学习资源分享平台

一个面向大学生的学习资源共享社区，利用 AI 技术实现智能推荐、自然语言搜索和自动摘要。

## 技术栈

- **前端**: Vue 3 + TypeScript + Tailwind CSS
- **后端**: Spring Boot 3 (Java 17)
- **数据库**: MySQL 8.0 + Redis
- **AI**: 兼容 Gemini API
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
# 登录 MySQL 并执行建表脚本
mysql -u root -p < db/init.sql
```

### 2. 后端启动

```powershell
cd backend

# 配置环境变量（可选，也可直接修改 application.yml）
$env:DB_PASSWORD = "your_mysql_password"
$env:REDIS_HOST = "localhost"
$env:JWT_SECRET = "your_jwt_secret_key_at_least_32_chars"
$env:AI_API_KEY = "your_gemini_api_key"

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
│   └── init.sql               # 建表 + 初始数据
│
├── openspec/                   # 项目文档
│   ├── proposal.md            # PRD 产品需求文档
│   ├── design.md              # 系统设计文档
│   └── tasks.md               # 开发任务拆解
│
└── README.md
```

## 核心功能

1. **用户与权限管理**: JWT 认证, RBAC 权限控制 (普通用户/发布者/管理员)
2. **资源管理**: 发布、浏览、搜索学习资源 (文件上传/外部链接)
3. **AI 智能摘要**: 自动生成约100字的精准资源摘要
4. **自然语言搜索**: 支持自然语言查询，AI 解析为结构化搜索
5. **个性化推荐**: 基于标签和行为的推荐 + AI 生成推荐理由
6. **社区互动**: 点赞、收藏、评分、多级评论

## API 接口

详见 [design.md](openspec/design.md) 中的 RESTful API 设计部分。

启动后端后可访问 Swagger 文档: http://localhost:8080/swagger-ui.html

## 许可证

MIT License
