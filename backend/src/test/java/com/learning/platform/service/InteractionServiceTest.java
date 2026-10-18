package com.learning.platform.service;

import com.learning.platform.entity.Comment;
import com.learning.platform.entity.Favorite;
import com.learning.platform.entity.LikeRecord;
import com.learning.platform.entity.Notification;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.CommentMapper;
import com.learning.platform.mapper.FavoriteMapper;
import com.learning.platform.mapper.LikeRecordMapper;
import com.learning.platform.mapper.RatingMapper;
import com.learning.platform.mapper.ResourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 互动通知钩子：资源被点赞/收藏通知发布者、评论被点赞通知评论作者，取消不通知。
 */
@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock private LikeRecordMapper likeRecordMapper;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private RatingMapper ratingMapper;
    @Mock private ResourceMapper resourceMapper;
    @Mock private CommentMapper commentMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private InteractionService service;

    private Resource resource(long id, long publisherId) {
        Resource r = new Resource();
        r.setId(id);
        r.setPublisherId(publisherId);
        r.setLikeCount(0);
        return r;
    }

    @Test
    @DisplayName("点赞资源：新增赞时通知资源发布者，取消赞不通知")
    void toggleLike_resourceNotifies() {
        when(likeRecordMapper.selectOne(any())).thenReturn(null);
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));

        Map<String, Object> result = service.toggleLike(1L, 9L, "RESOURCE");

        assertThat(result.get("liked")).isEqualTo(true);
        verify(notificationService).notify(eq(100L), eq(Notification.TYPE_LIKE_RESOURCE),
                eq(1L), eq(9L), isNull(), eq("有人赞了你的资源"));
    }

    @Test
    @DisplayName("点赞评论：新增赞时通知评论作者")
    void toggleLike_commentNotifies() {
        when(likeRecordMapper.selectOne(any())).thenReturn(null);
        Comment comment = new Comment();
        comment.setId(5L);
        comment.setUserId(200L);
        comment.setResourceId(9L);
        when(commentMapper.selectById(5L)).thenReturn(comment);

        service.toggleLike(1L, 5L, "COMMENT");

        verify(notificationService).notify(eq(200L), eq(Notification.TYPE_LIKE_COMMENT),
                eq(1L), eq(9L), eq(5L), eq("有人赞了你的评论"));
    }

    @Test
    @DisplayName("取消点赞：不通知")
    void toggleUnlike_noNotify() {
        LikeRecord existing = new LikeRecord();
        existing.setId(3L);
        when(likeRecordMapper.selectOne(any())).thenReturn(existing);
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));

        Map<String, Object> result = service.toggleLike(1L, 9L, "RESOURCE");

        assertThat(result.get("liked")).isEqualTo(false);
        verify(notificationService, never()).notify(anyLong(), any(), anyLong(), anyLong(), anyLong(), any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("收藏资源：新增收藏通知发布者，取消不通知")
    void toggleFavorite_notifies() {
        when(favoriteMapper.selectOne(any())).thenReturn(null);
        when(favoriteMapper.selectCount(any())).thenReturn(1L);
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));

        Map<String, Object> result = service.toggleFavorite(1L, 9L);

        assertThat(result.get("favorited")).isEqualTo(true);
        verify(notificationService).notify(eq(100L), eq(Notification.TYPE_FAVORITE),
                eq(1L), eq(9L), isNull(), eq("有人收藏了你的资源"));
    }
}
