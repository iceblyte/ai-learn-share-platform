package com.learning.platform.service;

import com.learning.platform.entity.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐理由解析的 token 节省策略：
 * 开关门控（关 → 本地模板，不调 LLM）+ 理由级缓存（命中不调 LLM，miss 调一次并落缓存）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceReasonTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private SystemConfigService systemConfigService;
    @Mock private AiService aiService;

    @InjectMocks
    private RecommendationService service;

    private final Resource resource = new Resource();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        resource.setId(42L);
        resource.setTitle("Java 并发编程实战");
        resource.setDescription("多线程与并发最佳实践");
        resource.setAvgRating(new java.math.BigDecimal("4.8"));
    }

    @Test
    @DisplayName("开关关闭：直接返回本地模板理由，不读缓存、不调 LLM")
    void resolveReason_skipsLlmWhenDisabled() {
        when(systemConfigService.isAiRecommendReasonEnabled()).thenReturn(false);

        String reason = service.resolveReason("Java、并发", resource);

        assertThat(reason).contains("匹配你关注的Java、并发");
        verify(valueOperations, never()).get(anyString());
        verify(aiService, never()).generateRecommendReason(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("缓存命中：返回缓存理由，不调 LLM")
    void resolveReason_hitsCache() {
        when(systemConfigService.isAiRecommendReasonEnabled()).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("兴趣高度契合的高分资源");

        String reason = service.resolveReason("Java、并发", resource);

        assertThat(reason).isEqualTo("兴趣高度契合的高分资源");
        verify(aiService, never()).generateRecommendReason(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("缓存 miss：调一次 LLM 并将结果写入 24h 缓存")
    void resolveReason_missCallsLlmAndCaches() {
        when(systemConfigService.isAiRecommendReasonEnabled()).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(aiService.generateRecommendReason(anyString(), anyString(), anyString()))
                .thenReturn("基于你的兴趣与高评分生成");

        String reason = service.resolveReason("Java、并发", resource);

        assertThat(reason).isEqualTo("基于你的兴趣与高评分生成");
        verify(valueOperations).set(anyString(), eq("基于你的兴趣与高评分生成"), eq(24L), eq(java.util.concurrent.TimeUnit.HOURS));
    }

    @Test
    @DisplayName("LLM 失败：回退本地模板理由，不写缓存")
    void resolveReason_fallsBackOnLlmFailure() {
        when(systemConfigService.isAiRecommendReasonEnabled()).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(aiService.generateRecommendReason(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout"));

        String reason = service.resolveReason("Java、并发", resource);

        assertThat(reason).isNotBlank();
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }
}
