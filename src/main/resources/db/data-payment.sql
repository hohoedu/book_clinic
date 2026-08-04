-- ════════════════════════════════════════════════════════════════════
-- 상품 / 환불 규정 시드 (2026-08-04)
--
-- [수동 실행] 이 파일은 spring.sql.init의 data-locations에 넣지 않는다.
-- 두 테이블은 본사가 관리자 화면에서 고치는 마스터라 매 기동 덮어쓰면 안 되고,
-- 리셋 대상도 아니기 때문이다(data-books.sql, data-itempool.sql과 같은 취급).
-- 개발 DB든 운영 DB든 처음 한 번만 실행한다. 재실행해도 중복 INSERT는 되지 않는다.
--
-- [아래 값은 예시다] 실제 상품 가격·횟수와 환불 규정은 확정되면 이 파일을 고쳐
-- 다시 실행하거나 관리자 화면에서 수정한다.
-- ════════════════════════════════════════════════════════════════════

-- ── 상품 ────────────────────────────────────────────────────────────
-- 책방(PG 결제)과 서당(일괄청구)이 같은 상품을 쓴다. 청구 방법만 다를 뿐 제공하는 이용권은 같다.
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_product WHERE product_code = 'BOOK_M8')
    INSERT INTO erp_bookstore_product (product_code, product_name, service_code, total_count, price, is_active)
    VALUES ('BOOK_M8', N'독서 클리닉 월 8회권', 'BOOK', 8, 50000, 1);

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_product WHERE product_code = 'BOOK_M4')
    INSERT INTO erp_bookstore_product (product_code, product_name, service_code, total_count, price, is_active)
    VALUES ('BOOK_M4', N'독서 클리닉 월 4회권', 'BOOK', 4, 30000, 1);

-- ── 환불 규정 ───────────────────────────────────────────────────────
-- priority 오름차순으로 훑어 "결제 후 max_days 이내 && 사용 max_count 이하"에 처음 맞는
-- 한 건만 적용한다. 어디에도 안 걸리면 환불 불가다.
--
-- 규정을 고칠 때는 이 행을 수정하지 말 것. 그 규정으로 환불해 준 과거 건의 근거가 사라진다.
-- is_active = 0으로 내리고 새 rule_code로 새 행을 넣는다.
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2601_FULL')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2601_FULL', N'7일 이내 미사용 전액환불', 7, 0, 100, 1, 1);

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2601_HALF')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2601_HALF', N'7일 이내 2회 이하 사용 50% 환불', 7, 2, 50, 2, 1);

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2601_PART')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2601_PART', N'14일 이내 4회 이하 사용 30% 환불', 14, 4, 30, 3, 1);
