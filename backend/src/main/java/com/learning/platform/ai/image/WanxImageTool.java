package com.learning.platform.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 生图工具三：通义万相（阿里 DashScope 文生图）。
 *
 * <p>API（官方文档核对）：仅异步任务模式——POST image-synthesis
 * （{@code X-DashScope-Async: enable}，size 为 {@code 1024*1024}，星号非 x）
 * 拿 task_id，轮询 GET /api/v1/tasks/{task_id} 至 SUCCEEDED 取
 * {@code output.results[0].url}。生成实测 15-25s：轮询预算拉长到 ~24s，
 * 配合收集器从点火时刻计的总预算（正文流式时间可折抵），生成期间
 * 与正文流式并行，先完成先用，赶不上静默出局，绝不阻塞 done。</p>
 */
@Slf4j
@Component
public class WanxImageTool implements ImageSearchTool {

    static final String SUBMIT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    static final String TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    /** 轮询节奏：8 次 × 3s ≈ 24s，覆盖生成实测 15-25s；收集总预算兜底 */
    static final int POLL_ATTEMPTS = 8;
    static final long POLL_INTERVAL_MS = 3000;

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WanxImageTool(ObjectMapper objectMapper,
                         @Value("${ai.image.wanx.api-key:}") String apiKey,
                         @Value("${ai.image.wanx.model:wanx2.1-t2i-turbo}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();
    }

    @Override
    public String source() {
        return "wanx";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public List<AnswerImage> search(String query) {
        try {
            String taskId = submitTask(query);
            if (taskId == null) {
                return List.of();
            }
            for (int i = 0; i < POLL_ATTEMPTS; i++) {
                Thread.sleep(POLL_INTERVAL_MS);
                String url = pollTask(taskId);
                if (url != null) {
                    return List.of(new AnswerImage(source(), url, query));
                }
            }
            log.info("Wanx task {} not finished within poll budget, skipped", taskId);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            throw new IllegalStateException("通义万相调用失败: " + e.getMessage(), e);
        }
    }

    /** 提交异步任务，返回 task_id；失败返回 null */
    private String submitTask(String query) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "model", model,
                "input", java.util.Map.of("prompt",
                        "简洁明快的扁平风格学习主题插画：" + query),
                "parameters", java.util.Map.of("size", "1024*1024", "n", 1)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUBMIT_URL))
                .timeout(Duration.ofMillis(7000))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("Wanx submit returned status {}: {}", response.statusCode(),
                    snippet(response.body()));
            return null;
        }
        JsonNode output = objectMapper.readTree(response.body()).path("output");
        String taskId = output.path("task_id").asText(null);
        if (taskId == null || taskId.isBlank()) {
            log.warn("Wanx submit returned no task_id: {}", snippet(response.body()));
        }
        return taskId;
    }

    /** 查询任务：SUCCEEDED 返回图片 URL；FAILED/未完成返回 null */
    private String pollTask(String taskId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TASK_URL + taskId))
                .timeout(Duration.ofMillis(5000))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode output = objectMapper.readTree(response.body()).path("output");
        String status = output.path("task_status").asText("");
        if ("SUCCEEDED".equals(status)) {
            return output.path("results").path(0).path("url").asText(null);
        }
        if ("FAILED".equals(status) || "CANCELED".equals(status)) {
            log.warn("Wanx task {} ended with status {}", taskId, status);
        }
        return null;
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
