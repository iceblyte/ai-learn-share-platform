-- ============================================================
-- 扩展功能增量升级脚本（2026-10-18）
-- 对应提交：通知系统 / 智能搜索（Tavily）
--
-- 用法：在已有数据库上直接执行本文件（无需重跑 init.sql / seed.sql）。
-- 本脚本可重复执行：表用 CREATE TABLE IF NOT EXISTS，加列用
-- information_schema 判断，已存在则跳过。不删除、不修改任何现有数据。
-- ============================================================
USE learning_platform;

-- ------------------------------------------------------------
-- 1. 站内通知表（通知系统：铃铛 + 通知中心）
--    对应 init.sql 第 20 节
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id` BIGINT NOT NULL COMMENT '接收人',
    `type` VARCHAR(32) NOT NULL COMMENT '类型：FOLLOW/LIKE_RESOURCE/LIKE_COMMENT/FAVORITE/COMMENT/REPLY/MENTION/AI_SUMMARY',
    `actor_id` BIGINT NULL COMMENT '触发者（AI 助手触发的通知可为空）',
    `resource_id` BIGINT NULL COMMENT '关联资源',
    `comment_id` BIGINT NULL COMMENT '关联评论',
    `content` VARCHAR(500) NULL COMMENT '通知摘要文本',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_user_read` (`user_id`, `is_read`),
    KEY `idx_notification_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';

-- ------------------------------------------------------------
-- 2. chat_message 加联网搜索来源快照列（智能搜索）
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程判断防重复执行报错
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS `upgrade_add_sources_json`;
DELIMITER $$
CREATE PROCEDURE `upgrade_add_sources_json`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chat_message'
          AND COLUMN_NAME = 'sources_json'
    ) THEN
        ALTER TABLE `chat_message`
            ADD COLUMN `sources_json` VARCHAR(4000) DEFAULT NULL
            COMMENT '联网搜索来源快照(JSON数组: title/url/snippet, 智能搜索开启时)'
            AFTER `images_json`;
    END IF;
END$$
DELIMITER ;

CALL `upgrade_add_sources_json`();
DROP PROCEDURE `upgrade_add_sources_json`;

-- ============================================================
-- 执行完毕。验证（可选）：
--   SHOW COLUMNS FROM chat_message LIKE 'sources_json';
--   SHOW TABLES LIKE 'notification';
-- ============================================================
