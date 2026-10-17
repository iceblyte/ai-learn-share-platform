package com.learning.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                // ResourceFile.uploadedAt：唯一不叫 createdAt/updatedAt 的 fill 字段，
                // 漏填会让该列以显式 null 插入（DB DEFAULT 不生效），发布文件资源直接报错
                this.strictInsertFill(metaObject, "uploadedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 不能用 strictUpdateFill：它只在字段为 null 时填充，而更新前实体通常
                // 已从 DB 查出（updatedAt 非空），会导致旧值被原样写回、updated_at 永不刷新。
                // 这里强制覆盖为当前时间（仅对实体级更新生效，UpdateWrapper.set 不经过此钩子）。
                if (metaObject.hasSetter("updatedAt")) {
                    metaObject.setValue("updatedAt", LocalDateTime.now());
                }
            }
        };
    }
}
