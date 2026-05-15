package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.entity.Category;
import com.learning.platform.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> getTree() {
        List<Category> all = categoryMapper.selectList(
                new QueryWrapper<Category>().eq("is_active", 1).orderByAsc("sort_order"));
        return buildTree(all, null);
    }

    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    private List<Category> buildTree(List<Category> all, Long parentId) {
        return all.stream()
                .filter(c -> (parentId == null && c.getParentId() == null) ||
                        (parentId != null && parentId.equals(c.getParentId())))
                .peek(c -> c.setChildren(buildTree(all, c.getId())))
                .collect(Collectors.toList());
    }
}
