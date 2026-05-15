package com.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learning.platform.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
