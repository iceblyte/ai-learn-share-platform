package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.NlSearchRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.service.AiService;
import com.learning.platform.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final AiService aiService;

    @GetMapping
    public Result<PageResult<Resource>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(searchService.search(keyword, categoryId, sortBy, page, size));
    }

    @PostMapping("/nl")
    public Result<String> nlSearch(@Valid @RequestBody NlSearchRequest request) {
        String parsed = aiService.parseNaturalLanguageQuery(request.getQuery());
        return Result.success(parsed);
    }

    @GetMapping("/hot")
    public Result<List<String>> hotSearches() {
        return Result.success(searchService.getHotSearches());
    }
}
