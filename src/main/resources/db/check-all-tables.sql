-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 사전 점검용 — 읽기 전용 (2026-08-21)
--
-- erp_bookstore_ 로 시작하는 테이블 + 관련 테이블(erp_student_sibling, sso_ticket)이
-- 운영 DB에 실제로 몇 개, 어떤 이름으로 있는지 그대로 조회한다. SELECT만 있어서
-- 몇 번을 돌려도 운영 DB는 전혀 안 바뀐다.
--
-- [보는 법] 결과가 하나도 없으면(0 rows) 독서클리닉 테이블이 전혀 없는 상태이므로
-- ddl-core.sql → ddl-payment.sql → ddl-sibling.sql → ddl-sso.sql → ddl-schedule.sql을
-- 그대로 실행하면 된다. 결과가 있으면 그 테이블 이름들을 알려달라 — 최신 ddl과
-- 컬럼이 같은지 대조한 뒤에 실행 여부를 판단해야 한다.
-- ════════════════════════════════════════════════════════════════════

SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME LIKE 'erp_bookstore_%'
   OR TABLE_NAME IN ('erp_student_sibling', 'sso_ticket')
ORDER BY TABLE_NAME;

-- erp_student에 clinic_grade_key / help_needed 컬럼이 이미 있는지도 같이 확인한다.
-- (이 두 컬럼은 어떤 ddl-*.sql에도 안 들어있고 patch-erp_student-*.sql로만 추가되므로
-- 위 결과와 무관하게 별도로 꼭 확인해야 한다)
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'erp_student' AND COLUMN_NAME IN ('clinic_grade_key', 'help_needed');
