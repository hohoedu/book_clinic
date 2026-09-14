-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 이용권 유효기간을 "첫 예약일부터 90일"로 (2026-09-14)
--
-- 정책
--   · 이용권 1회 5,000원, 한 번에 12회 구매(BOOK_12). 같은 달에 여러 번 살 수 있다.
--   · 사둔 이용권은 만료일이 없다. 그 이용권을 처음 쓰는 예약이 잡힐 때
--     "그 회차 날짜부터 90일"로 기간이 확정된다(valid_from ~ valid_from + 89일).
--   · 12회를 다 쓰고 두 번째 묶음을 쓰기 시작하면, 그때 그 묶음의 90일이 시작된다.
--   · 첫 예약을 취소해 사용 이력이 0이 되면 기간은 다시 풀린다(미배정으로 복귀).
--
-- 그래서 valid_from / valid_until 이 NULL을 허용해야 한다. 두 컬럼은 달력 월 주기 시절
-- NOT NULL로 만들어져 있어서, 이미 배포된 DB는 이 패치로 제약을 풀어야 한다.
-- 기존 행의 값은 건드리지 않는다 — 이미 쓰기 시작한 이용권의 기간을 지우면 안 된다.
--
-- [결제 쪽 영향] 일시불 구매는 payment.cycle_from / cycle_until 을 비워서 기록하게 바뀌었다.
-- 그 두 컬럼은 이제 자동결제의 청구 주기 전용이고, 이용권 기간과는 무관하다. 덕분에
-- UX_payment_active_cycle(주기 유니크, cycle_from IS NOT NULL 조건)에 걸리지 않아
-- 같은 달 재구매가 가능하다 — 인덱스는 그대로 두면 된다.
--
-- schema.sql / ddl-payment.sql 은 이 패치와 함께 최신 구조로 수정했다.
-- ════════════════════════════════════════════════════════════════════

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_from' AND is_nullable = 0)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_from DATE NULL;
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_until' AND is_nullable = 0)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_until DATE NULL;
GO

-- 확인 — 두 컬럼이 is_nullable = 1 로 나와야 한다.
-- SELECT name, is_nullable FROM sys.columns
-- WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name IN ('valid_from', 'valid_until');
