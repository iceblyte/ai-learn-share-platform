package com.learning.platform.ai.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地敏感词过滤器：词表加载（注释/空行/大小写）、命中判定、空文本防御。
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter(String content) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(
                content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "sensitive-words.txt";
            }
        };
        return new SensitiveWordFilter(resource);
    }

    @Test
    @DisplayName("词表加载：跳过注释行与空行，保留词项")
    void loadsWords_skippingCommentsAndBlanks() throws IOException {
        SensitiveWordFilter filter = filter("""
                # 注释行
                赌博

                  毒品  """);
        assertThat(filter.findViolations("他沉迷赌博")).containsExactly("赌博");
        assertThat(filter.findViolations("正常学习内容")).isEmpty();
    }

    @Test
    @DisplayName("命中判定：大小写不敏感，多命中全部返回")
    void matches_caseInsensitive_multipleHits() throws IOException {
        SensitiveWordFilter filter = filter("gambling\nBomb");
        assertThat(filter.findViolations("Do not mention GAMBLING here"))
                .containsExactly("gambling");
        assertThat(filter.findViolations("gambling and BOMB"))
                .containsExactlyInAnyOrder("gambling", "Bomb");
    }

    @Test
    @DisplayName("空文本/空词表：不命中，不抛异常")
    void blankTextOrEmptyWords_noViolation() throws IOException {
        SensitiveWordFilter filter = filter("# 只有注释");
        assertThat(filter.findViolations("任何内容")).isEmpty();
        assertThat(filter.findViolations("")).isEmpty();
        assertThat(filter.findViolations(null)).isEmpty();
    }
}
