package com.learning.platform.service;

import com.learning.platform.dto.ResourceCreateRequest;
import com.learning.platform.entity.Category;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.CategoryMapper;
import com.learning.platform.mapper.ResourceFileMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.ResourceTagMapper;
import com.learning.platform.mapper.TagMapper;
import com.learning.platform.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 审核流回归：发布动作不直接上线。
 * create 非草稿 → PENDING；update 中作者不能把未发布资源直接置为 PUBLISHED。
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock private ResourceMapper resourceMapper;
    @Mock private ResourceTagMapper resourceTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;
    @Mock private FileService fileService;
    @Mock private ResourceFileMapper resourceFileMapper;
    @Mock private AiService aiService;
    @Mock private ResourceSummaryAsyncService resourceSummaryAsyncService;

    @InjectMocks private ResourceService service;

    private ResourceCreateRequest publishRequest() {
        ResourceCreateRequest req = new ResourceCreateRequest();
        req.setTitle("测试资源");
        req.setDescription("测试描述");
        req.setResourceType("LINK");
        req.setStatus(null);
        return req;
    }

    @Test
    @DisplayName("新发布：非草稿创建后进入待审核（PENDING），不直接 PUBLISHED")
    void create_publishGoesToPending() {
        when(categoryMapper.selectById(any())).thenReturn(new Category());
        when(aiService.generateLocalSummary(anyString(), anyString())).thenReturn("摘要");
        when(userMapper.selectById(any(Long.class))).thenReturn(new User());
        when(resourceTagMapper.selectList(any())).thenReturn(List.of());
        when(resourceMapper.insert(any(Resource.class))).thenAnswer(inv -> {
            inv.getArgument(0, Resource.class).setId(100L);
            return 1;
        });

        ResourceCreateRequest req = publishRequest();
        req.setCategoryId(1L);

        Resource result = service.create(req, null, null, 9L);

        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("草稿发布：请求 PUBLISHED 但当前是 DRAFT → 强制进入待审核")
    void update_draftPublishForcedToPending() {
        Resource existing = new Resource();
        existing.setId(5L);
        existing.setPublisherId(9L);
        existing.setStatus("DRAFT");
        when(resourceMapper.selectById(5L)).thenReturn(existing, existing);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);
        when(aiService.generateLocalSummary(anyString(), anyString())).thenReturn("摘要");
        when(userMapper.selectById(any(Long.class))).thenReturn(new User());
        when(resourceTagMapper.selectList(any())).thenReturn(List.of());

        ResourceCreateRequest req = publishRequest();
        req.setStatus("PUBLISHED");

        Resource result = service.update(5L, req, null, null, 9L);

        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("编辑已发布资源：保持 PUBLISHED，不重新进入审核")
    void update_publishedEditStaysPublished() {
        Resource existing = new Resource();
        existing.setId(5L);
        existing.setPublisherId(9L);
        existing.setStatus("PUBLISHED");
        when(resourceMapper.selectById(5L)).thenReturn(existing, existing);
        when(resourceMapper.updateById(any(Resource.class))).thenReturn(1);
        when(aiService.generateLocalSummary(anyString(), anyString())).thenReturn("摘要");
        when(userMapper.selectById(any(Long.class))).thenReturn(new User());
        when(resourceTagMapper.selectList(any())).thenReturn(List.of());

        ResourceCreateRequest req = publishRequest();
        req.setStatus("PUBLISHED");

        Resource result = service.update(5L, req, null, null, 9L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }
}
