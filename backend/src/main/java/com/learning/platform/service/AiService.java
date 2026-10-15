package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Service
public class AiService {

    /** 路径拆分结果：content 为完整回答；failed 时 errorMessage 非空 */
    public record StreamingResult(String content, Integer tokensUsed, String errorMessage) {
        public boolean failed() {
            return errorMessage != null;
        }
    }

    static final int MEMORY_WINDOW = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryStore chatMemoryStore;
    private final int readTimeoutMs;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${ai.client.temperature:0.2}")
    private double temperature;

    @Value("${ai.client.max-tokens:512}")
    private int maxTokens;

    @Value("${ai.chat.timeout-ms:120000}")
    private int chatTimeoutMs;

    public AiService(RedisTemplate<String, Object> redisTemplate,
                     ObjectMapper objectMapper,
                     @Value("${ai.client.connect-timeout-ms:3000}") int connectTimeoutMs,
                     @Value("${ai.client.read-timeout-ms:8000}") int readTimeoutMs,
                     StreamingChatModel streamingChatModel,
                     ChatMemoryStore chatMemoryStore) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryStore = chatMemoryStore;
    }

    private static final String CACHE_PREFIX_SUMMARY = "ai:summary:";
    private static final String CACHE_PREFIX_NL = "ai:nl:";
    private static final String CACHE_PREFIX_REASON = "ai:reason:";
    private static final long SUMMARY_TTL_HOURS = 24;
    private static final long NL_TTL_HOURS = 1;
    private static final long REASON_TTL_HOURS = 6;

    public String generateSummary(String title, String description) {
        String cacheKey = CACHE_PREFIX_SUMMARY + hash(title + "|" + description);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请为以下学习资源生成一段约100字的精准摘要，突出核心内容和学习价值：\n\n标题：%s\n描述：%s\n\n要求：简洁明了，突出重点，约100字。",
                title, description.length() > 2000 ? description.substring(0, 2000) : description
        );
        String result = callAi(prompt);
        if (result == null) {
            result = buildLocalSummary(title, description);
        }
        if (result != null) cache(cacheKey, result, SUMMARY_TTL_HOURS);
        return result;
    }

    public String generateLocalSummary(String title, String description) {
        return buildLocalSummary(title, description);
    }

    public String parseNaturalLanguageQuery(String query) {
        String cacheKey = CACHE_PREFIX_NL + hash(query);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请将以下自然语言搜索请求解析为JSON格式的结构化查询参数。\n\n" +
                "用户输入：\"%s\"\n\n" +
                "请输出以下JSON格式（不要输出其他内容）：\n" +
                "{\"keywords\": [\"关键词1\", \"关键词2\"], \"category\": \"分类名或null\", \"tags\": [\"标签1\"], " +
                "\"sortBy\": \"relevance/latest/hot/rating\", \"limit\": 数字, \"minRating\": 数字或null}\n\n" +
                "注意：\n" +
                "- keywords 必须是简短的核心搜索词（2-6个字），用于数据库模糊匹配，不要包含动词、虚词或修饰语\n" +
                "- 例如用户说\"推荐关于Java并发且评分最高的前5个资源\"，keywords应为[\"Java并发\"]，而不是[\"推荐关于Java并发且评分最高的前5个资源\"]\n" +
                "- sortBy默认为relevance，limit默认为10", query
        );
        String result = callAi(prompt);
        if (result != null) cache(cacheKey, result, NL_TTL_HOURS);
        return result;
    }

    public String generateRecommendReason(String userInterests, String resourceTitle, String resourceDescription) {
        String cacheKey = CACHE_PREFIX_REASON + hash(userInterests + "|" + resourceTitle);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请为用户生成一段个性化的推荐理由（50字以内），让用户觉得这个资源很适合他。\n\n" +
                "用户兴趣：%s\n资源标题：%s\n资源简介：%s\n\n" +
                "要求：语气亲切，直接说明为什么适合该用户，50字以内。",
                userInterests, resourceTitle,
                resourceDescription.length() > 200 ? resourceDescription.substring(0, 200) : resourceDescription
        );
        String result = callAi(prompt);
        if (result != null) cache(cacheKey, result, REASON_TTL_HOURS);
        return result;
    }

    /**
     * 匿名单轮流式（游客通道）：无记忆，system + user 单轮。
     */
    public StreamingResult streamChat(String message, String systemPrompt, Consumer<String> onChunk) {
        return streamChat(null, message, systemPrompt, onChunk);
    }

    /**
     * 扩展三真流式聊天：LangChain4j OpenAI 兼容客户端直连 DeepSeek。
     *
     * <p>sessionId 非空时启用会话记忆（{@link MessageWindowChatMemory} 20 条窗口，
     * 底层 {@link ChatMemoryStore} 为 Redis，miss/故障回填 MySQL）；为空时匿名单轮。
     * token 级片段实时经 onChunk 转发；失败/未配置时降级文案也走 onChunk，
     * 同时以返回值告知调用方（供落库记账）。</p>
     */
    public StreamingResult streamChat(Long sessionId, String message, String systemPrompt, Consumer<String> onChunk) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            String notice = "AI 服务未配置，请先设置有效的 AI_API_KEY。";
            onChunk.accept(notice);
            return new StreamingResult(null, null, notice);
        }

        ChatMemory memory = null;
        List<ChatMessage> messages;
        if (sessionId != null) {
            memory = MessageWindowChatMemory.builder()
                    .id(sessionId)
                    .maxMessages(MEMORY_WINDOW)
                    .chatMemoryStore(chatMemoryStore)
                    .alwaysKeepSystemMessageFirst(true)
                    .build();
            memory.add(SystemMessage.from(systemPrompt));
            memory.add(UserMessage.from(message));
            messages = memory.messages();
        } else {
            messages = List.of(SystemMessage.from(systemPrompt), UserMessage.from(message));
        }

        StringBuilder answer = new StringBuilder();
        AtomicReference<TokenUsage> usageRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        try {
            streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    answer.append(token);
                    onChunk.accept(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (response != null && response.metadata() != null) {
                        usageRef.set(response.metadata().tokenUsage());
                    }
                    done.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming chat failed: {}", error.getMessage());
                    errorRef.set(describeStreamError(error));
                    done.countDown();
                }
            });

            boolean finished = done.await(Math.max(chatTimeoutMs, 30000), TimeUnit.MILLISECONDS);
            if (!finished) {
                errorRef.set("AI 服务响应超时，请稍后再试。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorRef.set("AI 服务请求被中断，请稍后再试。");
        } catch (Exception e) {
            log.error("Streaming chat invocation failed", e);
            errorRef.set(describeStreamError(e));
        }

        String error = errorRef.get();
        if (error != null) {
            onChunk.accept(error);
            return new StreamingResult(null, null, error);
        }

        String content = answer.toString();
        if (content.isBlank()) {
            String notice = "AI 服务没有返回有效内容，请稍后再试。";
            onChunk.accept(notice);
            return new StreamingResult(null, null, notice);
        }

        if (memory != null) {
            // 把 AI 回答写回记忆窗口（store 内部吞 Redis 异常）
            memory.add(AiMessage.from(content));
        }
        TokenUsage usage = usageRef.get();
        Integer tokensUsed = usage == null ? null : usage.totalTokenCount();
        return new StreamingResult(content, tokensUsed, null);
    }

    private String describeStreamError(Throwable error) {
        String msg = error == null ? "" : String.valueOf(error.getMessage());
        if (msg.contains("403")) {
            return "AI 服务鉴权失败（403）。请检查 AI_API_KEY、AI_MODEL，以及 DeepSeek 账号是否已开通对应模型权限。";
        }
        return "AI 服务请求失败，请稍后再试。";
    }

    private String callAi(String prompt) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            log.warn("AI API key is not configured, skipping remote call");
            return null;
        }
        try {
            return callChatCompletions(List.of(Map.of("role", "user", "content", prompt)), maxTokens, readTimeoutMs);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("429") || msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED"))) {
                log.warn("AI API quota exhausted (429), skipping: {}", msg.length() > 100 ? msg.substring(0, 100) : msg);
            } else {
                log.error("AI API call error: {}", msg);
            }
            return null;
        }
    }

    private String callChatCompletions(List<Map<String, Object>> messages, int requestedMaxTokens, int timeoutMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages, requestedMaxTokens), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            String body = response.body();
            String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 400) {
                snippet = snippet.substring(0, 400);
            }
            log.warn("AI provider API returned status: {}, body: {}", response.statusCode(), snippet);
            throw new IllegalStateException("AI provider API returned status " + response.statusCode() + (snippet.isBlank() ? "" : (": " + snippet)));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("AI provider API returned no choices");
            return null;
        }

        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("AI provider API returned empty content");
            return null;
        }

        content = content.trim();
        int jsonStart = content.indexOf('{');
        int jsonEnd = content.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            content = content.substring(jsonStart, jsonEnd + 1);
        }
        return content;
    }

    private String buildRequestBody(List<Map<String, Object>> messages, int requestedMaxTokens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", requestedMaxTokens);
        return objectMapper.writeValueAsString(body);
    }

    private String getCached(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.warn("Redis read failed, skipping cache: {}", e.getMessage());
            return null;
        }
    }

    private void cache(String key, String value, long ttlHours) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis write failed, skipping cache: {}", e.getMessage());
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    private String buildLocalSummary(String title, String description) {
        if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
            return null;
        }

        String normalized = description == null ? "" : description
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`", " ")
                .replaceAll("#{1,6}\\s*", " ")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> parts = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            parts.add(title.trim());
        }
        if (!normalized.isBlank()) {
            int firstBreak = Math.min(normalized.length(), 80);
            parts.add(normalized.substring(0, firstBreak));
        }

        String summary = String.join("：", parts);
        summary = summary.replaceAll("\\s+", " ").trim();
        if (summary.length() > 110) {
            summary = summary.substring(0, 110);
        }
        return summary;
    }

}
