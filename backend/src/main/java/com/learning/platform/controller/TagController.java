package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.entity.Tag;
import com.learning.platform.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<Tag>> list() {
        return Result.success(tagService.getAll());
    }

    @GetMapping("/hot")
    public Result<List<Tag>> hot(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(tagService.getHot(limit));
    }

    @GetMapping("/search")
    public Result<List<Tag>> search(@RequestParam String keyword) {
        return Result.success(tagService.search(keyword));
    }
}
