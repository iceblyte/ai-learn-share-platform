# AI 聊天 Function Calling 改造说明

日期：2026-10-16　提交：`d50e470` / `e2eb7eb` / `9fd89c0`

## 改造前后对比

| | 改造前（提示词注入） | 改造后（function calling） |
|---|---|---|
| 数据获取方 | 后端代码**无条件预取**当前页面资源并拼进 System Prompt | **模型自主决定**调用工具，后端按需查库 |
| 查询范围 | 只有当前页面资源（约 2000 字截断） | 可搜索全库已发布资源、查任意资源详情 |
| 可扩展性 | 加数据要改上下文服务与提示词拼装 | 新增一个 ChatTool 子类即可 |
| 提示词体积 | 每条提问都携带正文片段 | 只带页面元信息（ID/标题/划选），正文按需拉取 |

## 流程

```
用户提问 → [模型] 决定调工具 → ChatToolRegistry 执行（查库）
        → 工具结果回填对话 → [模型] 再生成 → …（≤4 轮）→ 最终回答
```

- token 级流式输出贯穿全程（工具轮次的过渡文本也会实时转发）
- 总超时沿用 `ai.chat.timeout-ms`（120s），覆盖所有轮次
- 会话记忆只落 `user + 最终 assistant 回答`，工具中间轮不进记忆——与 MySQL 会话历史重建逻辑保持一致

## 工具层（可扩展设计）

```
com.learning.platform.ai
├── ChatToolContext      工具执行上下文（userId / pageContextType / pageContextId / selectedText）
├── ChatTool             抽象基类：统一参数解析、异常兜底、结果截断(4000字)、ToolSpecification 转换
├── ChatToolRegistry     自动收集所有 ChatTool Bean；重名校验；未知工具返回错误文本
├── ResourceSearchTool   search_resources：关键词搜索已发布资源（PUBLISHED + 未删除）
└── ResourceDetailTool   get_resource_detail：资源详情；缺省 resourceId 回退当前页面资源
```

**新增工具三步**：

1. 继承 `ChatTool`，实现 `name()` / `description()` / `parameters()` / `doExecute()`；
2. 标注 `@Component`（构造注入所需的 Mapper/Service）；
3. 完成——注册表自动发现，提示词工具清单（`buildToolMode`）与模型可选工具集同步更新。

基类纪律：`doExecute` 抛出的任何异常都被兜底为"工具执行失败：…"文本回传给模型，由模型向用户解释，聊天流永不因工具故障中断。

## 提示词（ChatPromptBuilder.buildToolMode）

- 基础人设不变（V1 逐字保留）；
- 工具清单**动态生成**自注册表（名称 + 描述），新增工具无需改提示词代码；
- 保留轻量元数据：页面标题/路由、当前页面资源 ID、用户划选文本（≤500 字，防注入分隔符包裹）。

## 防护要点

- 工具查询全部强制 `status = PUBLISHED AND is_deleted = 0`，游客与登录用户同一视图；
- 单次工具结果截断 4000 字；工具轮数上限 4；未知工具名不抛异常；
- DeepSeek function calling 由 LangChain4j 1.22.0 `StreamingChatModel.chat(ChatRequest)` 承载。

## 验证

- `mvn test`：**62/62 通过**（新增 AiServiceToolLoopTest 5 项、ChatToolRegistryTest 4 项、ResourceToolsTest 5 项；既有流式/服务测试迁移到 ChatRequest 签名）。
- 前端无需改动：SSE 协议（message/done/error）与请求体结构不变。
- 人工复验建议：登录后在资源页问"这个资源讲什么"（应观察到工具调用日志 `Chat tool invocation: get_resource_detail`）、问"平台有哪些 Java 资源"（应触发 search_resources）。

## 遗留说明

1. 工具轮次的过渡文本（模型边想边说的部分）会进入最终落库的回答文本，属预期行为；
2. `ChatContext` / `ChatContextService` 的预取链路保留（详情工具复用其正文粗排截断），但不再在提问时预查数据库；
3. 模型是否调工具取决于模型判断——简单概念题不会触发查库，属正常。
