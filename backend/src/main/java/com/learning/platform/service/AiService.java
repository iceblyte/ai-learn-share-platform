package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    public AiService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
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
        if (result != null) cache(cacheKey, result, SUMMARY_TTL_HOURS);
        return result;
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

    private String callDashscope(String prompt) {
        try {
            String url = baseUrl + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("DashScope API returned status: {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                log.warn("DashScope API returned no choices");
                return null;
            }

            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                log.warn("DashScope API returned empty content");
                return null;
            }
            // Strip markdown code block / backtick wrapper — extract JSON between first { and last }
            content = content.trim();
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                content = content.substring(jsonStart, jsonEnd + 1);
            }
            return content;
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
}
