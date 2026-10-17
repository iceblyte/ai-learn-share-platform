package com.learning.platform.ai.image;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 并发图片收集器（CompletableFuture 三源并发）。
 *
 * <p><b>与正文流式并发</b>：查询词在请求时即可确定（划选文本或用户问题），
 * 由工作流在生成开始前调用 {@link #start} 点火；正文流完后
 * {@link Session#awaitJson} 收尾。等待窗口为「点火后总预算 max-total-ms」
 * 与「正文流完后尾等 timeout-ms」的较小者——wanx（15-25s）的生成时间
 * 大部分被正文流式折抵，长回答下能赶上；短回答时静默出局，先完成先用。</p>
 *
 * <p>失败纪律：任一源异常/超时仅该源出局，不阻塞、不抛出；
 * 全部失败时返回 null（不发 images 事件）。</p>
 */
@Slf4j
@Component
public class ImageCollector {

    /** 单次回答配图总数上限 */
    static final int MAX_TOTAL_IMAGES = 4;

    private final List<ImageSearchTool> activeTools;
    private final ObjectMapper objectMapper;
    private final AiImageStorage imageStorage;
    private final boolean enabled;
    private final int timeoutMs;
    private final int maxTotalMs;
    private final int maxPerSource;
    private final ExecutorService executor;

    public ImageCollector(List<ImageSearchTool> tools,
                          ObjectMapper objectMapper,
                          AiImageStorage imageStorage,
                          @Value("${ai.image.enabled:true}") boolean enabled,
                          @Value("${ai.image.timeout-ms:8000}") int timeoutMs,
                          @Value("${ai.image.max-per-source:2}") int maxPerSource) {
        this(tools, objectMapper, imageStorage, enabled, timeoutMs, 30000, maxPerSource);
    }

    ImageCollector(List<ImageSearchTool> tools,
                   ObjectMapper objectMapper,
                   AiImageStorage imageStorage,
                   boolean enabled,
                   int timeoutMs,
                   int maxTotalMs,
                   int maxPerSource) {
        this.activeTools = tools.stream().filter(ImageSearchTool::isConfigured).toList();
        this.objectMapper = objectMapper;
        this.imageStorage = imageStorage;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.maxTotalMs = maxTotalMs;
        this.maxPerSource = maxPerSource;
        this.executor = Executors.newFixedThreadPool(
                Math.max(3, Math.min(activeTools.size() * 2, 6)),
                runnable -> {
                    Thread thread = new Thread(runnable, "image-collect");
                    thread.setDaemon(true);
                    return thread;
                });
        log.info("Image collector initialized: enabled={}, activeSources={}", enabled,
                activeTools.stream().map(ImageSearchTool::source).toList());
    }

    /** 是否参与本次工作流（enabled 且至少一个源可用） */
    public boolean isActive() {
        return enabled && !activeTools.isEmpty();
    }

    /**
     * 点火并发收集；未启用、无可用源或查询词为空时返回 null。
     * 返回的 Session 由调用方在生成完成后 await。
     */
    public Session start(String query) {
        if (!isActive() || query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        List<CompletableFuture<List<AnswerImage>>> futures = activeTools.stream()
                .map(tool -> CompletableFuture.supplyAsync(() -> collectOne(tool, trimmed), executor))
                .toList();
        return new Session(futures);
    }

    private List<AnswerImage> collectOne(ImageSearchTool tool, String query) {
        try {
            List<AnswerImage> images = tool.search(query);
            if (images == null) {
                return List.of();
            }
            // 外链图落平台存储（万相生成图 24h 过期），替换永久 URL 再入库；
            // 在收集线程上执行，与正文流式并行。失败保留原 URL，见 AiImageStorage。
            return imageStorage.persist(images.stream().limit(maxPerSource).toList());
        } catch (Exception e) {
            log.warn("Image source {} failed: {}", tool.source(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return List.of();
        }
    }

    /** 一次收集会话：awaitJson 至多等待 timeout-ms，先完成先采纳 */
    public class Session {

        private final List<CompletableFuture<List<AnswerImage>>> futures;
        /** 点火时刻：wanx 等慢源在正文流式期间并行生成，总预算从这里起算 */
        private final long startedAt = System.currentTimeMillis();

        Session(List<CompletableFuture<List<AnswerImage>>> futures) {
            this.futures = futures;
        }

        /**
         * 等待收集完成，返回 JSON 数组或 null（全失败）。
         * 截止时刻 = min(点火 + max-total-ms, 现在 + timeout-ms)：
         * 正文流式耗时折抵慢源生成时间，同时尾等不超过 timeout-ms，
         * 短回答不会为图拖长响应。
         */
        public String awaitJson() {
            long deadline = Math.min(startedAt + maxTotalMs,
                    System.currentTimeMillis() + timeoutMs);
            List<AnswerImage> collected = new ArrayList<>();
            for (CompletableFuture<List<AnswerImage>> future : futures) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    collected.addAll(future.get(remaining, TimeUnit.MILLISECONDS));
                } catch (TimeoutException e) {
                    log.info("Image collection budget exhausted, remaining sources skipped");
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    // 收集器已兜底为空列表，理论不可达；防御处理
                    log.warn("Image source failed unexpectedly: {}", e.getMessage());
                }
                if (collected.size() >= MAX_TOTAL_IMAGES) {
                    break;
                }
            }
            if (collected.isEmpty()) {
                return null;
            }
            List<AnswerImage> limited = collected.size() > MAX_TOTAL_IMAGES
                    ? collected.subList(0, MAX_TOTAL_IMAGES) : collected;
            try {
                return objectMapper.writeValueAsString(limited);
            } catch (Exception e) {
                log.warn("Failed to serialize images: {}", e.getMessage());
                return null;
            }
        }
    }

    /** 解析收集结果 JSON（测试与前端契约校验用） */
    public static List<AnswerImage> parse(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<List<AnswerImage>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("非法的图片 JSON: " + e.getMessage(), e);
        }
    }
}
