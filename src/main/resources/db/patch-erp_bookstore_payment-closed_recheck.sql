-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_payment.closed_recheck_at 컬럼 추가 (2026-08-07)
--
-- 고객이 이니시스 결제창에서 딴짓하다 10분을 넘겨 우리 배치가 주문을 CLOSED로 닫았는데,
-- 그 직후 실제로 승인을 완료해버리는 레이스가 있을 수 있다(카드사엔 승인이 남는데 우리는
-- "종료된 결제"로 처리). PaymentCleanupJob이 최근 CLOSED된 주문을 한 번 더 거래조회해서
-- 실제로는 승인이었으면 PAID로 복구하고, 이 값을 채워 같은 주문을 계속 재확인하지 않게 한다.
-- IF NOT EXISTS로 감싸 여러 번 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'closed_recheck_at')
    ALTER TABLE erp_bookstore_payment ADD closed_recheck_at DATETIME2;
