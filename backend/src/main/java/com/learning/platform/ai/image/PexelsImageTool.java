package com.learning.platform.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 生图工具一：Pexels 真实图片搜索。
 *
 * <p>API（官方文档核对）：GET https://api.pexels.com/v1/search，
 * Authorization 头携带 key（非 Bearer），photos[].src.large 取图。</p>
 *
 * <p><b>中文相关性修复</b>：Pexels 图库与检索索引以英文为主，中文词
 * （如"多线程"）命中率极低、常返回无关图片。中文查询先经 LLM 译成
 * 英文关键词再搜索（收集线程上执行，与正文流式并行；失败/超时回退原词）。</p>
 */
@Slf4j
@Component
public class PexelsImageTool implements ImageSearchTool {

    static final String API_URL = "https://api.pexels.com/v1/search";
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final AiService aiService;
    private final HttpClient httpClient;

    public PexelsImageTool(ObjectMapper objectMapper,
                           AiService aiService,
                           @Value("${ai.image.pexels.api-key:}") String apiKey,
                           @Value("${ai.image.timeout-ms:8000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.aiService = aiService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
    }

    @Override
    public String source() {
        return "pexels";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public List<AnswerImage> search(String query) {
        try {
            String searchQuery = toSearchQuery(query);
            String url = API_URL + "?query=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8)
                    + "&per_page=4&orientation=landscape";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(7000))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Pexels API returned status {}", response.statusCode());
                return List.of();
            }
            JsonNode photos = objectMapper.readTree(response.body()).path("photos");
            List<AnswerImage> images = new ArrayList<>();
            for (JsonNode photo : photos) {
                String imageUrl = photo.path("src").path("large").asText(null);
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                String alt = photo.path("alt").asText("");
                images.add(new AnswerImage(source(), imageUrl,
                        alt.isBlank() ? "Pexels 图片" : alt));
            }
            return images;
        } catch (Exception e) {
            throw new IllegalStateException("Pexels 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检索词规整：含中文时先经 LLM 译成英文关键词（收集线程并行执行，
     * 4s 超时兜底）；翻译失败/为空回退原词。英文词只保留字母数字空格，
     * 防止模型输出解释性文字污染 URL。
     */
    String toSearchQuery(String query) {
        if (query == null || !CJK.matcher(query).find()) {
            return query;
        }
        try {
            String en = aiService.completeText(
                    "把下面的中文主题翻译成适合图片素材库检索的英文关键词（1-3个单词）。"
                            + "只输出英文词组本身，不要任何标点、编号或解释：\n" + query,
                    24, 4000);
            if (en == null) {
                return query;
            }
            en = en.replaceAll("[^a-zA-Z0-9 ]", " ").trim().replaceAll("\\s+", " ");
            // 模型常输出"1. xxx"式编号，剥掉前导序号
            en = en.replaceFirst("^\\d+\\s*", "").trim();
            if (en.isEmpty() || en.length() > 60) {
                return query;
            }
            log.info("Pexels query translated: '{}' -> '{}'", query, en);
            return en;
        } catch (Exception e) {
            log.warn("Pexels query translation failed, fallback to raw: {}", e.getMessage());
            return query;
        }
    }
}
