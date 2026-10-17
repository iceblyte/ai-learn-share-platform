package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.moderation.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 审查服务（双通道）：本地敏感词先行命中即违规；本地干净时 AI 审查把关；
 * AI 失败/输出异常降级放行。
 */
class ModerationServiceTest {

    @Mock
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    private SensitiveWordFilter filterWith(String... words) {
        String content = String.join("\n", words);
        ByteArrayResource resource = new ByteArrayResource(
                content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "sensitive-words.txt";
            }
        };
        return new SensitiveWordFilter(resource);
    }

    private ModerationService newService(SensitiveWordFilter filter, boolean aiEnabled) {
        return new ModerationService(filter, aiService, objectMapper, aiEnabled, 2000, 1024);
    }

    @Test
    @DisplayName("本地命中：直接判违规，不再走 AI 审查")
    void localHit_flagsImmediately() {
        ModerationService service = newService(filterWith("赌博"), true);

        ModerationService.ModerationResult result = service.review("这是关于赌博的内容");

        assertThat(result.clean()).isFalse();
        assertThat(result.fragments()).containsExactly("赌博");
        assertThat(result.channel()).isEqualTo("local");
        org.mockito.Mockito.verify(aiService, org.mockito.Mockito.never())
                .completeJson(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("本地干净 + AI 审查关闭：放行")
    void aiDisabled_localClean_passes() {
        ModerationService service = newService(filterWith("赌博"), false);

        ModerationService.ModerationResult result = service.review("正常讲解");

        assertThat(result.clean()).isTrue();
    }

    @Test
    @DisplayName("AI 审查判违规：fragments 取自模型输出")
    void aiReviewFlags_withFragments() {
        ModerationService service = newService(filterWith("赌博"), true);
        when(aiService.completeJson(anyString(), anyInt(), anyInt()))
                .thenReturn("{\"violation\":true,\"fragments\":[\"某某片段\"],\"reason\":\"涉及违禁\"}");

        ModerationService.ModerationResult result = service.review("一段可疑回答");

        assertThat(result.clean()).isFalse();
        assertThat(result.fragments()).containsExactly("某某片段");
        assertThat(result.channel()).isEqualTo("ai");
    }

    @Test
    @DisplayName("AI 审查通过：放行")
    void aiReviewClean_passes() {
        ModerationService service = newService(filterWith("赌博"), true);
        when(aiService.completeJson(anyString(), anyInt(), anyInt()))
                .thenReturn("{\"violation\":false,\"fragments\":[]}");

        assertThat(service.review("正常回答").clean()).isTrue();
    }

    @Test
    @DisplayName("AI 审查失败/输出非法：降级放行（channel=degraded）")
    void aiFailure_degradesToPass() {
        ModerationService service = newService(filterWith("赌博"), true);
        when(aiService.completeJson(anyString(), anyInt(), anyInt())).thenReturn(null);

        ModerationService.ModerationResult failed = service.review("回答一");
        assertThat(failed.clean()).isTrue();
        assertThat(failed.channel()).isEqualTo("degraded");

        when(aiService.completeJson(anyString(), anyInt(), anyInt())).thenReturn("不是 JSON 的输出");
        ModerationService.ModerationResult garbage = service.review("回答二");
        assertThat(garbage.clean()).isTrue();
        assertThat(garbage.channel()).isEqualTo("degraded");
    }

    @Test
    @DisplayName("AI 判违规但未给出片段：以全文为待改写片段")
    void aiFlagsWithoutFragments_fallsBackToFullText() {
        ModerationService service = newService(filterWith("赌博"), true);
        when(aiService.completeJson(anyString(), anyInt(), anyInt()))
                .thenReturn("{\"violation\":true,\"fragments\":[]}");

        ModerationService.ModerationResult result = service.review("问题回答");

        assertThat(result.clean()).isFalse();
        assertThat(result.fragments()).isNotEmpty();
    }
}
