package com.learning.platform.ai.image;

import com.learning.platform.service.AiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pexels 检索词规整：中文主题词必须先译成英文再搜
 * （Pexels 索引以英文为主，中文直搜命中率极低是配图不符的主因）。
 */
class PexelsImageToolTest {

    private final AiService aiService = mock(AiService.class);
    private final PexelsImageTool tool = new PexelsImageTool(
            new com.fasterxml.jackson.databind.ObjectMapper(), aiService, "test-key", 8000);

    @Test
    @DisplayName("中文查询：经 LLM 译成英文检索词")
    void chineseQuery_translatedToEnglish() {
        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn("multithreading concurrency");

        assertThat(tool.toSearchQuery("多线程")).isEqualTo("multithreading concurrency");
    }

    @Test
    @DisplayName("翻译失败/为空：回退原中文词，不出错")
    void translationFailure_fallsBackToRaw() {
        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn(null);
        assertThat(tool.toSearchQuery("多线程")).isEqualTo("多线程");

        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn("###");
        assertThat(tool.toSearchQuery("多线程")).isEqualTo("多线程");
    }

    @Test
    @DisplayName("英文查询：不调 LLM，原样透传")
    void englishQuery_passThrough() {
        assertThat(tool.toSearchQuery("Java programming")).isEqualTo("Java programming");
        org.mockito.Mockito.verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("模型输出带解释性文字：清洗为纯英文词组")
    void noisyTranslation_sanitized() {
        when(aiService.completeText(contains("数据结构"), anyInt(), anyInt())).thenReturn("1. data structure 算法。");
        assertThat(tool.toSearchQuery("数据结构")).isEqualTo("data structure");
    }
}
