-- ============================================
-- AI 个性化学习资源分享平台 - 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS learning_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE learning_platform;

-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希(BCrypt)',
    `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `bio` VARCHAR(500) DEFAULT '' COMMENT '个人简介',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER/PUBLISHER/ADMIN',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_user_username` (`username`),
    UNIQUE KEY `idx_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. 分类表
-- ============================================
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID, NULL表示顶级分类',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_parent` (`parent_id`),
    KEY `idx_category_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类表';

-- ============================================
-- 3. 标签表
-- ============================================
CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `usage_count` INT NOT NULL DEFAULT 0 COMMENT '使用次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_tag_name` (`name`),
    KEY `idx_tag_usage` (`usage_count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ============================================
-- 4. 资源表
-- ============================================
CREATE TABLE IF NOT EXISTS `resource` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `title` VARCHAR(200) NOT NULL COMMENT '资源标题',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `publisher_id` BIGINT NOT NULL COMMENT '发布者ID',
    `description` TEXT COMMENT '资源描述(支持Markdown)',
    `ai_summary` VARCHAR(500) DEFAULT NULL COMMENT 'AI生成的摘要',
    `resource_type` VARCHAR(10) NOT NULL DEFAULT 'FILE' COMMENT '资源类型: FILE/LINK',
    `external_url` VARCHAR(500) DEFAULT NULL COMMENT '外部链接',
    `cover_image_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `favorite_count` INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
    `avg_rating` DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '平均评分',
    `rating_count` INT NOT NULL DEFAULT 0 COMMENT '评分人数',
    `hot_score` INT NOT NULL DEFAULT 0 COMMENT '热度分',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: DRAFT/PENDING/PUBLISHED/REJECTED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_resource_category` (`category_id`),
    KEY `idx_resource_publisher` (`publisher_id`),
    KEY `idx_resource_status` (`status`),
    KEY `idx_resource_hot_score` (`hot_score` DESC),
    KEY `idx_resource_created` (`created_at` DESC),
    FULLTEXT INDEX `ft_resource_title_desc` (`title`, `description`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表';

-- ============================================
-- 5. 资源-标签关联表
-- ============================================
CREATE TABLE IF NOT EXISTS `resource_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_rt_unique` (`resource_id`, `tag_id`),
    KEY `idx_rt_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源-标签关联表';

-- ============================================
-- 6. 资源文件表
-- ============================================
CREATE TABLE IF NOT EXISTS `resource_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件URL',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件类型',
    `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    KEY `idx_rf_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源文件表';

-- ============================================
-- 7. 评论表
-- ============================================
CREATE TABLE IF NOT EXISTS `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID, NULL表示一级评论',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_comment_resource` (`resource_id`),
    KEY `idx_comment_user` (`user_id`),
    KEY `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================
-- 8. 点赞记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `like_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `target_id` BIGINT NOT NULL COMMENT '目标ID(资源或评论)',
    `target_type` VARCHAR(10) NOT NULL COMMENT '目标类型: RESOURCE/COMMENT',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_like_unique` (`user_id`, `target_id`, `target_type`),
    KEY `idx_like_target` (`target_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- ============================================
-- 9. 收藏表
-- ============================================
CREATE TABLE IF NOT EXISTS `favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_fav_unique` (`user_id`, `resource_id`),
    KEY `idx_fav_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- ============================================
-- 10. 评分表
-- ============================================
CREATE TABLE IF NOT EXISTS `rating` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `score` INT NOT NULL COMMENT '评分(1-5)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_rating_unique` (`user_id`, `resource_id`),
    KEY `idx_rating_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分表';

-- ============================================
-- 11. 搜索历史表
-- ============================================
CREATE TABLE IF NOT EXISTS `search_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `keyword` VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    `search_type` VARCHAR(10) NOT NULL DEFAULT 'KEYWORD' COMMENT '搜索类型: KEYWORD/NL',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_search_user` (`user_id`),
    KEY `idx_search_created` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索历史表';

-- ============================================
-- 12. 用户兴趣标签表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_interest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `weight` FLOAT NOT NULL DEFAULT 1.0 COMMENT '兴趣权重',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_interest_unique` (`user_id`, `tag_id`),
    KEY `idx_interest_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户兴趣标签表';

-- ============================================
-- 13. 推荐日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `recommendation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `resource_id` BIGINT NOT NULL COMMENT '推荐的资源ID',
    `reason` VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
    `algorithm` VARCHAR(50) DEFAULT NULL COMMENT '推荐算法',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_rec_user` (`user_id`),
    KEY `idx_rec_created` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐日志表';

-- ============================================
-- 初始数据
-- ============================================

-- 管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO `user` (`username`, `email`, `password_hash`, `nickname`, `role`, `points`)
VALUES ('admin', 'admin@learning.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'ADMIN', 1000);

-- 默认发布者账号 (密码: publisher123)
INSERT INTO `user` (`username`, `email`, `password_hash`, `nickname`, `role`, `points`)
VALUES ('publisher', 'publisher@learning.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '资源发布者', 'PUBLISHER', 500);

-- 默认分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('计算机科学', NULL, 1),
('数学', NULL, 2),
('语言学习', NULL, 3),
('专业课', NULL, 4),
('考试资料', NULL, 5);

-- 计算机科学子分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('编程语言', 1, 1),
('数据结构与算法', 1, 2),
('操作系统', 1, 3),
('计算机网络', 1, 4),
('数据库', 1, 5),
('人工智能/机器学习', 1, 6);

-- 数学子分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('高等数学', 2, 1),
('线性代数', 2, 2),
('概率论与数理统计', 2, 3),
('离散数学', 2, 4);

-- 语言学习子分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('英语', 3, 1),
('日语', 3, 2),
('其他语种', 3, 3);

-- 考试资料子分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('考研', 5, 1),
('四六级', 5, 2),
('计算机等级考试', 5, 3);

-- 常用标签
INSERT INTO `tag` (`name`, `usage_count`) VALUES
('Java', 0),
('Python', 0),
('C++', 0),
('Go', 0),
('JavaScript', 0),
('TypeScript', 0),
('Vue', 0),
('React', 0),
('Spring Boot', 0),
('并发编程', 0),
('多线程', 0),
('算法', 0),
('数据结构', 0),
('机器学习', 0),
('深度学习', 0),
('数据库', 0),
('MySQL', 0),
('Redis', 0),
('操作系统', 0),
('计算机网络', 0),
('考研', 0),
('四六级', 0),
('笔记', 0),
('视频教程', 0),
('电子书', 0),
('面试', 0),
('项目实战', 0),
('入门教程', 0),
('进阶', 0),
('高数', 0);
