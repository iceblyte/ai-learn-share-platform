package com.learning.platform.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.ResourceFile;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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

    @Test
    @DisplayName("更新填充：updatedAt 即使已有旧值也强制刷新（strictUpdateFill 不会覆盖非空值）")
    void updateFill_overwritesStaleUpdatedAt() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Resource.class);

        Resource resource = new Resource();
        LocalDateTime stale = LocalDateTime.now().minusHours(2);
        resource.setUpdatedAt(stale);
        new MyBatisPlusConfig().metaObjectHandler()
                .updateFill(SystemMetaObject.forObject(resource));

        assertThat(resource.getUpdatedAt()).isAfter(stale);
    }
}
