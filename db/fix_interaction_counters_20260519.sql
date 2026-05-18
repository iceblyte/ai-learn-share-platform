-- 修复资源与评论的交互计数，使统计字段与真实交互表一致
-- 执行时间：2026-05-19

UPDATE resource r
LEFT JOIN (
    SELECT target_id, COUNT(*) AS cnt
    FROM like_record
    WHERE target_type = 'RESOURCE'
    GROUP BY target_id
) lr ON lr.target_id = r.id
SET r.like_count = COALESCE(lr.cnt, 0);

UPDATE resource r
LEFT JOIN (
    SELECT resource_id, COUNT(*) AS cnt
    FROM favorite
    GROUP BY resource_id
) f ON f.resource_id = r.id
SET r.favorite_count = COALESCE(f.cnt, 0);

UPDATE resource r
LEFT JOIN (
    SELECT resource_id, COUNT(*) AS cnt
    FROM comment
    WHERE is_deleted = 0
    GROUP BY resource_id
) c ON c.resource_id = r.id
SET r.comment_count = COALESCE(c.cnt, 0);

UPDATE comment c
LEFT JOIN (
    SELECT target_id, COUNT(*) AS cnt
    FROM like_record
    WHERE target_type = 'COMMENT'
    GROUP BY target_id
) lr ON lr.target_id = c.id
SET c.like_count = COALESCE(lr.cnt, 0);
