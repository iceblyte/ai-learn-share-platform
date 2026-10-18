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

    /** key 是否已配置：未配置时 WebSearchTool 会在回答里明确提示，而不是误导性的"无结果" */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 搜索结果 + 失败原因：sources 为空且 error 非空时，error 是可直接展示给用户的诊断信息 */
    public record SearchOutcome(List<WebSource> sources, String error) {
        public static SearchOutcome ok(List<WebSource> sources) {
            return new SearchOutcome(sources, null);
        }
        public static SearchOutcome fail(String error) {
            return new SearchOutcome(List.of(), error);
        }
    }

    /** 兼容旧签名：只取来源列表 */
    public List<WebSource> search(String query) {
        return searchDetailed(query).sources();
    }

    /**
     * 联网搜索（带失败诊断）：无 key / 网络失败 / 响应异常都会在 error 里给出
     * 可读原因（含异常类名），由调用方透传给模型转述给用户——绝不静默吞掉。
     */
    public SearchOutcome searchDetailed(String query) {
        if (!isConfigured()) {
            return SearchOutcome.fail("未配置 TAVILY_API_KEY（在 application-local.yml 填入 tavily.api-key 后重启后端）");
        }
        if (query == null || query.isBlank()) {
            return SearchOutcome.ok(List.of());
        }
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "query", query,
                    "max_results", maxResults,
                    "search_depth", "basic"));
            String response = poster.post(ENDPOINT, body);
            List<WebSource> sources = parse(response);
            log.info("Tavily search ok: query='{}', results={}", query, sources.size());
            return SearchOutcome.ok(sources);
        } catch (Exception e) {
            String detail = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage() : e.getClass().getSimpleName();
            Throwable cause = e.getCause();
            String full = cause != null
                    ? e.getClass().getSimpleName() + ": " + detail
                      + "（根因 " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + "）"
                    : e.getClass().getSimpleName() + ": " + detail;
            log.warn("Tavily search failed: query='{}', {}", query, full);
            return SearchOutcome.fail(full);
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
