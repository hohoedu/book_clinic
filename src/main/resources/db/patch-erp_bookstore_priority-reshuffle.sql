-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 권장도서 순위 재섞기 (2026-09-14)
--
-- 이미 저장돼 있는 권장도서 순위(erp_bookstore_priority)를 아래 3가지 규칙 순서로 다시 섞는다.
--   1순위: 난이도 하 → 중 → 상
--   2순위: 같은 난이도 구간 안에서 분류(content_type)가 연달아 나오지 않도록 라운드로빈으로 분산
--   3순위: 위 두 조건을 지키는 범위 안에서 랜덤(NEWID())
--
-- data-priority.sql의 최초 시딩 로직과 동일한 규칙이고, 차이는 INSERT가 아니라
-- 활성 draft(is_active='Y')의 기존 sort_order를 UPDATE로 재계산한다는 점뿐이다.
-- NEWID()를 쓰므로 실행할 때마다 매번 다르게 섞인다. 여러 번 실행해도 안전(멱등, 재실행 가능).

WITH graded AS (
    SELECT c.content_id,
           c.schoolyear,
           c.content_type,
           CASE LTRIM(RTRIM(c.difficulty))
               WHEN N'하' THEN 1
               WHEN N'상' THEN 3
               ELSE 2                      -- '중' 그리고 미입력(NULL/공백)
           END AS diff_rank
    FROM erp_bookstore_content c
    WHERE c.state = 'Y'
), spread AS (
    SELECT g.content_id,
           g.schoolyear,
           g.content_type,
           g.diff_rank,
           ROW_NUMBER() OVER (
               PARTITION BY g.schoolyear, g.diff_rank, g.content_type
               ORDER BY NEWID()
           ) AS type_seq
    FROM graded g
), ranked AS (
    SELECT p.draft_id,
           p.content_id,
           ROW_NUMBER() OVER (
               PARTITION BY p.draft_id
               ORDER BY s.diff_rank, s.type_seq, NEWID()
           ) AS new_sort_order
    FROM erp_bookstore_priority p
    JOIN erp_bookstore_priority_draft d
        ON d.draft_id = p.draft_id AND d.is_active = 'Y'
    JOIN spread s
        ON s.content_id = p.content_id AND s.schoolyear = d.schoolyear
)
UPDATE p
SET p.sort_order = r.new_sort_order
FROM erp_bookstore_priority p
JOIN ranked r ON r.draft_id = p.draft_id AND r.content_id = p.content_id;
