package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.common.BusinessException;
import com.learning.platform.entity.*;
import com.learning.platform.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final RatingMapper ratingMapper;
    private final ResourceMapper resourceMapper;
    private final CommentMapper commentMapper;

    @Transactional
    public Map<String, Object> toggleLike(Long userId, Long targetId, String targetType) {
        QueryWrapper<LikeRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("target_id", targetId).eq("target_type", targetType);
        LikeRecord existing = likeRecordMapper.selectOne(wrapper);

        boolean liked;
        if (existing != null) {
            likeRecordMapper.deleteById(existing.getId());
            liked = false;
        } else {
            LikeRecord record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetId(targetId);
            record.setTargetType(targetType);
            likeRecordMapper.insert(record);
            liked = true;
        }

        int likeCount = Math.toIntExact(likeRecordMapper.selectCount(
                new QueryWrapper<LikeRecord>().eq("target_id", targetId).eq("target_type", targetType)));

        // Update count on target
        if ("RESOURCE".equals(targetType)) {
            Resource resource = resourceMapper.selectById(targetId);
            if (resource != null) {
                resource.setLikeCount(likeCount);
                resourceMapper.updateById(resource);
            }
        } else if ("COMMENT".equals(targetType)) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                comment.setLikeCount(likeCount);
                commentMapper.updateById(comment);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Transactional
    public Map<String, Object> toggleFavorite(Long userId, Long resourceId) {
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("resource_id", resourceId);
        Favorite existing = favoriteMapper.selectOne(wrapper);

        boolean favorited;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            favorited = false;
        } else {
            Favorite fav = new Favorite();
            fav.setUserId(userId);
            fav.setResourceId(resourceId);
            favoriteMapper.insert(fav);
            favorited = true;
        }

        int favCount = Math.toIntExact(favoriteMapper.selectCount(
                new QueryWrapper<Favorite>().eq("resource_id", resourceId)));

        Resource resource = resourceMapper.selectById(resourceId);
        if (resource != null) {
            resource.setFavoriteCount(favCount);
            resourceMapper.updateById(resource);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favorited);
        result.put("favoriteCount", favCount);
        return result;
    }

    @Transactional
    public Map<String, Object> rate(Long userId, Long resourceId, int score) {
        QueryWrapper<Rating> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("resource_id", resourceId);
        Rating existing = ratingMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setScore(score);
            ratingMapper.updateById(existing);
        } else {
            Rating rating = new Rating();
            rating.setUserId(userId);
            rating.setResourceId(resourceId);
            rating.setScore(score);
            ratingMapper.insert(rating);
        }

        // Recalculate average
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource != null) {
            Long count = ratingMapper.selectCount(
                    new QueryWrapper<Rating>().eq("resource_id", resourceId));
            // Sum scores using stream
            java.util.List<Rating> ratings = ratingMapper.selectList(
                    new QueryWrapper<Rating>().eq("resource_id", resourceId));
            int total = ratings.stream().mapToInt(Rating::getScore).sum();
            BigDecimal avg = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

            resource.setAvgRating(avg);
            resource.setRatingCount(count.intValue());
            resourceMapper.updateById(resource);

            Map<String, Object> result = new HashMap<>();
            result.put("myRating", score);
            result.put("avgRating", avg);
            result.put("ratingCount", count);
            return result;
        }

        throw BusinessException.notFound("资源不存在");
    }

    public boolean isLiked(Long userId, Long targetId, String targetType) {
        return likeRecordMapper.selectCount(
                new QueryWrapper<LikeRecord>()
                        .eq("user_id", userId)
                        .eq("target_id", targetId)
                        .eq("target_type", targetType)) > 0;
    }

    public boolean isFavorited(Long userId, Long resourceId) {
        return favoriteMapper.selectCount(
                new QueryWrapper<Favorite>()
                        .eq("user_id", userId)
                        .eq("resource_id", resourceId)) > 0;
    }
}
