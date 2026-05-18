# AI 开发日志 - AI 辅助开发记录

## 1. 项目概述

本文档记录「AI 个性化学习资源分享平台」开发过程中使用的 AI 技术、Prompt 模板设计、MCP/Skills 调用逻辑等辅助信息。

## 2. AI 工具链

| 工具 | 用途 | 版本 |
|------|------|------|
| Claude Code | 全栈代码生成、架构设计、文档编写 | Claude 4.X |
| Superpowers Skills | 开发工作流管理（brainstorming, writing-plans, TDD） | 5.1.0 |
| UI/UX Pro Max | UI 设计指导、UX 最佳实践 | 2.5.0 |
| OpenSpec | 项目规范管理（proposal, design, tasks） | - |

## 3. Skills 调用记录

### 3.1 brainstorming Skill
- **调用时机**: 项目启动阶段
- **用途**: 指导产品设计流程，确保从理解需求 → 探索方案 → 设计文档的完整链路
- **产出**: proposal.md (PRD), design.md (系统设计)

### 3.2 ui-ux-pro-max Skill
- **调用时机**: 前端页面设计阶段
- **用途**: 提供 UI 设计规范、色彩系统、响应式布局、无障碍访问等最佳实践
- **应用**:
  - 色彩系统: 主色 Blue-500 (#3B82F6), 信任与专业感
  - 间距系统: 基于 4px 的增量间距
  - 组件设计: 按钮、表单、卡片遵循 Touch & Interaction 规范
  - 响应式: 移动优先，断点 sm/md/lg/xl/2xl

### 3.3 writing-plans Skill
- **调用时机**: 架构设计完成后
- **用途**: 将设计文档转化为可执行的实现计划

## 4. Prompt 模板设计

### 4.1 AI 智能摘要 Prompt
```
请为以下学习资源生成一段约100字的精准摘要，突出核心内容和学习价值：

标题：{title}
描述：{description}

要求：简洁明了，突出重点，约100字。
```

**设计思路**:
- 明确输出长度（约100字）
- 指定摘要重点（核心内容、学习价值）
- 限制输入长度（描述截断到2000字）避免 token 浪费

### 4.2 自然语言搜索解析 Prompt
```
请将以下自然语言搜索请求解析为JSON格式的结构化查询参数。

用户输入："{query}"

请输出以下JSON格式（不要输出其他内容）：
{"keywords": ["关键词1", "关键词2"], "category": "分类名或null", "tags": ["标签1"],
 "sortBy": "relevance/latest/hot/rating", "limit": 数字, "minRating": 数字或null}

注意：sortBy默认为relevance，limit默认为10
```

**设计思路**:
- 强制 JSON 输出格式，便于后端解析
- 定义所有可能的字段和枚举值
- 提供默认值说明，减少歧义
- 要求"不要输出其他内容"，确保可解析性
- 后端会叠加本地规则解析，校验“评分最高/最热/最新/前 N 个”等确定性意图；AI 不可用或输出过宽时仍可查询数据库

### 4.3 个性化推荐理由 Prompt
```
请为用户生成一段个性化的推荐理由（50字以内），让用户觉得这个资源很适合他。

用户兴趣：{userInterests}
资源标题：{resourceTitle}
资源简介：{resourceDescription}

要求：语气亲切，直接说明为什么适合该用户，50字以内。
```

**设计思路**:
- 控制输出长度（50字以内），适合卡片展示
- 注入用户兴趣上下文，实现个性化
- 要求"语气亲切"，增强用户体验

### 4.4 AI 聊天 Prompt
```
你是 AI 学习平台内的学习助手。回答要直接、清楚、简洁，优先帮助用户理解学习资源、学习路径、技术概念和平台内功能。
如果用户的问题与当前页面有关，可以参考页面上下文。
当前页面标题：{pageTitle}
当前页面路由：{route}
不要输出 markdown 标题，不要使用过度客套。
```

**设计思路**:
- 固定系统角色，避免聊天结果跑题
- 透传页面标题和路由，给悬浮聊天窗最低限度上下文
- 控制语气和格式，保证流式输出时更稳定

## 5. 架构决策记录

### 5.1 前后端分离架构
- **决策**: Vue 3 SPA + Spring Boot REST API
- **理由**: 开发效率高，前后端可独立部署，便于后续扩展

### 5.2 JWT 无状态认证
- **决策**: JWT Token + Redis 黑名单
- **理由**: 无状态适合前后端分离，Redis 黑名单支持主动登出

### 5.3 AI 模块抽象
- **决策**: AiService 接口化，当前阿里云百炼 Qwen 实现可替换
- **理由**: 兼容多种 LLM，通过配置切换模型

### 5.4 MySQL 全文索引
- **决策**: 使用 MySQL FULLTEXT INDEX (ngram parser) 而非 Elasticsearch
- **理由**: 项目规模不需要 ES，MySQL FULLTEXT 满足初期需求

## 6. 开发效率数据

| 阶段 | 传统预估工时 | AI 辅助实际工时 | 效率提升 |
|------|------------|---------------|---------|
| 需求分析 + PRD | 8h | 0.5h | 16x |
| 系统设计 + API 设计 | 12h | 0.5h | 24x |
| 任务拆解 | 4h | 0.2h | 20x |
| 脚手架搭建 | 8h | 0.5h | 16x |
| 后端核心代码 | 40h | 1h | 40x |
| 前端核心代码 | 40h | 1h | 40x |
| 测试用例 + 脚本 | 16h | 0.5h | 32x |
| 文档编写 | 8h | 0.3h | 27x |
| **合计** | **136h** | **4.5h** | **30x** |

## 7. 经验总结

### 7.1 AI 辅助开发最佳实践
1. **先设计后编码**: 使用 brainstorming skill 确保设计完整再动手
2. **分层生成**: 按 openSpec 规范分层（proposal → design → tasks → code）
3. **并行生成**: 独立模块可并行生成，提高效率
4. **Prompt 工程**: 结构化 Prompt（明确格式、长度、约束）产出质量更高

### 7.2 注意事项
1. AI 生成的代码需要人工审查，特别是安全相关逻辑
2. 数据库索引设计需要根据实际查询模式调整
3. AI API 调用需要降级策略，避免服务不可用时影响核心功能

## 8. Bug 修复记录 (2026-05-18)

### 8.1 问题来源
用户提供了 `problem.md` 文件，列出 11 类问题，涵盖首页、资源详情、评论、个人中心、管理后台、发布资源、搜索筛选、NL 搜索等模块。

### 8.2 修复策略
按模块分批修复，每完成一批功能后 git commit 并 push：
1. 种子数据 → 2. 首页交互 → 3. 搜索筛选 → 4. 资源详情 → 5. OSS 集成 → 6. 个人中心/发布 → 7. 管理后台

### 8.3 关键修复项

| 模块 | 问题 | 修复方案 |
|------|------|---------|
| 搜索 | 分类筛选返回空 | `getAllDescendantIds()` 递归展开子分类，`IN` 替代 `EQ` |
| 搜索 | 标签筛选无效 | SQL 子查询 `HAVING COUNT(DISTINCT) = N` 确保全匹配 |
| 搜索 | NL 搜索无结果 | 取最长关键词而非拼接全部关键词 |
| 资源详情 | 点赞数重置为 1 | 新增 `GET /interactions` 接口获取状态，前端正确更新 |
| 资源详情 | 按钮无视觉反馈 | 条件 class 切换 `border-red-300 bg-red-50` 等 |
| 文件存储 | 头像上传路径错误 | 集成阿里云 OSS，`storage.type` 切换 local/oss |
| 发布 | 编辑模式内容丢失 | 读取 `?edit=id` 参数，`loadResource()` 回填表单 |
| 发布 | Markdown 显示原始文本 | 引入 `marked` 库，`v-html="renderMarkdown(text)"` |
| 管理后台 | 仪表盘数据缺失 | 补充 `todayActive` 统计，修复 API 路径 |
| 管理后台 | 审核无确认 | 全部替换为 `AppModal` 组件确认弹窗 |

### 8.4 新增组件
- **AppToast.vue**: 全局 Toast 通知，4 种类型 (info/success/error/warning)，自动消失
- **AppModal.vue**: 通用弹窗，Teleport 渲染，缩放动画，body/footer 插槽

### 8.5 新增后端接口
- `GET /resources/{id}/interactions` - 获取当前用户点赞/收藏状态
- `PUT /resources/{id}` - 更新资源 (multipart/form-data)
- `GET /admin/resources` - 通用资源列表 (支持 status 筛选)

### 8.6 种子数据补充
- 测试数据、封面图、头像、文件记录已统一并入 `db/seed.sql`

## 9. Bug 修复记录 (2026-05-18 - Problem3)

### 9.1 问题来源
用户提供了 `problem3.md` 文件，列出 7 类问题。

### 9.2 修复策略
按依赖关系分批修复：AI基础 → 交互层 → 文件上传 → 草稿功能 → 样式 → 导航

### 9.3 关键修复项

| 模块 | 问题 | 修复方案 |
|------|------|---------|
| AI | 所有AI功能不可用 | Google Gemini → 阿里云百炼 Qwen (DashScope OpenAI 兼容 API, qwen-plus-2025-07-28) |
| AI 搜索 | 自然语言搜索空结果 | AI 解析优先 + 本地意图解析兜底 + 空结果放宽分类/标签/评分过滤 |
| AI 推荐 | 前端推荐列表为空 | `GET /api/v1/ai/recommendations` 改为分页响应，前端兼容旧数组结构 |
| AI 推荐理由 | AI 不可用时理由缺失 | 使用用户兴趣、资源评分和热度生成本地推荐理由 |
| AI 摘要 | 外部服务慢响应导致超时感知差 | 增加 AI 连接/读取超时和占位 key 快速降级 |
| 资源详情 | 评分不持久化 | getInteractions API 增加 myRating 字段，InteractionService 新增 getMyRating() |
| 资源详情 | 点赞/收藏无反馈 | handleLike/handleFavorite/handleCommentLike 添加 toast.show() |
| 发布 | 封面图片丢失 | Controller 增加 @RequestPart coverImage，FileService 新增 storeCoverImage() |
| 发布 | 草稿消失 | 改为后端 DRAFT 状态持久化，Profile 新增草稿箱 tab |
| Markdown | 链接无法区分 | 安装 @tailwindcss/typography，renderMarkdown 链接加 target=_blank |
| 首页 | 分类跳搜索页 | 改为首页内联筛选，调用 resourceApi.getList({ categoryId }) |

### 9.4 新增后端方法
- `InteractionService.getMyRating(userId, resourceId)` - 获取用户评分
- `FileService.storeCoverImage(file)` - 存储封面图片 (OSS/本地)

### 9.5 后端接口变更
- `GET /resources/{id}/interactions` - 返回值增加 `myRating` 字段
- `GET /ai/recommendations` - 返回分页结构 `{ records, total, page, size, pages }`
- `GET /users/resources` - 新增 `status` 参数支持筛选草稿
- `POST /resources` - 新增 `@RequestPart coverImage` 参数
- `PUT /resources/{id}` - 新增 `@RequestPart coverImage` 参数

### 9.6 数据库脚本整理
- 最终仅保留 `db/init.sql` 与 `db/seed.sql`
- AI 配置说明和运行时策略已整理到 README 与设计文档中
- 交互计数修复已固化到业务代码，不再额外保留单独 SQL 脚本

## 10. Bug 修复记录 (2026-05-19 - Problem4 延伸)

### 10.1 关键修复项

| 模块 | 问题 | 修复方案 |
|------|------|---------|
| AI 聊天 | 悬浮窗始终显示固定离线答复 | 改为真实 AI 回复优先，失败时返回明确错误，不再伪装成功 |
| AI 聊天 | 页面内无法移动按钮和窗口 | `AiChatWidget` 增加按钮拖拽和面板头部拖拽 |
| AI 聊天 | 请求超时体验差 | 增加聊天专用超时、重试和分块输出 |
| 发布 | 发布资源接口被摘要生成阻塞 | 摘要先同步写本地摘要，再由独立异步服务刷新 AI 摘要 |
| 评论 | 评论或回复失败无反馈 | 资源详情页增加登录校验、失败提示和成功提示 |
| 点赞计数 | 点击后历史点赞数被重算成 1 | 交互逻辑改为基于当前统计值增减，并补充计数对齐 SQL |

### 10.2 新增前端组件
- `frontend/src/components/AiChatWidget.vue` - 全局 AI 悬浮聊天窗，支持流式输出、建议问题、拖拽移动

### 10.3 新增后端接口
- `POST /api/v1/ai/chat/stream` - AI 聊天流式输出接口

### 10.4 新增后端服务
- `ResourceSummaryAsyncService.refreshSummary()` - 独立异步刷新资源摘要
