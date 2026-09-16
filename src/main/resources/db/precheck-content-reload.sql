-- ════════════════════════════════════════════════════════════════════
-- 도서 마스터(erp_bookstore_content) 전체 재적재 사전 점검 — 읽기 전용 (2026-09-16)
--
-- SELECT만 있어서 몇 번을 돌려도 DB는 전혀 바뀌지 않는다.
-- 개발 DB(book_clinic)와 운영 DB(dbhohoedu_stst) 양쪽에서 각각 돌리고
-- 결과를 비교한 뒤에 재적재 방식(A안/B안)을 결정한다.
--
-- [판단 기준]
--  - 2)번 결과에서 참조 행이 "전부 0" 이면  → B안(완전 교체) 가능
--  - 하나라도 0이 아니면                    → A안(content_id 보존 upsert) 필수
--    이미 발급된 content_id에 학생 활동·재고·퀴즈가 매달려 있으므로
--    IDENTITY를 새로 채번하면 그 연결이 전부 끊긴다.
-- ════════════════════════════════════════════════════════════════════

-- 1) 도서 마스터 현황
SELECT '도서 마스터' AS 구분,
       COUNT(*)                          AS 전체건수,
       SUM(CASE WHEN state = 'Y' THEN 1 ELSE 0 END) AS 사용중,
       MIN(content_id)                   AS 최소_id,
       MAX(content_id)                   AS 최대_id
FROM erp_bookstore_content;

-- 2) content_id를 참조하는 테이블별 행 수 — 여기가 핵심
SELECT '참조 데이터' AS 구분, t.tbl AS 테이블,
       t.cnt AS 행수,
       CASE WHEN t.cnt = 0 THEN 'OK(비어있음)' ELSE '주의(데이터 있음)' END AS 판정
FROM (
    SELECT 'erp_bookstore_content_detail'   AS tbl, COUNT(*) AS cnt FROM erp_bookstore_content_detail   UNION ALL
    SELECT 'erp_bookstore_card_path',        COUNT(*) FROM erp_bookstore_card_path        UNION ALL
    SELECT 'erp_bookstore_priority',         COUNT(*) FROM erp_bookstore_priority         UNION ALL
    SELECT 'erp_bookstore_item',             COUNT(*) FROM erp_bookstore_item             UNION ALL
    SELECT 'erp_bookstore_item_stock_log',   COUNT(*) FROM erp_bookstore_item_stock_log   UNION ALL
    SELECT 'erp_bookstore_itempool',         COUNT(*) FROM erp_bookstore_itempool         UNION ALL
    SELECT 'erp_bookstore_recommend_log',    COUNT(*) FROM erp_bookstore_recommend_log    UNION ALL
    SELECT 'erp_bookstore_quiz_answer_log',  COUNT(*) FROM erp_bookstore_quiz_answer_log  UNION ALL
    SELECT 'erp_bookstore_quiz_reset_log',   COUNT(*) FROM erp_bookstore_quiz_reset_log   UNION ALL
    SELECT 'erp_bookstore_student_badge',    COUNT(*) FROM erp_bookstore_student_badge    UNION ALL
    SELECT 'erp_bookstore_student_card',     COUNT(*) FROM erp_bookstore_student_card     UNION ALL
    SELECT 'erp_bookstore_diary_detail',     COUNT(*) FROM erp_bookstore_diary_detail
) AS t
ORDER BY t.cnt DESC, t.tbl;

-- 3) 실제로 걸려 있는 FK 제약조건 (스키마 파일과 운영 DB가 다를 수 있으므로 실물 확인)
SELECT OBJECT_NAME(fk.parent_object_id) AS 자식_테이블,
       COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS 자식_컬럼,
       fk.name AS 제약조건명,
       fk.delete_referential_action_desc AS 삭제시_동작
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
WHERE fk.referenced_object_id = OBJECT_ID('erp_bookstore_content')
ORDER BY 자식_테이블;

-- 4) 제목 중복 여부 — A안은 제목(+저자)을 매칭 키로 쓰므로 중복이 있으면 먼저 정리해야 한다
SELECT '제목 중복' AS 구분, original_title, author, COUNT(*) AS 건수
FROM erp_bookstore_content
GROUP BY original_title, author
HAVING COUNT(*) > 1
ORDER BY 건수 DESC;

-- 5) 삭제/수정 로그 적재 현황 (복구 가능 범위 확인)
SELECT '삭제로그' AS 구분, log_type, COUNT(*) AS 건수, MIN(logged_at) AS 최초, MAX(logged_at) AS 최종
FROM erp_bookstore_content_del
GROUP BY log_type;
