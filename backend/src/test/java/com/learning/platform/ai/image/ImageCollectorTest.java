package com.learning.platform.ai.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发图片收集器：三源并发、单源超时/异常静默跳过、
 * 每源限额、全失败不出结果。超时用短超时（200ms）保证测试快速。
 */
class ImageCollectorTest {

    private static final AnswerImage IMG_A = new AnswerImage("pexels", "https://a/1.jpg", "A");
    private static final AnswerImage IMG_B = new AnswerImage("wanx", "https://b/2.jpg", "B");

    static class FakeTool implements ImageSearchTool {
        private final String source;
        private final boolean configured;
        private final long delayMs;
        private final boolean fail;
        private final List<AnswerImage> images;

        FakeTool(String source, boolean configured, long delayMs, boolean fail, List<AnswerImage> images) {
            this.source = source;
            this.configured = configured;
            this.delayMs = delayMs;
            this.fail = fail;
            this.images = images;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<AnswerImage> search(String query) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (fail) {
                throw new IllegalStateException(source + " 调用失败");
            }
            return images;
        }
    }

    private ImageCollector collector(int timeoutMs, ImageSearchTool... tools) {
        // 持久化关闭：本测试只关注收集行为，不做下载与存储
        return new ImageCollector(List.of(tools), new ObjectMapper(),
                new AiImageStorage(Mockito.mock(FileService.class), false), true, timeoutMs, 2);
    }

    @Test
    @DisplayName("三源并发：超时源与异常源被静默跳过，正常源结果保留")
    void concurrentCollection_skipsSlowAndBrokenSources() {
        ImageCollector collector = collector(400,
                new FakeTool("fast", true, 0, false, List.of(IMG_A)),
                new FakeTool("slow", true, 1500, false, List.of(IMG_B)),
                new FakeTool("broken", true, 0, true, List.of()));

        ImageCollector.Session session = collector.start("学习");
        String json = session.awaitJson();

        assertThat(json).isNotNull();
        List<AnswerImage> parsed = ImageCollector.parse(json);
        assertThat(parsed).hasSize(1);
        // 以 URL 区分来源：fast 产出 IMG_A，slow 的 IMG_B 与 broken 均被排除
        assertThat(parsed.get(0).url()).isEqualTo("https://a/1.jpg");
    }

    @Test
    @DisplayName("全源失败/超时：返回 null，不出图片事件")
    void allSourcesFail_returnsNull() {
        ImageCollector collector = collector(300,
                new FakeTool("broken1", true, 0, true, List.of()),
                new FakeTool("slow", true, 2000, false, List.of(IMG_B)));

        ImageCollector.Session session = collector.start("学习");

        assertThat(session.awaitJson()).isNull();
    }

    @Test
    @DisplayName("未启用或无可用源：start 返回 null（节点直达下一站）")
    void inactiveOrNoTools_startReturnsNull() {
        assertThat(collector(300, new FakeTool("off", false, 0, false, List.of(IMG_A))).start("学习"))
                .isNull();
        assertThat(collector(300).start("学习")).isNull();

        ImageCollector enabled = new ImageCollector(List.of(), new ObjectMapper(),
                new AiImageStorage(Mockito.mock(FileService.class), false), false, 300, 2);
        assertThat(enabled.isActive()).isFalse();
    }

    @Test
    @DisplayName("空白查询词：start 返回 null")
    void blankQuery_startReturnsNull() {
        ImageCollector c = collector(300, new FakeTool("fast", true, 0, false, List.of(IMG_A)));
        assertThat(c.start("  ")).isNull();
        assertThat(c.start(null)).isNull();
    }

    @Test
    @DisplayName("每源限额：单源返回超过 maxPerSource 时截断")
    void perSourceLimit_enforced() {
        ImageCollector collector = new ImageCollector(
                List.of(new FakeTool("pexels", true, 0, false,
                        List.of(IMG_A, IMG_A, IMG_A, IMG_A))),
                new ObjectMapper(),
                new AiImageStorage(Mockito.mock(FileService.class), false), true, 1000, 2);

        String json = collector.start("学习").awaitJson();

        assertThat(ImageCollector.parse(json)).hasSize(2);
    }

    @Test
    @DisplayName(" isActive 由 enabled 与可用源共同决定")
    void isActive_combinesFlagAndTools() {
        assertThat(collector(300, new FakeTool("on", true, 0, false, List.of())).isActive()).isTrue();

        ImageCollector disabled = new ImageCollector(
                List.of(new FakeTool("on", true, 0, false, List.of())),
                new ObjectMapper(),
                new AiImageStorage(Mockito.mock(FileService.class), false), false, 300, 2);
        assertThat(disabled.isActive()).isFalse();
    }
}
