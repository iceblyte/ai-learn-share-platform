# Bug 修复报告（2026-10-16）

两批共 10 个 Bug 已全部修复、验证并推送（origin/main）。以下为任务清单、根因分析、修复内容与自主验证结果。

## 第二批修复（Bug 7-12）

| # | Bug 现象 | 根因 | 状态 |
|---|---------|------|------|
| 7 | AI 对话框随输出变大 | 消息视口 max-h 上限式布局 | ✅ 已修复 |
| 8 | 流式输出先出两字后一口气全出 | 网络分片到达疏密不均，前端直接拼接 | ✅ 已修复 |
| 9 | AI 输出时上滑查看历史被拉回底部 | messages watch 无条件 scrollToBottom | ✅ 已修复 |
| 10 | 历史会话标题无法修改（搜索按消息内容匹配，标题取首条用户输入截断 30 字） | 无重命名功能 | ✅ 已修复 |
| 11 | 无法回复其他人的回复（楼中楼） | 回复按钮只挂在一级评论上 | ✅ 已修复 |
| 12 | 管理员/任何用户发重复内容评论报 500 | idx_comment_unique 唯一索引误伤正常使用 | ✅ 已修复 |

### Bug 7：对话框固定大小
消息视口 `max-h-[26rem]` 改为固定 `h-[26rem]`，AI 输出多少内容面板尺寸都不变。

### Bug 8：流式输出匀速化
网络分片到达节奏不均（先到一两个 token、停顿后剩余内容一次性吐出），直接拼接造成视觉突变。前端加**打字机缓冲队列**：分片先入缓冲，由 20ms/4 字符（约 200 字/秒）的固定节奏定时器匀速上屏；`sending` 状态保持到缓冲全部排空。

### Bug 9：自动滚动不打断阅读
监听视口滚动，仅当用户停留在底部 48px 内才自动滚底；用户自己发消息时恢复自动滚动。

### Bug 10：会话重命名
- **回答**：历史搜索按**消息内容**匹配（非标题）；标题自动取首条用户输入截断 30 字。
- 新增 `PUT /api/v1/chat/sessions/{id}/title`（仅本人，trim、超长截断 60 字）；前端历史列表加内联重命名编辑（铅笔图标）；搜索扩展为**消息内容 OR 标题**，重命名后的会话仍可被搜到。

### Bug 11：楼中楼回复
回复按钮补充到每条嵌套回复上；`parentId` 仍指向一级评论（保持一层楼结构），内容自动加 `回复 @昵称：` 前缀标识回复对象；回复框上方显示目标提示。

### Bug 12：评论唯一索引
- **根因**：`idx_comment_unique (resource_id, user_id, content(191))` 是当初为 seed 幂等加的索引，同一用户对同一资源发相同内容（如两次"加油"）直接 DuplicateKeyException → 500。
- 修复：`init.sql` 移除索引；新增迁移 `sql/migration-drop-comment-unique.sql` 并已对本地库执行；seed.sql 旧评论块（依赖该索引的 INSERT IGNORE）改写为 NOT EXISTS 幂等去重；`GlobalExceptionHandler` 增加 DuplicateKeyException → 409 友好提示兜底。
- **已实测**：重跑 seed 评论数 132 → 132（幂等不重复）；同内容重复评论两次插入均成功。

## 一、第一批 Bug 修复任务列表

| # | Bug 现象 | 根因 | 状态 |
|---|---------|------|------|
| 1 | AI 回复完成后发送按钮一直转圈 | SSE 流永不结束（与 Bug 4 同根因） | ✅ 已修复 |
| 2 | 页面边缘点击对话图标，对话框超出屏幕 | 面板展开后未按展开尺寸重新夹取位置 | ✅ 已修复 |
| 3 | 未登录时持续请求 /auth/refresh，页面卡顿 | refresh 接口未放行 + 前端拦截器自递归 | ✅ 已修复 |
| 4 | SSE event 类型一直是 message，无结束信息 | Flux.create 回调返回后未显式 complete | ✅ 已修复 |

## 二、根因与修复明细

### Bug 1 / Bug 4（同一根因）：SSE 流永不结束

- **根因**：`ChatStreamFactory.stream()` 用 `Flux.create` 包裹阻塞式聊天调用，但 Reactor 的 `Flux.create` 回调返回**并不会自动完成 sink**。聊天逻辑（CountDownLatch）正常结束后 Flux 永不终止 → `concatWith` 的 `done` 事件永远不发、连接不关。前端读循环阻塞在 `reader.read()`，`sending` 永远为 true → 按钮转圈。
- **修复**：
  - 后端 `ChatStreamFactory`：`chatInvocation.accept(sink::next)` 返回后显式 `sink.complete()`（异常路径仍走 `onErrorResume` 发 error 事件）。
  - 前端 `AiChatWidget.vue`：收到 `done` 事件即终止读循环并 `reader.cancel()`，不再单纯依赖服务端关闭连接的时机。

### Bug 2：贴边打开对话框越界

- **根因**：拖动时按当前状态（收起仅 56px 按钮）夹取位置，但展开后面板宽 384px、高约 600px+，右/下边缘打开时整体越出视口；初始位置也在右下角（`width - 80`）。
- **修复**：抽取 `clampWidgetToViewport()`（按展开/收起两种真实尺寸计算），`toggleOpen` 展开渲染完成后重新夹取；拖动复用同一函数。

### Bug 3：未登录时 /auth/refresh 请求风暴

- **根因**（两端叠加）：
  1. 后端 `/api/v1/auth/refresh` 未 `permitAll`，未登录调用被 Security 入口点以 HTTP 401 拒绝；
  2. 前端 axios 错误拦截器把 401 一律当作"需要刷新"，而刷新请求自身也走同一拦截器 → `refreshToken()` 自我递归，无限循环。
  首页/详情页的 `aiApi.getRecommendations()`（需登录）在未登录时 401，即触发上述风暴。
- **修复**：
  - 后端 `SecurityConfig`：放行 `POST /api/v1/auth/refresh`（请求体中的 refreshToken 即凭证，刷新接口本应匿名可达）。
  - 前端 `request.ts`：仅 401 触发刷新；刷新接口自身/无 refreshToken/已重试过（`_retry` 标记）一律不再刷新；403（已登录无权限）不再触发刷新；访客 401 仅清理本地状态、由页面 catch 兜底，不再误跳登录页。

## 三、提交记录（50/72 规范，6 个提交）

```
cbaf309 test: cover SSE stream done and error termination
3185c9b fix: expose refreshTokenValue from user store
b595016 fix: break endless auth refresh loop for guests
4934bb4 fix: clamp chat widget inside viewport when panel opens
83384bc fix: end chat read loop on SSE done event
91e36ca fix: complete SSE stream so done event is emitted
```

## 四、自主验证结果

| 验证项 | 结果 |
|--------|------|
| 后端 `mvn test`（JDK 17） | ✅ 48 个测试全部通过（含 2 个新增重命名测试 + 2 个 SSE 回归测试），BUILD SUCCESS |
| 前端 `pnpm build`（vue-tsc + vite） | ✅ 类型检查通过，构建成功 |
| 评论唯一索引（数据库实测） | ✅ 本地库已删索引；seed 重跑幂等（132→132）；同内容重复评论可正常插入 |
| Git 推送 | ✅ 全部提交已推送 origin/main（77ba96f..54e7d05） |
| 工作区 | ✅ 全部提交，无未提交改动 |

## 五、遗留问题与建议（非本次 Bug，供决策）

1. **未登录访客仍会发一次注定 401 的推荐请求**（首页/资源详情页调用 `/ai/recommendations`）。页面有 catch 兜底不会卡顿，但属于无效请求。建议：前端未登录时跳过该请求，或后端为游客提供降级推荐。
2. **AI 超时上限 120 秒**（`ai.chat.timeout-ms`）：AI 服务挂起时前端最长要等 2 分钟才能收到 error/done。可考虑缩短或增加 SSE 心跳。
3. **行为变化说明**：403 现在直接放行错误（不再尝试刷新）；访客 401 不再被强制跳转登录页。这两处比原行为更合理，但如需保留旧行为请告知。
4. **窗口缩放**：对话面板展开时缩放浏览器窗口不会重新夹取位置（低频场景，未处理）。
5. **流式匀速是前端渲染层面的保证**：若模型/链路本身长时间不出 token，匀速期同样会出现停顿（缓冲为空时无内容可上屏），属正常现象。
6. **楼中楼采用一层楼结构**：所有回复都挂在一级评论下，回复别人的回复时用 `回复 @昵称：` 前缀区分对象（与主流社区一致）。如需真正的多级嵌套需改表结构与前端展示，工作量大，未做。
7. **历史会话分页**：会话列表固定取前 20 条（page 1, size 20），会话数超过 20 条时无翻页入口（消息已有游标翻页，会话列表暂未做）。

## 六、建议人工复验步骤

1. 重启后端，未登录提问：Network 面板应看到 `event: done` 帧且连接关闭，按钮不再转圈。
2. 登录后连续提问两次：第二次可正常发送；输出应匀速逐步出现；上滑查看历史时不被拉回底部；对话框高度固定不变。
3. 把 AI 悬浮球拖到屏幕右下角再点开：对话框完整留在视口内。
4. 清空 token 访问首页：Network 中 `/auth/refresh` 不应连续出现。
5. 历史会话列表：点铅笔图标重命名标题；用新标题搜索能命中。
6. 资源详情页：回复任意一级评论、再回复别人的回复（出现 `回复 @昵称：` 前缀）。
7. 用 admin 对资源 49 再次评论"加油"：应成功（不再 500）；再发一次相同内容也应成功。
