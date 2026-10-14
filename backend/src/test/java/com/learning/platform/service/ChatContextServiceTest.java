package com.learning.platform.service;

import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 页面上下文解析（扩展一）：
 * RESOURCE 上下文从库内富化（标题/摘要/正文截断），
 * 资源缺失或类型未知时降级为空上下文（行为回退到 V1）。
 */
class ChatContextServiceTest {

    @Mock
    private ResourceMapper resourceMapper;

    private ChatContextService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new ChatContextService(resourceMapper);
    }

    private AiChatRequest resourceRequest(Long id, String message, String selectedText) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        request.setPageContextType("RESOURCE");
        request.setPageContextId(id);
        request.setSelectedText(selectedText);
        return request;
    }

    private Resource resource(Long id, String title, String summary, String description) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setTitle(title);
        resource.setAiSummary(summary);
        resource.setDescription(description);
        return resource;
    }

    @Test
    @DisplayName("RESOURCE 上下文从库内富化标题、摘要与正文截断")
    void resolveResource_returnsTitleSummaryExcerpt() {
        when(resourceMapper.selectById(1L)).thenReturn(
                resource(1L, "Java并发编程实战笔记", "一份并发入门笔记", "正文内容不长"));

        ChatContext ctx = service.resolve(resourceRequest(1L, "什么是 happens-before？", null));

        assertThat(ctx.hasContext()).isTrue();
        assertThat(ctx.type()).isEqualTo("RESOURCE");
        assertThat(ctx.resourceId()).isEqualTo(1L);
        assertThat(ctx.title()).isEqualTo("Java并发编程实战笔记");
        assertThat(ctx.summary()).isEqualTo("一份并发入门笔记");
        assertThat(ctx.excerpt()).isEqualTo("正文内容不长");
    }

    @Test
    @DisplayName("资源不存在时降级为空上下文")
    void missingResource_degradesToEmpty() {
        when(resourceMapper.selectById(99L)).thenReturn(null);

        ChatContext ctx = service.resolve(resourceRequest(99L, "问题", null));

        assertThat(ctx.hasContext()).isFalse();
        assertThat(ctx.type()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("类型为空或未知时降级为空上下文（首页等页面回归 V1）")
    void unknownType_degradesToEmpty() {
        AiChatRequest noType = new AiChatRequest();
        noType.setMessage("问题");

        assertThat(service.resolve(noType).hasContext()).isFalse();

        AiChatRequest badType = resourceRequest(1L, "问题", null);
        badType.setPageContextType("SEARCH");
        assertThat(service.resolve(badType).hasContext()).isFalse();
    }

    @Test
    @DisplayName("正文超长时按问题关键词粗排段落，截断到 2000 字内")
    void longDescription_rankedByKeywordWithinBudget() {
        StringBuilder description = new StringBuilder();
        description.append("开头介绍：本文是课程讲义，包含环境搭建与安装步骤。".repeat(30));
        description.append('\n');
        description.append("核心章节：happens-before 规则定义了并发编程中的内存可见性语义。".repeat(30));
        description.append('\n');
        description.append("结尾：常见问题答疑与参考文献列表。".repeat(30));
        when(resourceMapper.selectById(1L)).thenReturn(
                resource(1L, "笔记", null, description.toString()));

        ChatContext ctx = service.resolve(resourceRequest(1L, "什么是 happens-before？", null));

        assertThat(ctx.excerpt().length()).isLessThanOrEqualTo(2000);
        assertThat(ctx.excerpt()).contains("happens-before 规则定义");
    }

    @Test
    @DisplayName("划选文本原样进入上下文")
    void selectedText_passedThrough() {
        when(resourceMapper.selectById(1L)).thenReturn(
                resource(1L, "笔记", null, "正文"));

        ChatContext ctx = service.resolve(resourceRequest(1L, "解释一下", "happens-before"));

        assertThat(ctx.selectedText()).isEqualTo("happens-before");
    }
}
