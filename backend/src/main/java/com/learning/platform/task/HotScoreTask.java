package com.learning.platform.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotScoreTask {

    private final ResourceMapper resourceMapper;

    @Scheduled(fixedRate = 3600000)
    public void calculateHotScore() {
        log.info("Starting hot score calculation...");
        UpdateWrapper<Resource> wrapper = new UpdateWrapper<>();
        wrapper.eq("status", "PUBLISHED")
               .eq("is_deleted", 0)
               .setSql("hot_score = view_count + like_count * 3 + favorite_count * 5 + rating_count * 2 + avg_rating * 10");
        resourceMapper.update(null, wrapper);
        log.info("Hot score calculation completed.");
    }
}
