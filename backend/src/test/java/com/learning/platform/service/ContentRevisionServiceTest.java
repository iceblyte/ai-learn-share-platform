package com.learning.platform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 增量改写服务：只改违规片段、保留其余内容；模型失败返回 null（调用方走重答）。
 */
class ContentRevisionServiceTest {

    @Test
    @DisplayName("改写成功：返回模型输出的完整修订文本")
    void reviseSuccess_returnsRevisedText() {
        AiService aiService = mock(AiService.class);
        when(aiService.completeText(anyString(), anyInt(), anyInt()))
                .thenReturn("这是修改后的安全回答");

        ContentRevisionService service = new ContentRevisionService(aiService, 1024);
        String revised = service.revise("这是原始回答", List.of("原始片段"));

        assertThat(revised).isEqualTo("这是修改后的安全回答");
    }

    @Test
    @DisplayName("空片段/空原文：不调用模型，直接返回 null")
    void invalidInput_skipsModelCall() {
        AiService aiService = mock(AiService.class);
        ContentRevisionService service = new ContentRevisionService(aiService, 1024);

        assertThat(service.revise("回答", List.of())).isNull();
        assertThat(service.revise("回答", null)).isNull();
        assertThat(service.revise("", List.of("片段"))).isNull();
        assertThat(service.revise(null, List.of("片段"))).isNull();
        org.mockito.Mockito.verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("模型调用失败：返回 null，不改写")
    void modelFailure_returnsNull() {
        AiService aiService = mock(AiService.class);
        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn(null);

        ContentRevisionService service = new ContentRevisionService(aiService, 1024);
        assertThat(service.revise("回答", List.of("片段"))).isNull();
    }

    @Test
    @DisplayName("提示词包含原文与全部违规片段")
    void promptContainsContentAndFragments() {
        AiService aiService = mock(AiService.class);
        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenAnswer(inv -> {
            String prompt = inv.getArgument(0);
            assertThat(prompt).contains("原始回答内容");
            assertThat(prompt).contains("片段A");
            assertThat(prompt).contains("片段B");
            return "修订结果";
        });

        ContentRevisionService service = new ContentRevisionService(aiService, 1024);
        assertThat(service.revise("原始回答内容", List.of("片段A", "片段B"))).isEqualTo("修订结果");
    }
}
