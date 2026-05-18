-- AI 功能配置变更记录
-- 日期: 2026-05-18

-- 变更1: gemini-pro → gemini-2.0-flash (gemini-pro 已被 Google 废弃)
-- 变更2: Google Gemini → 阿里云百炼 Qwen (DashScope OpenAI 兼容 API)
--   原因: Gemini 免费额度耗尽(429), 切换至阿里云百炼 qwen-plus-2025-07-28

-- 无表结构变更，仅配置变更:
-- pom.xml: spring-ai-starter-model-google-genai → spring-ai-starter-model-openai
-- application.yml:
--   spring.ai.openai.base-url = https://dashscope.aliyuncs.com/compatible-mode/v1
--   spring.ai.openai.api-key = ${AI_API_KEY}
--   spring.ai.openai.chat.options.model = qwen-plus-2025-07-28
-- 环境变量: AI_API_KEY 改为 DashScope API Key
