-- AI 功能配置变更记录
-- 日期: 2026-05-18
-- 变更: 模型名 gemini-pro → gemini-2.0-flash (gemini-pro 已被 Google 废弃)

-- 无表结构变更，仅配置变更:
-- application.yml: spring.ai.google.genai.chat.options.model = gemini-2.0-flash
-- pom.xml: Spring AI BOM 保持 1.1.6，Spring Boot 保持 3.2.5
