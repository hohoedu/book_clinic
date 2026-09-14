-- ════════════════════════════════════════════════════════════════════
-- 상품 / 환불 규정 시드 (2026-08-04)
--
-- [수동 실행] 이 파일은 spring.sql.init의 data-locations에 넣지 않는다.
-- 두 테이블은 본사가 관리자 화면에서 고치는 마스터라 매 기동 덮어쓰면 안 되고,
-- 리셋 대상도 아니기 때문이다(data-books.sql, data-itempool.sql과 같은 취급).
-- 개발 DB든 운영 DB든 처음 한 번만 실행한다. 재실행해도 중복 INSERT는 되지 않는다.
--
-- [재실행] 상품 가격·횟수가 바뀌면 이 파일을 고쳐 다시 실행한다. 판매를 내리는 UPDATE와
-- 새 상품 INSERT 모두 여러 번 실행해도 결과가 같다.
-- ════════════════════════════════════════════════════════════════════

-- ── 상품 ────────────────────────────────────────────────────────────
-- 책방(PG 결제)과 서당(일괄청구)이 같은 상품을 쓴다. 청구 방법만 다를 뿐 제공하는 이용권은 같다.
--
-- [2026-09-14 가격 정책 변경] 이용권 1회당 5,000원이고, 한 번 구매하면 12회를 한꺼번에 산다.
-- 그래서 판매 상품은 12회권 한 종류뿐이다(60,000원). 가격을 상품 행에 그대로 적어 두는 이유는
-- 결제 금액 검증이 이 값을 기준으로 하기 때문이다 — 단가 × 수량을 서버가 계산하는 구조가 아니다.
--
-- 기존 월 4회/8회권은 판매를 내린다(is_active=0). 과거 결제·이용권이 product_id로 이 행들을
-- 참조하므로 삭제하지 않는다 — 삭제하면 지난 결제내역의 상품명이 사라진다.
UPDATE erp_bookstore_product SET is_active = 0
    WHERE product_code IN ('BOOK_M8', 'BOOK_M4') AND is_active = 1;

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_product WHERE product_code = 'BOOK_12')
    INSERT INTO erp_bookstore_product (product_code, product_name, service_code, total_count, price, is_active)
    VALUES ('BOOK_12', N'독서 클리닉 12회권', 'BOOK', 12, 60000, 1);

-- ── 환불 규정 ───────────────────────────────────────────────────────
-- priority 오름차순으로 훑어 "사용 max_count 이하"에 처음 맞는 한 건만 적용한다.
-- 어디에도 안 걸리면(3회 이상 사용) 환불 불가다. 날짜(max_days) 조건은 2026-08-05 폐지 —
-- PaymentService.calculateRefund()가 더 이상 이 컬럼을 판정에 쓰지 않는다. 컬럼 자체는
-- NOT NULL이라 값은 채우되(9999 = 사실상 무제한), 의미 없는 값임을 알 수 있게 표시한다.
--
-- 규정을 고칠 때는 이 행을 수정하지 말 것. 그 규정으로 환불해 준 과거 건의 근거가 사라진다.
-- is_active = 0으로 내리고 새 rule_code로 새 행을 넣는다.
UPDATE erp_bookstore_refund_rule SET is_active = 0
    WHERE rule_code IN ('R2601_FULL', 'R2601_HALF', 'R2601_PART') AND is_active = 1;

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2608_FULL')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2608_FULL', N'미사용 전액환불', 9999, 0, 100, 1, 1);

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2608_Q3')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2608_Q3', N'1회 사용 75% 환불', 9999, 1, 75, 2, 1);

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_refund_rule WHERE rule_code = 'R2608_HALF')
    INSERT INTO erp_bookstore_refund_rule (rule_code, rule_name, max_days, max_count, refund_rate, priority, is_active)
    VALUES ('R2608_HALF', N'2회 사용 50% 환불', 9999, 2, 50, 3, 1);
-- 3회 이상 사용은 위 어느 규정에도 안 걸려 자연히 환불 불가로 떨어진다(새 규정 행 불필요).
