package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.entity.*;
import com.learning.platform.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RECOMMEND_CACHE_PREFIX = "recommend:user:";
    private static final long RECOMMEND_CACHE_TTL_MINUTES = 30;

    private static final String DEFAULT_REASON = "该资源在相关领域受到好评";
    private static final int CF_MAX_SIMILAR_USERS = 20;
    private static final int CF_MAX_CANDIDATES = 30;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecommendations(Long userId, int limit) {
        // Check cache first
        String cacheKey = RECOMMEND_CACHE_PREFIX + userId + ":" + limit;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                return (List<Map<String, Object>>) cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for recommendations: {}", e.getMessage());
        }

        // Step 1: Get user's interaction history
        Set<Long> interactedResourceIds = new HashSet<>();
        Map<Long, Integer> tagWeightMap = new HashMap<>();

        List<LikeRecord> likes = likeRecordMapper.selectList(
                new QueryWrapper<LikeRecord>().eq("user_id", userId).eq("target_type", "RESOURCE"));
        for (LikeRecord like : likes) {
            interactedResourceIds.add(like.getTargetId());
            addTagWeights(like.getTargetId(), tagWeightMap, 3);
        }

        List<Favorite> favorites = favoriteMapper.selectList(
                new QueryWrapper<Favorite>().eq("user_id", userId));
        for (Favorite fav : favorites) {
            interactedResourceIds.add(fav.getResourceId());
            addTagWeights(fav.getResourceId(), tagWeightMap, 5);
        }

        if (interactedResourceIds.isEmpty()) {
            return getHotRecommendations(limit);
        }

        // Step 2: Tag-based recommendations
        Map<Long, Double> tagScores = new HashMap<>();
        if (!tagWeightMap.isEmpty()) {
            List<Long> topTagIds = tagWeightMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();

            for (Long tagId : topTagIds) {
                List<ResourceTag> rts = resourceTagMapper.selectList(
                        new QueryWrapper<ResourceTag>().eq("tag_id", tagId));
                for (ResourceTag rt : rts) {
                    if (!interactedResourceIds.contains(rt.getResourceId())) {
                        tagScores.merge(rt.getResourceId(), tagWeightMap.getOrDefault(tagId, 1).doubleValue(), Double::sum);
                    }
                }
            }
        }

        // Step 3: Collaborative filtering recommendations
        Map<Long, Double> cfScores = collaborativeFilter(userId, interactedResourceIds);

        // Step 4: Merge scores (tag:weight=0.6, cf:weight=0.4)
        Map<Long, Double> mergedScores = new HashMap<>();
        double tagMax = tagScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double cfMax = cfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        Set<Long> allCandidates = new HashSet<>();
        allCandidates.addAll(tagScores.keySet());
        allCandidates.addAll(cfScores.keySet());

        for (Long resourceId : allCandidates) {
            double tagNorm = tagScores.getOrDefault(resourceId, 0.0) / tagMax;
            double cfNorm = cfScores.getOrDefault(resourceId, 0.0) / cfMax;
            mergedScores.put(resourceId, tagNorm * 0.6 + cfNorm * 0.4);
        }

        if (mergedScores.isEmpty()) {
            return getHotRecommendations(limit);
        }

        // Step 5: Sort by merged score, take top N
        List<Long> sortedIds = mergedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        List<Resource> resources = sortedIds.stream()
                .map(resourceMapper::selectById)
                .filter(r -> r != null && "PUBLISHED".equals(r.getStatus()) && r.getIsDeleted() == 0)
                .peek(resourceService::enrichResource)
                .toList();

        // Step 6: Generate reasons
        String userInterests = tagWeightMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> { Tag t = tagMapper.selectById(e.getKey()); return t != null ? t.getName() : ""; })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("、"));

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource r = resources.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", r);
            item.put("algorithm", mergedScores.containsKey(r.getId()) ? "HYBRID" : "HOT");
            item.put("score", mergedScores.getOrDefault(r.getId(), 0.0));

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

        // Cache the results
        try {
            redisTemplate.opsForValue().set(cacheKey, results, RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis write failed for recommendations: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Item-based collaborative filtering:
     * Find users who interacted with the same resources, then recommend what they also liked.
     */
    private Map<Long, Double> collaborativeFilter(Long userId, Set<Long> interactedResourceIds) {
        Map<Long, Double> scores = new HashMap<>();
        if (interactedResourceIds.isEmpty()) return scores;

        // Find similar users (users who liked/favorited the same resources)
        Map<Long, Integer> similarUserCounts = new HashMap<>();
        for (Long resourceId : interactedResourceIds) {
            List<LikeRecord> likers = likeRecordMapper.selectList(
                    new QueryWrapper<LikeRecord>().eq("target_id", resourceId).eq("target_type", "RESOURCE"));
            for (LikeRecord lr : likers) {
                if (!lr.getUserId().equals(userId)) {
                    similarUserCounts.merge(lr.getUserId(), 1, Integer::sum);
                }
            }
            List<Favorite> favorers = favoriteMapper.selectList(
                    new QueryWrapper<Favorite>().eq("resource_id", resourceId));
            for (Favorite f : favorers) {
                if (!f.getUserId().equals(userId)) {
                    similarUserCounts.merge(f.getUserId(), 1, Integer::sum);
                }
            }
        }

        // Take top N similar users
        List<Long> similarUsers = similarUserCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(CF_MAX_SIMILAR_USERS)
                .map(Map.Entry::getKey)
                .toList();

        if (similarUsers.isEmpty()) return scores;

        // Collect resources from similar users
        Map<Long, Integer> resourceVotes = new HashMap<>();
        for (Long similarUserId : similarUsers) {
            int similarity = similarUserCounts.getOrDefault(similarUserId, 1);

            List<LikeRecord> userLikes = likeRecordMapper.selectList(
                    new QueryWrapper<LikeRecord>().eq("user_id", similarUserId).eq("target_type", "RESOURCE"));
            for (LikeRecord lr : userLikes) {
                if (!interactedResourceIds.contains(lr.getTargetId())) {
                    resourceVotes.merge(lr.getTargetId(), similarity, Integer::sum);
                }
            }

            List<Favorite> userFavs = favoriteMapper.selectList(
                    new QueryWrapper<Favorite>().eq("user_id", similarUserId));
            for (Favorite f : userFavs) {
                if (!interactedResourceIds.contains(f.getResourceId())) {
                    resourceVotes.merge(f.getResourceId(), similarity, Integer::sum);
                }
            }
        }

        // Normalize to scores
        resourceVotes.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(CF_MAX_CANDIDATES)
                .forEach(e -> scores.put(e.getKey(), (double) e.getValue()));

        return scores;
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
