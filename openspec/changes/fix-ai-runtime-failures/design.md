## Context

当前系统已经具备基础 AI 能力和推荐算法，但运行时可靠性不足。外部 AI 服务应该作为增强能力，而不是搜索与推荐的单点依赖。

## Goals

- 自然语言搜索在 AI 服务不可用、返回非 JSON、返回过严条件时仍能检索数据库。
- 推荐列表和推荐理由接口稳定返回前端可消费的数据结构。
- AI 摘要、搜索解析、推荐理由调用都有明确超时边界。
- 前端能给出清楚的 AI 搜索解析反馈，避免用户只看到“暂无搜索结果”。

## Non-Goals

- 不新增数据库表。
- 不重构为完整向量检索或 RAG。
- 不要求必须启用 Spring AI function calling；本次优先修复可用性和契约断点。

## Decisions

### D1: 自然语言搜索采用“AI 解析 + 本地解析 + 放宽重试”

流程：

1. 优先调用 AI 获取结构化意图。
2. 对 AI JSON 做字段归一化：关键词、排序、数量、评分、标签。
3. AI 失败时使用本地规则解析常见中文检索意图。
4. 首轮查询 0 条时，依次放宽标签、分类、评分过滤，保留关键词和排序。

### D2: AI HTTP 调用设置显式超时

使用 `RestTemplateBuilder` 创建 `RestTemplate`，配置：

- `ai.client.connect-timeout`
- `ai.client.read-timeout`
- `ai.client.max-tokens`
- `ai.client.temperature`

摘要和推荐理由失败时返回 `null`，由业务服务负责本地降级。

### D3: 推荐接口返回分页结构

`GET /api/v1/ai/recommendations` 返回 `PageResult<Map<String,Object>>`，支持 `page`、`size` 和兼容旧参数 `limit`。前端同时兼容旧 List，降低联调风险。

### D4: 推荐理由本地降级

当 AI 不可用时，根据用户兴趣、资源标签、评分和热度生成短理由，确保“AI 推荐理由”区域始终可用。

## Risks / Trade-offs

- 本地自然语言解析不能覆盖所有表达，但能覆盖“Java 并发、评分最高、前 5 个”等高频表达。
- 放宽过滤可能返回更宽泛结果，因此解析意图中需要保留实际使用条件，前端显示给用户。
- AI function calling 更严格，但当前代码没有 Spring AI ChatClient 接入层；直接引入会扩大改动面。
