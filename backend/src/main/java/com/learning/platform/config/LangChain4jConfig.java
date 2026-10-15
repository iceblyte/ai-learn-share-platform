package com.learning.platform.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 扩展三：LangChain4j 真 token 级流式模型。
 *
 * <p>只接管聊天链路（单轮 + 会话记忆）；摘要、自然语言搜索、
 * 推荐理由三条链路仍走 AiService 内的 HTTP 客户端（同为
 * DeepSeek OpenAI 兼容端点），一行不动。</p>
 */
@Configuration
public class LangChain4jConfig {

    @Bean
    public StreamingChatModel streamingChatModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String modelName,
            @Value("${ai.client.temperature:0.2}") double temperature,
            @Value("${ai.chat.timeout-ms:120000}") int timeoutMs) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 30000)))
                .build();
    }
}
