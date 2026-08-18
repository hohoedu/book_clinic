-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 사전 점검용 — 읽기 전용 (2026-08-06)
--
-- ddl-core.sql / ddl-payment.sql을 실행하기 전에 먼저 이 파일을 돌려서
-- 결과를 확인한다. SELECT만 있고 CREATE/ALTER/DROP은 하나도 없어서
-- 아무리 여러 번 돌려도 운영 DB는 전혀 바뀌지 않는다.
--
-- [보는 법]
--  1) 첫 번째 결과(테이블 존재 여부) — '있음'으로 나오는 테이블이 하나라도 있으면,
--     그 테이블 이름을 알려달라. ddl-core.sql/ddl-payment.sql의 최신 구조와
--     실제 컬럼이 같은지 대조한 뒤에 실행 여부를 판단해야 한다.
--  2) 전부 '없음'이면 그대로 ddl-core.sql → ddl-payment.sql을 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════

SELECT
    t.name AS 대상_테이블,
    CASE WHEN OBJECT_ID(t.name, 'U') IS NULL THEN '없음' ELSE '있음' END AS 존재_여부
FROM (VALUES
    ('erp_bookstore_code'), ('erp_bookstore_content'), ('erp_bookstore_content_del'),
    ('erp_bookstore_content_detail'), ('erp_bookstore_content_detail_del'),
    ('erp_bookstore_priority_draft'), ('erp_bookstore_priority'),
    ('erp_bookstore_priority_draft_del'), ('erp_bookstore_priority_del'),
    ('erp_bookstore_item'), ('erp_bookstore_item_loan'), ('erp_bookstore_item_del'),
    ('erp_bookstore_item_stock_log'), ('erp_bookstore_itempool'), ('erp_bookstore_itempool_del'),
    ('erp_notification'), ('erp_bookstore_recommend_log'), ('erp_bookstore_quiz_answer_log'),
    ('erp_bookstore_quiz_reset_log'), ('erp_bookstore_level'), ('erp_bookstore_badge'),
    ('erp_bookstore_student_badge'), ('erp_bookstore_student_card'), ('erp_bookstore_clinic_session'),
    ('erp_bookstore_slot_instance'), ('erp_bookstore_reservation'), ('erp_bookstore_reservation_log'),
    ('erp_bookstore_diary'), ('erp_bookstore_diary_detail'),
    ('erp_bookstore_attitude_code'), ('erp_bookstore_attitude'),
    ('erp_bookstore_product'), ('erp_bookstore_refund_rule'), ('erp_bookstore_pass'),
    ('erp_bookstore_pass_use'), ('erp_bookstore_payment'), ('erp_student_sibling'),
    ('erp_bookstore_payment_cancel'), ('erp_bookstore_payment_log')
) AS t(name)
ORDER BY 존재_여부 DESC, 대상_테이블;

-- 위에서 '있음'으로 나온 테이블이 있다면, 그 테이블 이름을 아래 IN(...)에 채워서
-- 다시 실행하면 실제 컬럼 목록까지 볼 수 있다. (예시로 몇 개 넣어뒀다 — 실제로 있는
-- 테이블명으로 바꿔서 실행)
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_NAME IN ('erp_bookstore_item', 'erp_bookstore_recommend_log')
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
