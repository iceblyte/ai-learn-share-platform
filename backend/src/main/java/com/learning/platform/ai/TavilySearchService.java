package com.learning.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tavily 联网搜索客户端（HTTP 直调，无 SDK 依赖）。
 *
 * <p>未配置 {@code tavily.api-key}（env TAVILY_API_KEY）时搜索直接返回空列表，
 * 聊天链路据此走"无联网来源"的降级回答；请求失败同样吞异常返回空列表，
 * 绝不中断聊天。</p>
 */
@Slf4j
@Component
public class TavilySearchService {

    /** HTTP 发送接缝：生产用 JDK HttpClient，测试注入桩 */
    public interface HttpPoster {
        String post(String url, String jsonBody) throws Exception;
    }

    private static final String ENDPOINT = "https://api.tavily.com/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpPoster poster;
    private final String apiKey;
    private final int maxResults;

    public TavilySearchService(ObjectMapper objectMapper,
                               HttpPoster poster,
                               @Value("${tavily.api-key:}") String apiKey,
                               @Value("${tavily.max-results:5}") int maxResults) {
        this.objectMapper = objectMapper;
        this.poster = poster;
        this.apiKey = apiKey;
        this.maxResults = Math.max(1, Math.min(maxResults, 10));
    }

    /** 联网搜索：无 key / 失败 / 空结果一律返回空列表（调用方按"无来源"降级） */
    public List<WebSource> search(String query) {
        if (apiKey == null || apiKey.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "query", query,
                    "max_results", maxResults,
                    "search_depth", "basic"));
            String response = poster.post(ENDPOINT, body);
            return parse(response);
        } catch (Exception e) {
            log.warn("Tavily search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSource> parse(String responseJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);
            com.fasterxml.jackson.databind.JsonNode results = root.path("results");
            List<WebSource> sources = new ArrayList<>();
            if (results.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : results) {
                    String url = item.path("url").asText("");
                    if (url.isBlank()) {
                        continue;
                    }
                    sources.add(new WebSource(
                            item.path("title").asText(url),
                            url,
                            item.path("content").asText("")));
                }
            }
            return sources;
        } catch (Exception e) {
            log.warn("Tavily response parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 生产 HttpPoster：JDK HttpClient，Bearer 认证 */
    @Slf4j
    @org.springframework.stereotype.Component
    static class JdkHttpPoster implements HttpPoster {

        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        @Override
        public String post(String url, String jsonBody) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Tavily HTTP " + response.statusCode());
            }
            return response.body();
        }
    }
}
