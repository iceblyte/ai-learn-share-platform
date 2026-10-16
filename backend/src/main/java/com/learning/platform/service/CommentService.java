package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.CommentRequest;
import com.learning.platform.entity.Comment;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.CommentMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final ResourceMapper resourceMapper;
    private final UserMapper userMapper;

    public PageResult<Comment> getByResource(Long resourceId, int page, int size) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("resource_id", resourceId).isNull("parent_id").orderByDesc("created_at");
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::enrichWithChildren);
        return PageResult.from(result);
    }

    @Transactional
    public Comment create(Long resourceId, CommentRequest request, Long userId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        if (request.getParentId() != null) {
            // 多级嵌套：父评论必须存在且属于同一资源，防止跨资源挂载
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null || !parent.getResourceId().equals(resourceId)) {
                throw new BusinessException("要回复的评论不存在");
            }
        }

        Comment comment = new Comment();
        comment.setResourceId(resourceId);
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent());
        comment.setLikeCount(0);
        comment.setStatus("APPROVED");
        commentMapper.insert(comment);

        // Update comment count
        resource.setCommentCount(resource.getCommentCount() + 1);
        resourceMapper.updateById(resource);

        return enrichWithChildren(comment);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BusinessException.notFound("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除此评论");
        }
        commentMapper.deleteById(id);

        // Update comment count
        Resource resource = resourceMapper.selectById(comment.getResourceId());
        if (resource != null && resource.getCommentCount() > 0) {
            resource.setCommentCount(resource.getCommentCount() - 1);
            resourceMapper.updateById(resource);
        }
    }

    public List<Comment> getReplies(Long parentId) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId).orderByAsc("created_at");
        List<Comment> replies = commentMapper.selectList(wrapper);
        replies.forEach(this::enrichWithChildren);
        return replies;
    }

    /**
     * 富化单条评论（用户信息 + 递归加载子评论树）。
     *
     * <p>多级嵌套：不再限制一层楼，每条回复同样递归挂载自己的子评论。
     * 父评论只能指向已存在的同资源评论（create 里校验），因此树天然无环，
     * 递归终止由数据完整性保证。</p>
     */
    private Comment enrichWithChildren(Comment comment) {
        if (comment.getUserId() != null) {
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                user.setPasswordHash(null);
                comment.setUser(user);
            }
        }
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", comment.getId()).orderByAsc("created_at");
        List<Comment> children = commentMapper.selectList(wrapper);
        children.forEach(this::enrichWithChildren);
        comment.setReplies(children);
        return comment;
    }
}
