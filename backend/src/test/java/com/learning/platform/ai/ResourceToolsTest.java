package com.learning.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 两个数据库工具的执行逻辑：参数解析、缺省回退、状态过滤与 JSON 输出。
 */
class ResourceToolsTest {

    @Mock
    private ResourceMapper resourceMapper;

    private ObjectMapper objectMapper;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
    }

    private ResourceSearchTool searchTool() {
        return new ResourceSearchTool(resourceMapper, objectMapper);
    }

    private ResourceDetailTool detailTool() {
        return new ResourceDetailTool(resourceMapper, new com.learning.platform.service.ChatContextService(resourceMapper), objectMapper);
    }

    private Resource publishedResource() {
        Resource r = new Resource();
        r.setId(49L);
        r.setTitle("可汗学院");
        r.setResourceType("LINK");
        r.setStatus("PUBLISHED");
        r.setIsDeleted(0);
        r.setAiSummary("非营利免费学习平台");
        r.setDescription("非常长的正文".repeat(10));
        return r;
    }

    @Test
    @DisplayName("搜索工具：只查 PUBLISHED 且未删除的资源，输出 JSON 列表")
    void searchTool_filtersAndFormats() throws Exception {
        when(resourceMapper.selectList(any())).thenReturn(List.of(publishedResource()));

        String output = searchTool().execute("{\"keyword\":\"数学\",\"limit\":3}", ChatToolContext.NONE);

        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(output);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("resources").get(0).get("title").asText()).isEqualTo("可汗学院");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Resource>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        org.mockito.Mockito.verify(resourceMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("status").contains("is_deleted").contains("LIMIT");
    }

    @Test
    @DisplayName("搜索工具：空关键词返回参数错误文本")
    void searchTool_rejectsBlankKeyword() {
        assertThat(searchTool().execute("{}", ChatToolContext.NONE)).contains("参数错误");
    }

    @Test
    @DisplayName("详情工具：未传 resourceId 且页面无资源时给出可读错误")
    void detailTool_requiresResourceId() {
        assertThat(detailTool().execute("{}", ChatToolContext.NONE)).contains("参数错误");
    }

    @Test
    @DisplayName("详情工具：省略 resourceId 时回退查询当前页面资源")
    void detailTool_fallsBackToPageResource() {
        when(resourceMapper.selectById(49L)).thenReturn(publishedResource());
        ChatToolContext context = new ChatToolContext(1L, "RESOURCE", 49L, "勾股定理");

        String output = detailTool().execute("{}", context);

        assertThat(output).contains("可汗学院").contains("非营利免费学习平台");
        org.mockito.Mockito.verify(resourceMapper).selectById(49L);
    }

    @Test
    @DisplayName("详情工具：资源不存在/未发布时返回可读错误，模型可据此解释")
    void detailTool_reportsMissingOrUnpublished() {
        when(resourceMapper.selectById(404L)).thenReturn(null);
        Resource pending = publishedResource();
        pending.setId(50L);
        pending.setStatus("PENDING");
        when(resourceMapper.selectById(50L)).thenReturn(pending);

        assertThat(detailTool().execute("{\"resourceId\":404}", ChatToolContext.NONE)).contains("不存在");
        assertThat(detailTool().execute("{\"resourceId\":50}", ChatToolContext.NONE)).contains("未发布");
    }
}
