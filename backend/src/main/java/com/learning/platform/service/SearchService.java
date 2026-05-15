package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.PageResult;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ResourceMapper resourceMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public PageResult<Resource> search(String keyword, Long categoryId, String sortBy, int page, int size) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PUBLISHED");

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }
        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        }

        switch (sortBy != null ? sortBy : "relevance") {
            case "latest" -> wrapper.orderByDesc("created_at");
            case "rating" -> wrapper.orderByDesc("avg_rating");
            case "hot" -> wrapper.orderByDesc("hot_score");
            default -> wrapper.orderByDesc("hot_score");
        }

        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);

        // Record search keyword
        if (keyword != null && !keyword.isBlank()) {
            recordSearch(keyword);
        }

        return PageResult.from(result);
    }

    @SuppressWarnings("unchecked")
    public List<String> getHotSearches() {
        List<Object> hot = redisTemplate.opsForList().range("search:hot", 0, 9);
        return hot != null ? hot.stream().map(Object::toString).toList() : List.of();
    }

    private void recordSearch(String keyword) {
        redisTemplate.opsForList().leftPush("search:hot", keyword);
        redisTemplate.opsForList().trim("search:hot", 0, 99);
        redisTemplate.expire("search:hot", 7, TimeUnit.DAYS);
    }
}
