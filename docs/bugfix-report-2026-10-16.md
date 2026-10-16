# Bug 修复报告（2026-10-16）

人工发现的 4 个 Bug 已全部修复并分步提交，验证全部通过。以下为任务清单、根因分析、修复内容与自主验证结果。

## 一、Bug 修复任务列表

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
| 后端 `mvn test`（JDK 17） | ✅ 46 个测试全部通过（44 存量 + 2 新增回归），BUILD SUCCESS |
| 新增 `ChatStreamFactoryTest` | ✅ 修复前该测试会挂起超时，修复后验证：message 分片 → done 收尾 → 流终止；异常路径发 error 事件 |
| 前端 `pnpm build`（vue-tsc + vite） | ✅ 类型检查通过，132 模块构建成功 |
| 工作区 | ✅ 全部提交，无未提交改动 |

## 五、遗留问题与建议（非本次 Bug，供决策）

1. **未登录访客仍会发一次注定 401 的推荐请求**（首页/资源详情页调用 `/ai/recommendations`）。页面有 catch 兜底不会卡顿，但属于无效请求。建议：前端未登录时跳过该请求，或后端为游客提供降级推荐。
2. **AI 超时上限 120 秒**（`ai.chat.timeout-ms`）：AI 服务挂起时前端最长要等 2 分钟才能收到 error/done。可考虑缩短或增加 SSE 心跳。
3. **行为变化说明**：403 现在直接放行错误（不再尝试刷新）；访客 401 不再被强制跳转登录页。这两处比原行为更合理，但如需保留旧行为请告知。
4. **窗口缩放**：对话面板展开时缩放浏览器窗口不会重新夹取位置（低频场景，未处理）。

## 六、建议人工复验步骤

1. 重启后端，未登录提问：Network 面板应看到 `event: done` 帧且连接关闭，按钮不再转圈。
2. 登录后连续提问两次：第二次可正常发送。
3. 把 AI 悬浮球拖到屏幕右下角再点开：对话框完整留在视口内。
4. 清空 token 访问首页：Network 中 `/auth/refresh` 不应连续出现。
