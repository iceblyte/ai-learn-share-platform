package com.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learning.platform.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
