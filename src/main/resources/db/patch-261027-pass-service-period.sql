-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 서비스 제공기간 + 쿠폰 전환 (2026-10-27)
--
-- 정책
--   · 서비스 제공기간 = 결제일 ~ 결제일 + 90일(당일 포함). 이 기간 안에서만 이용권으로 예약할 수 있다.
--   · 유효기간 = 첫 예약 회차 날짜 ~ + 90일(당일 포함). 결제 후 30일까지 예약이 없으면
--     결제일 + 30일에 자동으로 시작된다. 시작일은 min(첫 예약 회차 날짜, 결제일 + 30일).
--   · 서비스 제공기간이 끝나면 남은 이용권은 소멸하고, 같은 수만큼 쿠폰이 나온다.
--     쿠폰 기간 = 서비스 종료 다음 날 ~ 유효기간 마지막 날. 쿠폰은 이용권과 똑같이 예약에 쓰고 환불은 안 된다.
--   · 소멸 이후 예약을 취소하면 그 1회는 쿠폰으로 돌려준다.
--   · 환불 기간 = 서비스 제공기간.
--
-- 쿠폰은 erp_bookstore_pass 에 source='COUPON', ref_no=원래 pass_id 인 행으로 들어간다.
-- 그래서 쿠폰용 컬럼은 따로 없고, 서비스 제공기간과 전환 시각 두 컬럼만 늘어난다.
--
-- ddl-payment.sql / schema.sql 도 같은 내용으로 수정했다.
-- GO 없이 문장마다 ; 로 끝난다 — DBeaver에서는 Alt+X(스크립트 실행)로 한 문장씩 실행한다.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'service_until')
    ALTER TABLE erp_bookstore_pass ADD service_until DATE NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'converted_at')
    ALTER TABLE erp_bookstore_pass ADD converted_at DATETIME2 NULL;

-- 기존 앱 결제분에도 서비스 제공기간을 채운다(아직 실구매가 없어 소급해도 영향이 없다 — 2026-10-27 확인).
-- 서당/무상분은 NULL로 둔다 — 그쪽은 달력 월 기간이 곧 사용 기간이다.
UPDATE erp_bookstore_pass
SET service_until = DATEADD(DAY, 90, CAST(granted_at AS DATE))
WHERE source = 'PG'
  AND service_until IS NULL;

-- 전환 배치가 매일 "서비스 기간이 끝났는데 아직 전환 안 된" 이용권을 찾는 경로
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_service_until' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    CREATE INDEX IX_pass_service_until ON erp_bookstore_pass (service_until)
        WHERE converted_at IS NULL AND service_until IS NOT NULL;

-- 확인
-- SELECT pass_id, source, granted_at, service_until, valid_from, valid_until, remain_count, converted_at
-- FROM erp_bookstore_pass ORDER BY pass_id DESC;
