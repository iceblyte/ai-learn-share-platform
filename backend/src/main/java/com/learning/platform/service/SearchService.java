package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.PageResult;
import com.learning.platform.entity.Category;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.CategoryMapper;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ResourceMapper resourceMapper;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_KEY = "search:hot";
    private static final String HISTORY_KEY_PREFIX = "search:history:";
    private static final int HISTORY_MAX_SIZE = 20;
    private static final int HISTORY_EXPIRE_DAYS = 30;

    public PageResult<Resource> search(String keyword, Long categoryId, String sortBy, int page, int size) {
        return search(keyword, categoryId, null, null, sortBy, page, size, null);
    }

    /**
     * Search by multiple keywords (OR logic) — any keyword matches title or description.
     */
    public PageResult<Resource> searchByKeywords(List<String> keywords, Long categoryId, List<String> tags,
                                                  Double minRating, String sortBy, int page, int size, Long userId) {
        if (keywords == null || keywords.isEmpty()) {
            return search(null, categoryId, tags, minRating, sortBy, page, size, userId);
        }
        if (keywords.size() == 1) {
            return search(keywords.get(0), categoryId, tags, minRating, sortBy, page, size, userId);
        }

        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PUBLISHED");

        // Match ANY keyword in title or description
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (i > 0) w.or();
                w.and(inner -> inner.like("title", kw).or().like("description", kw));
            }
        });

        if (categoryId != null) {
            List<Long> categoryIds = getAllDescendantIds(categoryId);
            categoryIds.add(categoryId);
            wrapper.in("category_id", categoryIds);
        }
        if (minRating != null && minRating > 0) {
            wrapper.ge("avg_rating", minRating);
        }
        if (tags != null && !tags.isEmpty()) {
            wrapper.inSql("id",
                    "SELECT resource_id FROM resource_tag rt JOIN tag t ON rt.tag_id = t.id " +
                    "WHERE t.name IN (" + tags.stream().map(t -> "'" + t.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(", ")) + ") " +
                    "GROUP BY resource_id HAVING COUNT(DISTINCT t.id) = " + tags.size());
        }

        switch (sortBy != null ? sortBy : "relevance") {
            case "latest" -> wrapper.orderByDesc("created_at");
            case "rating" -> wrapper.orderByDesc("avg_rating");
            case "hot" -> wrapper.orderByDesc("hot_score");
            default -> wrapper.orderByDesc("hot_score");
        }

        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);

        String primary = keywords.get(0);
        recordSearch(primary);
        if (userId != null) {
            recordUserSearch(userId, primary);
        }

        return PageResult.from(result);
    }

    public PageResult<Resource> search(String keyword, Long categoryId, List<String> tags, Double minRating,
                                       String sortBy, int page, int size, Long userId) {
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
        if (minRating != null && minRating > 0) {
            wrapper.ge("avg_rating", minRating);
        }
        if (tags != null && !tags.isEmpty()) {
            // Filter resources that have ALL specified tags
            wrapper.inSql("id",
                    "SELECT resource_id FROM resource_tag rt JOIN tag t ON rt.tag_id = t.id " +
                    "WHERE t.name IN (" + tags.stream().map(t -> "'" + t.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(", ")) + ") " +
                    "GROUP BY resource_id HAVING COUNT(DISTINCT t.id) = " + tags.size());
        }

        switch (sortBy != null ? sortBy : "relevance") {
            case "latest" -> wrapper.orderByDesc("created_at");
            case "rating" -> wrapper.orderByDesc("avg_rating");
            case "hot" -> wrapper.orderByDesc("hot_score");
            default -> wrapper.orderByDesc("hot_score");
        }

        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);

        if (keyword != null && !keyword.isBlank()) {
            recordSearch(keyword);
            if (userId != null) {
                recordUserSearch(userId, keyword);
            }
        }

        return PageResult.from(result);
    }

    @SuppressWarnings("unchecked")
    public List<String> getHotSearches() {
        try {
            List<Object> hot = redisTemplate.opsForList().range(HOT_KEY, 0, 9);
            return hot != null ? hot.stream().map(Object::toString).toList() : List.of();
        } catch (Exception e) {
            log.warn("Redis unavailable for hot searches: {}", e.getMessage());
            return List.of();
        }
    }

    private void recordSearch(String keyword) {
        try {
            redisTemplate.opsForList().leftPush(HOT_KEY, keyword);
            redisTemplate.opsForList().trim(HOT_KEY, 0, 99);
            redisTemplate.expire(HOT_KEY, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis unavailable for recording search: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getUserSearchHistory(Long userId) {
        try {
            String key = HISTORY_KEY_PREFIX + userId;
            List<Object> history = redisTemplate.opsForList().range(key, 0, HISTORY_MAX_SIZE - 1);
            return history != null ? history.stream().map(Object::toString).toList() : List.of();
        } catch (Exception e) {
            log.warn("Redis unavailable for search history: {}", e.getMessage());
            return List.of();
        }
    }

    public void recordUserSearch(Long userId, String keyword) {
        try {
            String key = HISTORY_KEY_PREFIX + userId;
            redisTemplate.opsForList().remove(key, 1, keyword);
            redisTemplate.opsForList().leftPush(key, keyword);
            redisTemplate.opsForList().trim(key, 0, HISTORY_MAX_SIZE - 1);
            redisTemplate.expire(key, HISTORY_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis unavailable for recording user search: {}", e.getMessage());
        }
    }

    public void clearUserSearchHistory(Long userId) {
        try {
            String key = HISTORY_KEY_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable for clearing search history: {}", e.getMessage());
        }
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
