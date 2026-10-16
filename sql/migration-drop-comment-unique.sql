-- 迁移：删除评论表的内容唯一索引（2026-10-16）
--
-- 背景：idx_comment_unique (resource_id, user_id, content(191)) 当初是为
-- seed.sql 幂等而加，但会误伤正常使用——同一用户对同一资源发表相同内容
-- 的评论（如两次“加油”）会触发 DuplicateKeyException 直接 500。
-- 现在 seed 的所有评论块均已改为 NOT EXISTS 幂等去重，索引可以安全移除。
--
-- 执行：mysql -u<user> -p learning_platform < sql/migration-drop-comment-unique.sql

ALTER TABLE `comment` DROP INDEX `idx_comment_unique`;
