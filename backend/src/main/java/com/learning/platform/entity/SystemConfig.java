package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置：管理员运行时开关的键值存储。
 * 主键即 config_key（一行一个配置项），无自增 id。
 */
@Data
@TableName("system_config")
public class SystemConfig {

    @TableId
    private String configKey;

    private String configValue;

    private String remark;

    private LocalDateTime updatedAt;
}
