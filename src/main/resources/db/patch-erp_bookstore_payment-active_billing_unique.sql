-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 같은 학생·서비스·청구월 중복 결제 방지 (2026-08-07)
--
-- A기기로 결제창을 열어두고 방치한 사이 B기기로 새로 결제하면, 둘 다 정상적으로 승인돼서
-- 같은 달 결제가 이중으로 잡히고 이용권도 두 배로 발급되는 사고가 가능했다(발견 경위:
-- 실제 사용 중 "A로 결제하다 딴짓 → B로 결제 → A로 돌아와서 마저 결제"를 재현해봄).
-- service_code 스냅샷 컬럼 + 필터드 유니크 인덱스로 DB 레벨에서 최종 방어선을 건다.
--
-- [주의] 3번 인덱스 생성이 실패하면, 과거에 실제로 이 버그로 중복 결제가 발생해 있었다는
-- 뜻이다. 아래 조회로 중복 건을 먼저 찾아 수동으로 정리(환불 등)한 뒤 다시 실행해야 한다:
--   SELECT student_id, service_code, billing_ym, COUNT(*) AS 건수
--   FROM erp_bookstore_payment
--   WHERE status IN ('READY','PAID')
--   GROUP BY student_id, service_code, billing_ym
--   HAVING COUNT(*) > 1;
-- ════════════════════════════════════════════════════════════════════

-- 1) service_code 컬럼 추가
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'service_code')
    ALTER TABLE erp_bookstore_payment ADD service_code VARCHAR(10);

-- 2) 기존 행 백필 — product 조인으로 service_code를 채운다(이 컬럼 도입 이전 결제 건은 전부 NULL이므로)
UPDATE p
SET p.service_code = pr.service_code
FROM erp_bookstore_payment p
JOIN erp_bookstore_product pr ON pr.product_id = p.product_id
WHERE p.service_code IS NULL;

-- 3) 유니크 인덱스 생성 — 이미 배포된 운영 DB는 CREATE TABLE 시점에 이 인덱스가 없었으므로 별도로 만든다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_active_billing' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE UNIQUE INDEX UX_payment_active_billing ON erp_bookstore_payment (student_id, service_code, billing_ym)
        WHERE status IN ('READY', 'PAID');
