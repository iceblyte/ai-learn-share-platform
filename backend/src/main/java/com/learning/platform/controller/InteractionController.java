package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.dto.CommentRequest;
import com.learning.platform.dto.RatingRequest;
import com.learning.platform.entity.Comment;
import com.learning.platform.service.CommentService;
import com.learning.platform.service.InteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;
    private final CommentService commentService;

    @GetMapping("/resources/{id}/interactions")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getInteractions(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("liked", interactionService.isLiked(userId, id, "RESOURCE"));
        result.put("favorited", interactionService.isFavorited(userId, id));
        return Result.success(result);
    }

    @PostMapping("/resources/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> likeResource(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(interactionService.toggleLike(userId, id, "RESOURCE"));
    }

    @PostMapping("/resources/{id}/favorite")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> favoriteResource(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(interactionService.toggleFavorite(userId, id));
    }

    @PostMapping("/resources/{id}/rating")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> rateResource(@PathVariable Long id,
                                                     @Valid @RequestBody RatingRequest request,
                                                     Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(interactionService.rate(userId, id, request.getScore()));
    }

    @GetMapping("/resources/{id}/comments")
    public Result<?> getComments(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(commentService.getByResource(id, page, size));
    }

    @PostMapping("/resources/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<Comment> createComment(@PathVariable Long id,
                                          @Valid @RequestBody CommentRequest request,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.created(commentService.create(id, request, userId));
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteComment(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        commentService.delete(id, userId);
        return Result.success(null);
    }

    @PostMapping("/comments/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> likeComment(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(interactionService.toggleLike(userId, id, "COMMENT"));
    }
}
