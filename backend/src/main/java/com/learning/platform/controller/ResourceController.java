package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.ResourceCreateRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public Result<PageResult<Resource>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false, defaultValue = "hot") String sortBy,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(resourceService.list(keyword, categoryId, tags, sortBy, minRating, page, size));
    }

    @GetMapping("/{id}")
    public Result<Resource> detail(@PathVariable Long id) {
        return Result.success(resourceService.getDetail(id));
    }

    @PostMapping
    public Result<Resource> create(@Valid @RequestBody ResourceCreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.created(resourceService.create(request, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        resourceService.delete(id, userId);
        return Result.success(null);
    }

    @GetMapping("/hot")
    public Result<List<Resource>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(resourceService.getHot(limit));
    }

    @GetMapping("/latest")
    public Result<List<Resource>> latest(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(resourceService.getLatest(limit));
    }
}
