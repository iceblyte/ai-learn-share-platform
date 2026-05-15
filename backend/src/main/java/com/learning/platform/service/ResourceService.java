package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.ResourceCreateRequest;
import com.learning.platform.entity.*;
import com.learning.platform.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;

    public PageResult<Resource> list(String keyword, Long categoryId, List<String> tags,
                                     String sortBy, BigDecimal minRating, int page, int size) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PUBLISHED");

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }
        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        }
        if (minRating != null) {
            wrapper.ge("avg_rating", minRating);
        }

        // Sort
        switch (sortBy != null ? sortBy : "hot") {
            case "latest" -> wrapper.orderByDesc("created_at");
            case "rating" -> wrapper.orderByDesc("avg_rating");
            case "hot" -> wrapper.orderByDesc("hot_score");
            default -> wrapper.orderByDesc("hot_score");
        }

        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);
        // Enrich with relations
        result.getRecords().forEach(this::enrichResource);
        return PageResult.from(result);
    }

    public Resource getDetail(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        // Increment view count
        resource.setViewCount(resource.getViewCount() + 1);
        resourceMapper.updateById(resource);
        return enrichResource(resource);
    }

    @Transactional
    public Resource create(ResourceCreateRequest request, Long publisherId) {
        // Validate category
        Category category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            throw new BusinessException("分类不存在");
        }

        Resource resource = new Resource();
        resource.setTitle(request.getTitle());
        resource.setCategoryId(request.getCategoryId());
        resource.setPublisherId(publisherId);
        resource.setDescription(request.getDescription());
        resource.setResourceType(request.getResourceType());
        resource.setExternalUrl(request.getExternalUrl());
        resource.setViewCount(0);
        resource.setLikeCount(0);
        resource.setFavoriteCount(0);
        resource.setCommentCount(0);
        resource.setAvgRating(BigDecimal.ZERO);
        resource.setRatingCount(0);
        resource.setHotScore(0);
        resource.setStatus("PUBLISHED"); // Can change to PENDING if review needed
        resourceMapper.insert(resource);

        // Handle tags
        if (request.getTags() != null) {
            for (String tagName : request.getTags()) {
                Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("name", tagName));
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tag.setUsageCount(0);
                    tagMapper.insert(tag);
                }
                tag.setUsageCount(tag.getUsageCount() + 1);
                tagMapper.updateById(tag);

                ResourceTag rt = new ResourceTag();
                rt.setResourceId(resource.getId());
                rt.setTagId(tag.getId());
                resourceTagMapper.insert(rt);
            }
        }

        return enrichResource(resource);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        if (!resource.getPublisherId().equals(userId)) {
            throw BusinessException.forbidden("无权删除此资源");
        }
        resourceMapper.deleteById(id);
    }

    public List<Resource> getHot(int limit) {
        List<Resource> resources = resourceMapper.selectHotResources(limit);
        resources.forEach(this::enrichResource);
        return resources;
    }

    public List<Resource> getLatest(int limit) {
        List<Resource> resources = resourceMapper.selectLatestResources(limit);
        resources.forEach(this::enrichResource);
        return resources;
    }

    public PageResult<Resource> getByUser(Long userId, int page, int size) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("publisher_id", userId).orderByDesc("created_at");
        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::enrichResource);
        return PageResult.from(result);
    }

    private Resource enrichResource(Resource resource) {
        if (resource.getCategoryId() != null) {
            resource.setCategory(categoryMapper.selectById(resource.getCategoryId()));
        }
        if (resource.getPublisherId() != null) {
            User publisher = userMapper.selectById(resource.getPublisherId());
            if (publisher != null) {
                publisher.setPasswordHash(null);
                resource.setPublisher(publisher);
            }
        }
        // Get tags
        List<ResourceTag> rts = resourceTagMapper.selectList(
                new QueryWrapper<ResourceTag>().eq("resource_id", resource.getId()));
        if (!rts.isEmpty()) {
            List<Long> tagIds = rts.stream().map(ResourceTag::getTagId).toList();
            resource.setTags(tagMapper.selectBatchIds(tagIds));
        }
        return resource;
    }
}
