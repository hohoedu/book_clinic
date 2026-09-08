-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 자동결제 첫 결제일 선택 (2026-09-08)
--
-- 학부모가 카드 등록 시 "첫 결제일"을 고른다(E-1). 그 날짜에 배치가 빌키로 첫 청구를 내고,
-- 이용권은 결제일 ~ 다음달 결제일 전일(1개월)로 발급된다. 이후 매달 같은 일자에 반복.
--
-- 이니시스 빌키발급 창은 orderId만 되돌려줘서, 고른 날짜는 서버가 PENDING 구독 행에
-- 미리 저장해 두고 등록 확정 시점에 anchor_day / next_billing_on / billing_cycle_from을
-- 이 값으로 세운다. NULL이면 등록일(오늘)로 폴백한다(기존 동작).
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_subscription') AND name = 'first_billing_on')
    ALTER TABLE erp_bookstore_subscription ADD first_billing_on DATE;   -- 학부모가 고른 첫 결제일. NULL이면 등록일
