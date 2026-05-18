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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "请", "帮我", "找", "查", "搜索", "看看", "需要", "想要", "我",
            "评分最高", "最高评分", "高评分", "最热", "热门", "最新"
    );

    private static final Pattern ARABIC_LIMIT_PATTERN = Pattern.compile("(?:前|top|Top|TOP)\\s*(\\d{1,2})");
    private static final Map<Character, Integer> CHINESE_NUMBERS = Map.of(
            '一', 1, '二', 2, '两', 2, '三', 3, '四', 4, '五', 5,
            '六', 6, '七', 7, '八', 8, '九', 9
    );

    @PostMapping("/nl")
    public Result<NlSearchResult> nlSearch(@Valid @RequestBody NlSearchRequest request, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (userId != null && request.getQuery() != null) {
            searchService.recordUserSearch(userId, request.getQuery());
        }

        Map<String, Object> localIntent = buildLocalIntent(request.getQuery());
        Map<String, Object> intent = new LinkedHashMap<>(localIntent);

        // Extract parameters from parsed intent
        List<String> keywords = toStringList(intent.get("keywords"));

        Long categoryId = null;
        Object categoryObj = intent.get("category");
        String categoryName = categoryObj != null ? categoryObj.toString() : null;
        if (categoryName != null && !categoryName.equals("null")) {
            try {
                List<Category> tree = categoryService.getTree();
                categoryId = findCategoryId(tree, categoryName);
            } catch (Exception ignored) {}
        }

        // Extract tags from parsed intent
        List<String> tags = toStringList(intent.get("tags"));

        // Extract minRating
        Double minRating = null;
        Object minRatingObj = intent.get("minRating");
        if (minRatingObj instanceof Number) {
            minRating = ((Number) minRatingObj).doubleValue();
        }

        String sortBy = (String) intent.getOrDefault("sortBy", "relevance");
        int limit = intent.get("limit") instanceof Number ? ((Number) intent.get("limit")).intValue() : 10;
        limit = Math.max(1, Math.min(limit, 30));

        PageResult<Resource> results = searchService.searchByKeywords(
                keywords != null && !keywords.isEmpty() ? keywords : List.of(request.getQuery()),
                categoryId, tags, minRating, sortBy, 1, limit, userId);

        if (results.getTotal() > 0 || shouldPreferLocalIntent(localIntent)) {
            return Result.success(new NlSearchResult(intent, results.getRecords(), results.getTotal()));
        }

        String parsed = aiService.parseNaturalLanguageQuery(request.getQuery());
        if (parsed != null) {
            try {
                Map<String, Object> aiIntent = objectMapper.readValue(parsed, new TypeReference<>() {});
                intent.putAll(aiIntent);
                mergeLocalCorrections(intent, localIntent);
                keywords = toStringList(intent.get("keywords"));
                categoryId = null;
                Object aiCategoryObj = intent.get("category");
                String aiCategoryName = aiCategoryObj != null ? aiCategoryObj.toString() : null;
                if (aiCategoryName != null && !aiCategoryName.equals("null")) {
                    try {
                        List<Category> tree = categoryService.getTree();
                        categoryId = findCategoryId(tree, aiCategoryName);
                    } catch (Exception ignored) {}
                }
                tags = toStringList(intent.get("tags"));
                minRating = null;
                Object aiMinRatingObj = intent.get("minRating");
                if (aiMinRatingObj instanceof Number) {
                    minRating = ((Number) aiMinRatingObj).doubleValue();
                }
                sortBy = (String) intent.getOrDefault("sortBy", sortBy);
                limit = intent.get("limit") instanceof Number ? ((Number) intent.get("limit")).intValue() : limit;
                limit = Math.max(1, Math.min(limit, 30));
                results = searchService.searchByKeywords(
                        keywords != null && !keywords.isEmpty() ? keywords : List.of(request.getQuery()),
                        categoryId, tags, minRating, sortBy, 1, limit, userId);
            } catch (Exception e) {
                log.warn("Failed to parse AI JSON, using local intent: {}", e.getMessage());
            }
        } else {
            log.warn("AI parse returned null, using local intent");
        }

        if (results.getTotal() == 0 && minRating != null) {
            intent.put("relaxed", true);
            results = searchService.searchByKeywords(
                    keywords != null && !keywords.isEmpty() ? keywords : List.of(request.getQuery()),
                    categoryId, tags, null, sortBy, 1, limit, userId);
        }
        if (results.getTotal() == 0 && ((tags != null && !tags.isEmpty()) || categoryId != null)) {
            intent.put("relaxed", true);
            List<String> localKeywords = toStringList(localIntent.get("keywords"));
            results = searchService.searchByKeywords(
                    localKeywords.isEmpty() ? List.of(request.getQuery()) : localKeywords,
                    null, null, null, sortBy, 1, limit, userId);
        }
        if (results.getTotal() == 0) {
            List<String> narrowedKeywords = toStringList(localIntent.get("keywords")).stream()
                    .filter(k -> !isNoiseKeyword(k))
                    .limit(2)
                    .toList();
            if (!narrowedKeywords.isEmpty()) {
                intent.put("relaxed", true);
                results = searchService.searchByKeywords(
                        narrowedKeywords,
                        null, null, null, sortBy, 1, limit, userId);
            }
        }
        return Result.success(new NlSearchResult(intent, results.getRecords(), results.getTotal()));
    }

    private Map<String, Object> buildLocalIntent(String query) {
        Map<String, Object> intent = new LinkedHashMap<>();
        List<String> keywords = extractKeywords(query);
        intent.put("keywords", keywords.isEmpty() ? List.of(query) : keywords);
        intent.put("sortBy", inferSortBy(query));
        intent.put("limit", inferLimit(query));
        intent.put("tags", List.of());
        intent.put("minRating", inferMinRating(query));
        String category = inferCategory(query);
        if (category != null) {
            intent.put("category", category);
        }
        intent.put("source", "local");
        return intent;
    }

    private void mergeLocalCorrections(Map<String, Object> intent, Map<String, Object> localIntent) {
        List<String> aiKeywords = toStringList(intent.get("keywords"));
        List<String> localKeywords = toStringList(localIntent.get("keywords"));
        boolean aiKeywordsTooBroad = aiKeywords.isEmpty() || aiKeywords.stream().anyMatch(this::isNoiseKeyword);
        if (aiKeywordsTooBroad && !localKeywords.isEmpty()) {
            intent.put("keywords", localKeywords);
        }

        String localSortBy = String.valueOf(localIntent.getOrDefault("sortBy", "relevance"));
        if (!"relevance".equals(localSortBy)) {
            intent.put("sortBy", localSortBy);
        }

        Object localLimit = localIntent.get("limit");
        if (localLimit instanceof Number && ((Number) localLimit).intValue() != 10) {
            intent.put("limit", localLimit);
        }

        if (!intent.containsKey("category") && localIntent.containsKey("category")) {
            intent.put("category", localIntent.get("category"));
        }
        intent.put("source", "ai+local");
    }

    private List<String> toStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !"null".equalsIgnoreCase(s))
                    .distinct()
                    .toList();
        }
        String str = value.toString().trim();
        return str.isEmpty() || "null".equalsIgnoreCase(str) ? List.of() : List.of(str);
    }

    private String inferSortBy(String query) {
        if (query == null) return "relevance";
        if (query.contains("评分最高") || query.contains("高分") || query.contains("评价最好")) return "rating";
        if (query.contains("最热") || query.contains("热门") || query.contains("热度")) return "hot";
        if (query.contains("最新") || query.contains("最近")) return "latest";
        return "relevance";
    }

    private int inferLimit(String query) {
        if (query == null) return 10;
        Matcher matcher = ARABIC_LIMIT_PATTERN.matcher(query);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        int index = query.indexOf('前');
        if (index >= 0 && index + 1 < query.length()) {
            Integer n = CHINESE_NUMBERS.get(query.charAt(index + 1));
            if (n != null) return n;
        }
        return 10;
    }

    private Double inferMinRating(String query) {
        if (query == null) return null;
        Matcher matcher = Pattern.compile("(\\d(?:\\.\\d)?)\\s*分\\s*(?:以上|起)").matcher(query);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private String inferCategory(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            return findCategoryName(categoryService.getTree(), query);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extract meaningful keywords from Chinese text by stripping stop words and punctuation.
     * Splits on common delimiters, filters stop words, returns by length descending.
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> phraseKeywords = new ArrayList<>();
        Matcher techMatcher = Pattern.compile("([A-Za-z][A-Za-z0-9+#.]*)([\\u4e00-\\u9fff]{2,8})").matcher(text);
        while (techMatcher.find()) {
            String phrase = techMatcher.group(1) + techMatcher.group(2);
            if (!isNoiseKeyword(phrase)) {
                phraseKeywords.add(phrase);
            }
            String chinese = techMatcher.group(2);
            if (chinese.length() > 2) {
                String shorterPhrase = techMatcher.group(1) + chinese.substring(0, 2);
                if (!isNoiseKeyword(shorterPhrase)) {
                    phraseKeywords.add(shorterPhrase);
                }
            }
            phraseKeywords.add(techMatcher.group(1));
        }
        // Split on punctuation, spaces, and common delimiters
        String[] tokens = text.split("[，。！？、；：\"\"''（）【】\\s,.!?;:\"'()\\[\\]]+");
        List<String> keywords = new ArrayList<>(phraseKeywords);
        for (String token : tokens) {
            String t = token.trim();
            if (t.length() >= 2 && !STOP_WORDS.contains(t) && !isNoiseKeyword(t)) {
                keywords.add(t);
            }
        }
        // Sort by length descending — longer tokens are more specific
        keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keywords.stream().distinct().limit(6).toList();
    }

    private boolean isNoiseKeyword(String keyword) {
        if (keyword == null) {
            return true;
        }
        String text = keyword.trim();
        if (text.length() < 2 || text.length() > 12) {
            return true;
        }
        if (text.contains("推荐") || text.contains("资源")) {
            return true;
        }
        if (text.contains("评分") || text.contains("最高") || text.contains("最热") || text.contains("最新")) {
            return true;
        }
        return text.matches(".*前\\d+个.*") || text.matches(".*前[一二两三四五六七八九十]个.*");
    }

    private boolean shouldPreferLocalIntent(Map<String, Object> localIntent) {
        List<String> localKeywords = toStringList(localIntent.get("keywords"));
        String localSortBy = String.valueOf(localIntent.getOrDefault("sortBy", "relevance"));
        int localLimit = localIntent.get("limit") instanceof Number ? ((Number) localIntent.get("limit")).intValue() : 10;
        return !localKeywords.isEmpty()
                && (localKeywords.size() <= 3 || !"relevance".equals(localSortBy) || localLimit != 10);
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

    private String findCategoryName(List<Category> categories, String query) {
        for (Category cat : categories) {
            if (query.contains(cat.getName())) {
                return cat.getName();
            }
            if (cat.getChildren() != null) {
                String found = findCategoryName(cat.getChildren(), query);
                if (found != null) return found;
            }
        }
        return null;
    }
}
