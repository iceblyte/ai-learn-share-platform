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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FileService fileService;
    private final ResourceFileMapper resourceFileMapper;
    private final AiService aiService;

    public PageResult<Resource> list(String keyword, Long categoryId, List<String> tags,
                                     String sortBy, BigDecimal minRating, int page, int size) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PUBLISHED");

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }
        if (categoryId != null) {
            List<Long> categoryIds = getAllDescendantIds(categoryId);
            categoryIds.add(categoryId);
            wrapper.in("category_id", categoryIds);
        }
        if (minRating != null) {
            wrapper.ge("avg_rating", minRating);
        }
        if (tags != null && !tags.isEmpty()) {
            String tagNames = tags.stream()
                    .map(t -> "'" + t.replace("'", "''") + "'")
                    .collect(Collectors.joining(","));
            wrapper.inSql("id",
                    "SELECT resource_id FROM resource_tag rt JOIN tag t ON rt.tag_id = t.id WHERE t.name IN (" + tagNames + ")");
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
    public Resource create(ResourceCreateRequest request, MultipartFile[] files, MultipartFile coverImage, Long publisherId) {
        boolean isDraft = "DRAFT".equalsIgnoreCase(request.getStatus());

        // Validate for published resources (skip for drafts)
        if (!isDraft) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessException("标题不能为空");
            }
            if (request.getCategoryId() == null) {
                throw new BusinessException("分类不能为空");
            }
            if (request.getDescription() == null || request.getDescription().isBlank()) {
                throw new BusinessException("描述不能为空");
            }
            if (request.getResourceType() == null || request.getResourceType().isBlank()) {
                throw new BusinessException("资源类型不能为空");
            }
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryMapper.selectById(request.getCategoryId());
            if (category == null) {
                throw new BusinessException("分类不存在");
            }
        }

        Resource resource = new Resource();
        resource.setTitle(request.getTitle());
        resource.setCategoryId(request.getCategoryId());
        resource.setPublisherId(publisherId);
        resource.setDescription(request.getDescription());
        resource.setResourceType(request.getResourceType());
        resource.setExternalUrl(request.getExternalUrl());
        resource.setCoverImageUrl(request.getCoverImageUrl());
        // Handle cover image upload
        if (coverImage != null && !coverImage.isEmpty()) {
            resource.setCoverImageUrl(fileService.storeCoverImage(coverImage));
        }
        resource.setViewCount(0);
        resource.setLikeCount(0);
        resource.setFavoriteCount(0);
        resource.setCommentCount(0);
        resource.setAvgRating(BigDecimal.ZERO);
        resource.setRatingCount(0);
        resource.setHotScore(0);
        resource.setStatus(isDraft ? "DRAFT" : "PUBLISHED");
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

        // Handle file uploads
        if (files != null && files.length > 0) {
            for (MultipartFile file : files) {
                FileService.StoredFile stored = fileService.store(file);
                ResourceFile rf = new ResourceFile();
                rf.setResourceId(resource.getId());
                rf.setFileName(stored.fileName());
                rf.setFileUrl(stored.fileUrl());
                rf.setFileSize(stored.fileSize());
                rf.setFileType(stored.fileType());
                resourceFileMapper.insert(rf);
            }
        }

        // Async AI summary generation
        if (request.getDescription() != null && request.getDescription().length() > 100) {
            generateSummaryAsync(resource.getId(), resource.getTitle(), request.getDescription());
        }

        return enrichResource(resource);
    }

    @Async
    public void generateSummaryAsync(Long resourceId, String title, String description) {
        try {
            String summary = aiService.generateSummary(title, description);
            Resource resource = resourceMapper.selectById(resourceId);
            if (resource != null && summary != null) {
                resource.setAiSummary(summary);
                resourceMapper.updateById(resource);
            }
        } catch (Exception e) {
            log.error("Failed to generate AI summary for resource {}: {}", resourceId, e.getMessage());
        }
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

    @Transactional
    public Resource update(Long id, ResourceCreateRequest request, MultipartFile[] files, MultipartFile coverImage, Long userId) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        if (!resource.getPublisherId().equals(userId)) {
            throw BusinessException.forbidden("无权编辑此资源");
        }

        resource.setTitle(request.getTitle());
        resource.setCategoryId(request.getCategoryId());
        resource.setDescription(request.getDescription());
        resource.setResourceType(request.getResourceType());
        resource.setExternalUrl(request.getExternalUrl());
        if (request.getCoverImageUrl() != null) {
            resource.setCoverImageUrl(request.getCoverImageUrl());
        }
        // Handle cover image upload
        if (coverImage != null && !coverImage.isEmpty()) {
            resource.setCoverImageUrl(fileService.storeCoverImage(coverImage));
        }
        resource.setStatus("PUBLISHED");
        resourceMapper.updateById(resource);

        // Update tags: remove old, add new
        resourceTagMapper.delete(new QueryWrapper<ResourceTag>().eq("resource_id", id));
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

        // Handle new file uploads
        if (files != null && files.length > 0) {
            for (MultipartFile file : files) {
                FileService.StoredFile stored = fileService.store(file);
                ResourceFile rf = new ResourceFile();
                rf.setResourceId(resource.getId());
                rf.setFileName(stored.fileName());
                rf.setFileUrl(stored.fileUrl());
                rf.setFileSize(stored.fileSize());
                rf.setFileType(stored.fileType());
                resourceFileMapper.insert(rf);
            }
        }

        // Re-generate AI summary if description changed
        if (request.getDescription() != null && request.getDescription().length() > 100) {
            generateSummaryAsync(resource.getId(), resource.getTitle(), request.getDescription());
        }

        return enrichResource(resource);
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

    public Resource enrichResource(Resource resource) {
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

    private List<Long> getAllDescendantIds(Long categoryId) {
        List<Category> children = categoryMapper.selectList(
                new QueryWrapper<Category>().eq("parent_id", categoryId));
        List<Long> ids = new ArrayList<>();
        for (Category child : children) {
            ids.add(child.getId());
            ids.addAll(getAllDescendantIds(child.getId()));
        }
        return ids;
    }
}
