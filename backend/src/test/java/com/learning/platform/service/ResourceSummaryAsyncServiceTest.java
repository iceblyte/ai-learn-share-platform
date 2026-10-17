package com.learning.platform.service;

import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步摘要回写回归：refreshSummary 只允许更新 ai_summary 单列。
 * 曾发生的事故：异步任务 selectById 读到过期行快照后整行 updateById，
 * 把刚发布（PENDING）的资源静默回退成 DRAFT。
 */
@ExtendWith(MockitoExtension.class)
class ResourceSummaryAsyncServiceTest {

    @Mock private AiService aiService;
    @Mock private ResourceMapper resourceMapper;
    @Mock private SystemConfigService systemConfigService;

    @InjectMocks private ResourceSummaryAsyncService service;

    @Test
    @DisplayName("摘要回写：仅按 id 更新 ai_summary 单列，不读整行、不整行 updateById")
    void refreshSummary_updatesOnlySummaryColumn() {
        when(systemConfigService.isAiSummaryAutoEnabled()).thenReturn(true);
        when(aiService.generateSummary(anyString(), anyString())).thenReturn("生成的摘要");

        service.refreshSummary(51L, "标题", "描述");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Resource>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(resourceMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        // 更新内容只包含 ai_summary 一列
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("ai_summary");
        assertThat(sqlSet).doesNotContain("status");
        // 不允许再走 selectById / updateById 整行路径
        verify(resourceMapper, never()).selectById(any(Long.class));
        verify(resourceMapper, never()).updateById(any(Resource.class));
    }

    @Test
    @DisplayName("摘要为空时不执行任何更新")
    void refreshSummary_skipsWhenSummaryBlank() {
        when(systemConfigService.isAiSummaryAutoEnabled()).thenReturn(true);
        when(aiService.generateSummary(anyString(), anyString())).thenReturn("  ");

        service.refreshSummary(51L, "标题", "描述");

        verify(resourceMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("管理员关闭自动摘要：不调 LLM、不写库（节省 token）")
    void refreshSummary_skipsWhenDisabled() {
        when(systemConfigService.isAiSummaryAutoEnabled()).thenReturn(false);

        service.refreshSummary(51L, "标题", "描述");

        verify(aiService, never()).generateSummary(anyString(), anyString());
        verify(resourceMapper, never()).update(any(), any());
    }
}
