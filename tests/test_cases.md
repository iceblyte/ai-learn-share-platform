# AI 个性化学习资源分享平台 - 测试用例表

## 1. 用户认证模块 (P0)

| 用例ID | 模块 | 用例名称 | 前置条件 | 测试步骤 | 预期结果 | 优先级 |
|--------|------|---------|---------|---------|---------|--------|
| TC-AUTH-001 | 注册 | 正常注册 | 无 | 1. 输入合法用户名/邮箱/密码 2. 点击注册 | 注册成功，跳转首页，返回JWT Token | P0 |
| TC-AUTH-002 | 注册 | 重复用户名注册 | 用户名已存在 | 1. 输入已存在的用户名 2. 点击注册 | 提示"用户名已存在" | P0 |
| TC-AUTH-003 | 注册 | 无效邮箱注册 | 无 | 1. 输入无效邮箱格式 2. 点击注册 | 提示"邮箱格式不正确" | P1 |
| TC-AUTH-004 | 注册 | 密码过短 | 无 | 1. 输入少于6位密码 2. 点击注册 | 提示"密码长度6-20位" | P1 |
| TC-AUTH-005 | 登录 | 正常登录 | 已注册用户 | 1. 输入正确用户名/密码 2. 点击登录 | 登录成功，跳转首页 | P0 |
| TC-AUTH-006 | 登录 | 错误密码登录 | 已注册用户 | 1. 输入错误密码 2. 点击登录 | 提示"用户名或密码错误" | P0 |
| TC-AUTH-007 | 登录 | 不存在的用户 | 无 | 1. 输入不存在的用户名 2. 点击登录 | 提示"用户名或密码错误" | P1 |
| TC-AUTH-008 | Token | Token 自动刷新 | 已登录 | 1. Token 接近过期 2. 发起请求 | 自动刷新 Token，请求成功 | P1 |
| TC-AUTH-009 | 退出 | 正常退出 | 已登录 | 1. 点击退出登录 | Token 失效，跳转首页 | P1 |

## 2. 资源管理模块 (P0)

| 用例ID | 模块 | 用例名称 | 前置条件 | 测试步骤 | 预期结果 | 优先级 |
|--------|------|---------|---------|---------|---------|--------|
| TC-RES-001 | 列表 | 获取资源列表 | 有已发布资源 | 1. GET /api/v1/resources | 返回分页资源列表 | P0 |
| TC-RES-002 | 列表 | 按分类筛选 | 有已发布资源 | 1. GET /api/v1/resources?categoryId=1 | 只返回该分类的资源 | P0 |
| TC-RES-003 | 列表 | 按关键词搜索 | 有已发布资源 | 1. GET /api/v1/resources?keyword=Java | 返回标题/描述包含Java的资源 | P0 |
| TC-RES-004 | 列表 | 按热度排序 | 有已发布资源 | 1. GET /api/v1/resources?sortBy=hot | 按hot_score降序排列 | P1 |
| TC-RES-005 | 详情 | 获取资源详情 | 资源存在 | 1. GET /api/v1/resources/{id} | 返回完整资源信息，viewCount+1 | P0 |
| TC-RES-006 | 详情 | 获取不存在资源 | 无 | 1. GET /api/v1/resources/99999 | 返回404 | P1 |
| TC-RES-007 | 发布 | 发布者发布资源 | 用户角色=PUBLISHER | 1. POST /api/v1/resources (合法数据) | 资源创建成功，返回资源信息 | P0 |
| TC-RES-008 | 发布 | 普通用户发布资源 | 用户角色=USER | 1. POST /api/v1/resources | 返回403无权限 | P0 |
| TC-RES-009 | 发布 | 标题为空发布 | PUBLISHER | 1. POST /api/v1/resources (title为空) | 返回400参数错误 | P1 |
| TC-RES-010 | 删除 | 发布者删除自己的资源 | 资源所有者 | 1. DELETE /api/v1/resources/{id} | 删除成功 | P0 |
| TC-RES-011 | 删除 | 删除他人资源 | 非资源所有者 | 1. DELETE /api/v1/resources/{id} | 返回403无权限 | P0 |

## 3. 社区互动模块 (P0)

| 用例ID | 模块 | 用例名称 | 前置条件 | 测试步骤 | 预期结果 | 优先级 |
|--------|------|---------|---------|---------|---------|--------|
| TC-INT-001 | 点赞 | 点赞资源 | 已登录 | 1. POST /api/v1/resources/{id}/like | liked=true, likeCount+1 | P0 |
| TC-INT-002 | 点赞 | 取消点赞 | 已点赞 | 1. POST /api/v1/resources/{id}/like | liked=false, likeCount-1 | P0 |
| TC-INT-003 | 收藏 | 收藏资源 | 已登录 | 1. POST /api/v1/resources/{id}/favorite | favorited=true, favoriteCount+1 | P0 |
| TC-INT-004 | 评分 | 评分资源 | 已登录 | 1. POST /api/v1/resources/{id}/rating (score=5) | 评分成功，avgRating更新 | P0 |
| TC-INT-005 | 评分 | 评分范围校验 | 已登录 | 1. POST /api/v1/resources/{id}/rating (score=6) | 返回400参数错误 | P1 |
| TC-INT-006 | 评论 | 发表一级评论 | 已登录 | 1. POST /api/v1/resources/{id}/comments | 评论成功，commentCount+1 | P0 |
| TC-INT-007 | 评论 | 发表回复 | 已登录 | 1. POST /api/v1/resources/{id}/comments (parentId=X) | 回复成功 | P1 |
| TC-INT-008 | 评论 | 删除自己的评论 | 评论所有者 | 1. DELETE /api/v1/comments/{id} | 删除成功 | P1 |
| TC-INT-009 | 评论 | 删除他人评论 | 非评论所有者 | 1. DELETE /api/v1/comments/{id} | 返回403 | P1 |

## 4. AI 功能模块 (P1)

| 用例ID | 模块 | 用例名称 | 前置条件 | 测试步骤 | 预期结果 | 优先级 |
|--------|------|---------|---------|---------|---------|--------|
| TC-AI-001 | 摘要 | 生成资源摘要 | 资源存在 | 1. POST /api/v1/ai/summary | 返回约100字摘要 | P1 |
| TC-AI-002 | NL搜索 | 自然语言搜索 | 已登录 | 1. POST /api/v1/search/nl ("推荐Java并发最高评分前5") | 返回解析意图和搜索结果 | P1 |
| TC-AI-003 | 推荐 | 获取个性化推荐 | 已登录，有浏览历史 | 1. GET /api/v1/ai/recommendations | 返回推荐列表+推荐理由 | P1 |

## 5. 管理员模块 (P1)

| 用例ID | 模块 | 用例名称 | 前置条件 | 测试步骤 | 预期结果 | 优先级 |
|--------|------|---------|---------|---------|---------|--------|
| TC-ADM-001 | 用户 | 查看用户列表 | 管理员 | 1. GET /api/v1/admin/users | 返回用户分页列表 | P1 |
| TC-ADM-002 | 用户 | 修改用户角色 | 管理员 | 1. PUT /api/v1/admin/users/{id}/role | 角色更新成功 | P1 |
| TC-ADM-003 | 审核 | 通过资源审核 | 管理员 | 1. PUT /api/v1/admin/resources/{id}/audit (APPROVE) | status变为PUBLISHED | P1 |
| TC-ADM-004 | 审核 | 拒绝资源审核 | 管理员 | 1. PUT /api/v1/admin/resources/{id}/audit (REJECT) | status变为REJECTED | P1 |
| TC-ADM-005 | 统计 | 查看平台统计 | 管理员 | 1. GET /api/v1/admin/statistics | 返回用户数、资源数等 | P1 |
| TC-ADM-006 | 权限 | 普通用户访问管理接口 | 普通用户 | 1. GET /api/v1/admin/users | 返回403 | P0 |
