package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class AiService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
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

    @Value("${ai.chat.retries:2}")
    private int chatRetries;

    @Value("${ai.chat.retry-delay-ms:1500}")
    private long chatRetryDelayMs;

    public AiService(RedisTemplate<String, Object> redisTemplate,
                     ObjectMapper objectMapper,
                     @Value("${ai.client.connect-timeout-ms:3000}") int connectTimeoutMs,
                     @Value("${ai.client.read-timeout-ms:8000}") int readTimeoutMs) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
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
        String result = callDashscope(prompt);
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
        String result = callDashscope(prompt);
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
        String result = callDashscope(prompt);
        if (result != null) cache(cacheKey, result, REASON_TTL_HOURS);
        return result;
    }

    public void streamChat(String message, String route, String pageTitle, Consumer<String> onChunk) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            onChunk.accept("AI 服务未配置，请先设置有效的 AI_API_KEY。");
            return;
        }

        String systemPrompt = buildChatSystemPrompt(route, pageTitle);
        Exception lastError = null;

        for (int attempt = 1; attempt <= Math.max(chatRetries, 1); attempt++) {
            try {
                String answer = callDashscopeMessages(List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", message)
                ), Math.min(Math.max(maxTokens, 384), 512), Math.max(chatTimeoutMs, 30000));

                if (answer != null && !answer.isBlank()) {
                    for (String chunk : splitForStreaming(answer, 24)) {
                        onChunk.accept(chunk);
                    }
                    return;
                }
            } catch (Exception e) {
                lastError = e;
                log.error("AI stream chat attempt {} failed: {}", attempt, e.getMessage());
            }

            if (attempt < Math.max(chatRetries, 1)) {
                try {
                    Thread.sleep(chatRetryDelayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastError != null) {
            String msg = lastError.getMessage();
            if (msg != null && msg.contains("403")) {
                onChunk.accept("AI 服务鉴权失败（403）。请检查 AI_API_KEY、AI_MODEL，以及 DashScope 账号是否已开通对应模型权限。");
                return;
            }
            onChunk.accept("AI 服务请求失败，已进行重试，但仍未拿到模型回复。请稍后再试。");
            return;
        }
        onChunk.accept("AI 服务没有返回有效内容，请稍后再试。");
    }

    private String callDashscope(String prompt) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            log.warn("AI API key is not configured, skipping remote call");
            return null;
        }
        try {
            return callDashscopeMessages(List.of(Map.of("role", "user", "content", prompt)), maxTokens, readTimeoutMs);
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

    private String callDashscopeMessages(List<Map<String, Object>> messages, int requestedMaxTokens, int timeoutMs) throws Exception {
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
            log.warn("DashScope API returned status: {}, body: {}", response.statusCode(), snippet);
            throw new IllegalStateException("DashScope API returned status " + response.statusCode() + (snippet.isBlank() ? "" : (": " + snippet)));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("DashScope API returned no choices");
            return null;
        }

        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("DashScope API returned empty content");
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

    private List<String> splitForStreaming(String text, int chunkSize) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        for (int i = 0; i < text.length(); i += chunkSize) {
            parts.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return parts;
    }

    private String buildChatSystemPrompt(String route, String pageTitle) {
        String currentRoute = route == null || route.isBlank() ? "/" : route;
        String currentPageTitle = pageTitle == null || pageTitle.isBlank() ? "AI学习平台" : pageTitle;
        return """
                你是 AI 学习平台内的学习助手。回答要直接、清楚、简洁，优先帮助用户理解学习资源、学习路径、技术概念和平台内功能。
                如果用户的问题与当前页面有关，可以参考页面上下文。
                当前页面标题：%s
                当前页面路由：%s
                不要输出 markdown 标题，不要使用过度客套。
                """.formatted(currentPageTitle, currentRoute);
    }

}
