package com.learning.platform.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learning.platform.entity.ResourceFile;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus 自动填充回归：ResourceFile.uploadedAt 是全项目唯一
 * 不叫 createdAt/updatedAt 的 fill 字段——handler 必须覆盖它，
 * 否则发布文件资源时该列以显式 null 插入（DB DEFAULT 不生效）直接报错。
 */
class MyBatisPlusConfigTest {

    @Test
    @DisplayName("插入填充：ResourceFile.uploadedAt 自动填当前时间")
    void insertFill_setsUploadedAt() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ResourceFile.class);

        ResourceFile file = new ResourceFile();
        new MyBatisPlusConfig().metaObjectHandler()
                .insertFill(SystemMetaObject.forObject(file));

        assertThat(file.getUploadedAt()).isNotNull();
    }
}
