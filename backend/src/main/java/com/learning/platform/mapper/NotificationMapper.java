package com.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learning.platform.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
