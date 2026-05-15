package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.entity.Tag;
import com.learning.platform.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public List<Tag> getAll() {
        return tagMapper.selectList(new QueryWrapper<Tag>().orderByDesc("usage_count"));
    }

    public List<Tag> getHot(int limit) {
        return tagMapper.selectList(
                new QueryWrapper<Tag>().orderByDesc("usage_count").last("LIMIT " + limit));
    }

    public List<Tag> search(String keyword) {
        return tagMapper.selectList(
                new QueryWrapper<Tag>().like("name", keyword).orderByDesc("usage_count").last("LIMIT 20"));
    }
}
