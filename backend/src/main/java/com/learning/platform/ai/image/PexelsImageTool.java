package com.learning.platform.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 生图工具一：Pexels 真实图片搜索。
 *
 * <p>API（官方文档核对）：GET https://api.pexels.com/v1/search，
 * Authorization 头携带 key（非 Bearer），photos[].src.large 取图。</p>
 */
@Slf4j
@Component
public class PexelsImageTool implements ImageSearchTool {

    static final String API_URL = "https://api.pexels.com/v1/search";

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PexelsImageTool(ObjectMapper objectMapper,
                           @Value("${ai.image.pexels.api-key:}") String apiKey,
                           @Value("${ai.image.timeout-ms:8000}") int timeoutMs) {
        this.objectMapper = objectMapper;
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
            String url = API_URL + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
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
}
