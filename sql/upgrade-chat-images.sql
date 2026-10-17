-- ============================================================
-- 存量库升级：扩展五（AI 聊天工作流）—— 配图与审查状态落库
-- 适用：已按旧版 init.sql 建库的环境；全新安装（用新版 init.sql）
--       建表已含新列，无需执行本文件。
-- 注意：MySQL 8 无 ADD COLUMN IF NOT EXISTS，重复执行会报
--       Duplicate column，可忽略。
-- 日期：2026-10-17
-- ============================================================

USE `learning_platform`;

ALTER TABLE `chat_message`
    ADD COLUMN `images_json` VARCHAR(2000) DEFAULT NULL
        COMMENT '回答配图快照(JSON数组: source/url/alt, 工作流imageCollect产出)' AFTER `context_json`;

ALTER TABLE `chat_message`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'OK'
        COMMENT '状态: OK/FAILED/MODERATED';
