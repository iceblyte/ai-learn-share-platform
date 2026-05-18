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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Chinese stop words and common words to strip from fallback search
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "推荐", "关于", "并且", "而且", "的", "了", "是", "在", "有", "和", "与",
            "或", "但", "最", "比较", "一些", "几个", "哪些", "什么", "怎么", "如何",
            "前", "后", "个", "条", "篇", "份", "资源", "资料", "学习", "教程",
            "请", "帮我", "找", "查", "搜索", "看看", "需要", "想要", "我"
    );

    @PostMapping("/nl")
    public Result<NlSearchResult> nlSearch(@Valid @RequestBody NlSearchRequest request, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (userId != null && request.getQuery() != null) {
            searchService.recordUserSearch(userId, request.getQuery());
        }

        String parsed = aiService.parseNaturalLanguageQuery(request.getQuery());
        if (parsed == null) {
            log.warn("AI parse returned null, falling back to keyword extraction");
            List<String> extracted = extractKeywords(request.getQuery());
            PageResult<Resource> fallback = searchService.searchByKeywords(extracted.isEmpty() ? List.of(request.getQuery()) : extracted, null, null, null, "relevance", 1, 10, userId);
            Map<String, Object> intent = Map.of("keywords", extracted.isEmpty() ? List.of(request.getQuery()) : extracted, "sortBy", "relevance");
            return Result.success(new NlSearchResult(intent, fallback.getRecords(), fallback.getTotal()));
        }

        Map<String, Object> intent;
        try {
            intent = objectMapper.readValue(parsed, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON: {}", e.getMessage());
            List<String> extracted = extractKeywords(request.getQuery());
            PageResult<Resource> fallback = searchService.searchByKeywords(extracted.isEmpty() ? List.of(request.getQuery()) : extracted, null, null, null, "relevance", 1, 10, userId);
            Map<String, Object> fallbackIntent = Map.of("keywords", extracted.isEmpty() ? List.of(request.getQuery()) : extracted, "sortBy", "relevance");
            return Result.success(new NlSearchResult(fallbackIntent, fallback.getRecords(), fallback.getTotal()));
        }

        // Extract parameters from parsed intent
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) intent.get("keywords");

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

        PageResult<Resource> results = searchService.searchByKeywords(
                keywords != null && !keywords.isEmpty() ? keywords : List.of(request.getQuery()),
                categoryId, tags, minRating, sortBy, 1, limit, userId);
        return Result.success(new NlSearchResult(intent, results.getRecords(), results.getTotal()));
    }

    /**
     * Extract meaningful keywords from Chinese text by stripping stop words and punctuation.
     * Splits on common delimiters, filters stop words, returns by length descending.
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        // Split on punctuation, spaces, and common delimiters
        String[] tokens = text.split("[，。！？、；：\"\"''（）【】\\s,.!?;:\"'()\\[\\]]+");
        List<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            String t = token.trim();
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                keywords.add(t);
            }
        }
        // Sort by length descending — longer tokens are more specific
        keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keywords;
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
