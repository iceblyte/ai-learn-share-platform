package com.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learning.platform.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    @Select("SELECT r.*, c.name as category_name, u.nickname as publisher_nickname, u.avatar_url as publisher_avatar " +
            "FROM resource r " +
            "LEFT JOIN category c ON r.category_id = c.id " +
            "LEFT JOIN user u ON r.publisher_id = u.id " +
            "WHERE r.status = 'PUBLISHED' AND r.is_deleted = 0 " +
            "ORDER BY r.hot_score DESC " +
            "LIMIT #{limit}")
    List<Resource> selectHotResources(@Param("limit") int limit);

    @Select("SELECT r.*, c.name as category_name, u.nickname as publisher_nickname, u.avatar_url as publisher_avatar " +
            "FROM resource r " +
            "LEFT JOIN category c ON r.category_id = c.id " +
            "LEFT JOIN user u ON r.publisher_id = u.id " +
            "WHERE r.status = 'PUBLISHED' AND r.is_deleted = 0 " +
            "ORDER BY r.created_at DESC " +
            "LIMIT #{limit}")
    List<Resource> selectLatestResources(@Param("limit") int limit);
}
