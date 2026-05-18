package com.learning.platform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.NlSearchRequest;
import com.learning.platform.dto.NlSearchResult;
import com.learning.platform.entity.Category;
import com.learning.platform.entity.Resource;
import com.learning.platform.service.AiService;
import com.learning.platform.service.CategoryService;
import com.learning.platform.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final AiService aiService;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<PageResult<Resource>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false, defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return Result.success(searchService.search(keyword, categoryId, tags, minRating, sortBy, page, size, userId));
    }

    @PostMapping("/nl")
    public Result<NlSearchResult> nlSearch(@Valid @RequestBody NlSearchRequest request, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (userId != null && request.getQuery() != null) {
            searchService.recordUserSearch(userId, request.getQuery());
        }

        String parsed;
        try {
            parsed = aiService.parseNaturalLanguageQuery(request.getQuery());
        } catch (Exception e) {
            log.warn("AI parse failed, falling back to keyword search: {}", e.getMessage());
            PageResult<Resource> fallback = searchService.search(request.getQuery(), null, null, null, "relevance", 1, 10, userId);
            Map<String, Object> intent = Map.of("keywords", List.of(request.getQuery()), "sortBy", "relevance");
            return Result.success(new NlSearchResult(intent, fallback.getRecords(), fallback.getTotal()));
        }

        Map<String, Object> intent;
        try {
            intent = objectMapper.readValue(parsed, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON: {}", e.getMessage());
            PageResult<Resource> fallback = searchService.search(request.getQuery(), null, null, null, "relevance", 1, 10, userId);
            Map<String, Object> fallbackIntent = Map.of("keywords", List.of(request.getQuery()), "sortBy", "relevance");
            return Result.success(new NlSearchResult(fallbackIntent, fallback.getRecords(), fallback.getTotal()));
        }

        // Extract parameters from parsed intent
        String keyword = null;
        List<String> keywords = (List<String>) intent.get("keywords");
        if (keywords != null && !keywords.isEmpty()) {
            // Use the most specific keyword (longest one) for better matching
            keyword = keywords.stream()
                    .max(java.util.Comparator.comparingInt(String::length))
                    .orElse(keywords.get(0));
        }

        Long categoryId = null;
        String categoryName = (String) intent.get("category");
        if (categoryName != null && !categoryName.equals("null")) {
            try {
                List<Category> tree = categoryService.getTree();
                categoryId = findCategoryId(tree, categoryName);
            } catch (Exception ignored) {}
        }

        // Extract tags from parsed intent
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) intent.get("tags");

        // Extract minRating
        Double minRating = null;
        Object minRatingObj = intent.get("minRating");
        if (minRatingObj instanceof Number) {
            minRating = ((Number) minRatingObj).doubleValue();
        }

        String sortBy = (String) intent.getOrDefault("sortBy", "relevance");
        int limit = intent.get("limit") instanceof Number ? ((Number) intent.get("limit")).intValue() : 10;

        PageResult<Resource> results = searchService.search(keyword, categoryId, tags, minRating, sortBy, 1, limit, userId);
        return Result.success(new NlSearchResult(intent, results.getRecords(), results.getTotal()));
    }

    @GetMapping("/hot")
    public Result<List<String>> hotSearches() {
        return Result.success(searchService.getHotSearches());
    }

    @GetMapping("/history")
    public Result<List<String>> getHistory(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(searchService.getUserSearchHistory(userId));
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        searchService.clearUserSearchHistory(userId);
        return Result.success(null);
    }

    private Long findCategoryId(List<Category> categories, String name) {
        for (Category cat : categories) {
            if (cat.getName().contains(name) || name.contains(cat.getName())) {
                return cat.getId();
            }
            if (cat.getChildren() != null) {
                Long found = findCategoryId(cat.getChildren(), name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
