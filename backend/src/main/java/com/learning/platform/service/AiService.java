package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.timeout}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Generate AI summary for a resource description
     */
    public String generateSummary(String title, String description) {
        String prompt = String.format(
                "请为以下学习资源生成一段约100字的精准摘要，突出核心内容和学习价值：\n\n标题：%s\n描述：%s\n\n要求：简洁明了，突出重点，约100字。",
                title, description.length() > 2000 ? description.substring(0, 2000) : description
        );
        return callGemini(prompt);
    }

    /**
     * Parse natural language search query into structured parameters
     */
    public String parseNaturalLanguageQuery(String query) {
        String prompt = String.format(
                "请将以下自然语言搜索请求解析为JSON格式的结构化查询参数。\n\n" +
                "用户输入：\"%s\"\n\n" +
                "请输出以下JSON格式（不要输出其他内容）：\n" +
                "{\"keywords\": [\"关键词1\", \"关键词2\"], \"category\": \"分类名或null\", \"tags\": [\"标签1\"], " +
                "\"sortBy\": \"relevance/latest/hot/rating\", \"limit\": 数字, \"minRating\": 数字或null}\n\n" +
                "注意：sortBy默认为relevance，limit默认为10", query
        );
        return callGemini(prompt);
    }

    /**
     * Generate personalized recommendation reason
     */
    public String generateRecommendReason(String userInterests, String resourceTitle, String resourceDescription) {
        String prompt = String.format(
                "请为用户生成一段个性化的推荐理由（50字以内），让用户觉得这个资源很适合他。\n\n" +
                "用户兴趣：%s\n资源标题：%s\n资源简介：%s\n\n" +
                "要求：语气亲切，直接说明为什么适合该用户，50字以内。",
                userInterests, resourceTitle,
                resourceDescription.length() > 200 ? resourceDescription.substring(0, 200) : resourceDescription
        );
        return callGemini(prompt);
    }

    private String callGemini(String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            );

            String json = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(apiUrl + "?key=" + apiKey)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("AI API call failed: {}", response.code());
                    throw new BusinessException(503, "AI 服务调用失败");
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            }
        } catch (IOException e) {
            log.error("AI API call error", e);
            throw new BusinessException(503, "AI 服务不可用");
        }
    }
}
