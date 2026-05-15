package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.entity.*;
import com.learning.platform.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ResourceMapper resourceMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final TagMapper tagMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final AiService aiService;
    private final ResourceService resourceService;

    private static final String DEFAULT_REASON = "该资源在相关领域受到好评";

    public List<Map<String, Object>> getRecommendations(Long userId, int limit) {
        // Step 1: Get user's interest tags from likes and favorites
        Set<Long> interactedResourceIds = new HashSet<>();
        Map<Long, Integer> tagWeightMap = new HashMap<>();

        // From likes
        List<LikeRecord> likes = likeRecordMapper.selectList(
                new QueryWrapper<LikeRecord>().eq("user_id", userId).eq("target_type", "RESOURCE"));
        for (LikeRecord like : likes) {
            interactedResourceIds.add(like.getTargetId());
            addTagWeights(like.getTargetId(), tagWeightMap, 3);
        }

        // From favorites
        List<Favorite> favorites = favoriteMapper.selectList(
                new QueryWrapper<Favorite>().eq("user_id", userId));
        for (Favorite fav : favorites) {
            interactedResourceIds.add(fav.getResourceId());
            addTagWeights(fav.getResourceId(), tagWeightMap, 5);
        }

        // Step 2: If no interests, return hot resources
        if (tagWeightMap.isEmpty()) {
            return getHotRecommendations(limit);
        }

        // Step 3: Get top interest tags
        List<Long> topTagIds = tagWeightMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Step 4: Find resources matching interest tags (exclude already interacted)
        Set<Long> recommendedIds = new HashSet<>();
        for (Long tagId : topTagIds) {
            List<ResourceTag> rts = resourceTagMapper.selectList(
                    new QueryWrapper<ResourceTag>().eq("tag_id", tagId));
            for (ResourceTag rt : rts) {
                if (!interactedResourceIds.contains(rt.getResourceId())) {
                    recommendedIds.add(rt.getResourceId());
                }
            }
        }

        if (recommendedIds.isEmpty()) {
            return getHotRecommendations(limit);
        }

        // Step 5: Fetch and sort by hot_score
        List<Resource> resources = resourceMapper.selectBatchIds(recommendedIds).stream()
                .filter(r -> "PUBLISHED".equals(r.getStatus()) && r.getIsDeleted() == 0)
                .sorted(Comparator.comparingInt(Resource::getHotScore).reversed())
                .limit(limit)
                .peek(resourceService::enrichResource)
                .toList();

        // Step 6: Generate reasons for top 5
        List<Map<String, Object>> results = new ArrayList<>();
        String userInterests = topTagIds.stream()
                .map(id -> { Tag t = tagMapper.selectById(id); return t != null ? t.getName() : ""; })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("、"));

        for (int i = 0; i < resources.size(); i++) {
            Resource r = resources.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", r);
            item.put("algorithm", "TAG_BASED");
            item.put("score", 1.0 - (i * 0.05));

            if (i < 5) {
                try {
                    String reason = aiService.generateRecommendReason(userInterests, r.getTitle(), r.getDescription());
                    item.put("recommendReason", reason);
                } catch (Exception e) {
                    log.warn("Failed to generate reason for resource {}: {}", r.getId(), e.getMessage());
                    item.put("recommendReason", DEFAULT_REASON);
                }
            } else {
                item.put("recommendReason", DEFAULT_REASON);
            }
            results.add(item);
        }
        return results;
    }

    public List<String> generateReasons(Long userId, List<Long> resourceIds) {
        String userInterests = "";
        try {
            Set<Long> tagIds = new HashSet<>();
            List<LikeRecord> likes = likeRecordMapper.selectList(
                    new QueryWrapper<LikeRecord>().eq("user_id", userId).eq("target_type", "RESOURCE"));
            for (LikeRecord like : likes) {
                List<ResourceTag> rts = resourceTagMapper.selectList(
                        new QueryWrapper<ResourceTag>().eq("resource_id", like.getTargetId()));
                rts.forEach(rt -> tagIds.add(rt.getTagId()));
            }
            userInterests = tagIds.stream()
                    .limit(5)
                    .map(id -> { Tag t = tagMapper.selectById(id); return t != null ? t.getName() : ""; })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("、"));
        } catch (Exception ignored) {}

        List<String> reasons = new ArrayList<>();
        for (Long resourceId : resourceIds) {
            Resource r = resourceMapper.selectById(resourceId);
            if (r == null) {
                reasons.add(DEFAULT_REASON);
                continue;
            }
            try {
                reasons.add(aiService.generateRecommendReason(userInterests, r.getTitle(), r.getDescription()));
            } catch (Exception e) {
                reasons.add(DEFAULT_REASON);
            }
        }
        return reasons;
    }

    private void addTagWeights(Long resourceId, Map<Long, Integer> tagWeightMap, int weight) {
        List<ResourceTag> rts = resourceTagMapper.selectList(
                new QueryWrapper<ResourceTag>().eq("resource_id", resourceId));
        for (ResourceTag rt : rts) {
            tagWeightMap.merge(rt.getTagId(), weight, Integer::sum);
        }
    }

    private List<Map<String, Object>> getHotRecommendations(int limit) {
        List<Resource> hot = resourceMapper.selectHotResources(limit);
        hot.forEach(resourceService::enrichResource);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < hot.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", hot.get(i));
            item.put("recommendReason", DEFAULT_REASON);
            item.put("algorithm", "HOT");
            item.put("score", 1.0 - (i * 0.05));
            results.add(item);
        }
        return results;
    }
}
