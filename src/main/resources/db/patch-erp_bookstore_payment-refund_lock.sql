-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_payment.refund_requested_at 컬럼 추가 (2026-08-07)
--
-- 동시 환불 요청 경합(이중 환불) 방지용 선점 컬럼. "환불 안 됨"을 확인만 하고 PG 호출까지
-- 가는 사이 락이 없으면, 거의 동시에 두 번 요청됐을 때 PG 취소가 이중으로 나갈 수 있었다.
-- PaymentService.refund()가 원자적 UPDATE로 이 값을 채워 선점하고, 처리가 끝나면(성공/실패
-- 무관) 다시 NULL로 되돌린다. IF NOT EXISTS로 감싸 여러 번 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'closed_recheck_at')
    ALTER TABLE erp_bookstore_payment ADD closed_recheck_at DATETIME2;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'needs_review')
    ALTER TABLE erp_bookstore_payment ADD needs_review BIT NOT NULL DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'review_reason')
    ALTER TABLE erp_bookstore_payment ADD review_reason VARCHAR(200);
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'reviewed_at')
    ALTER TABLE erp_bookstore_payment ADD reviewed_at DATETIME2;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'refund_requested_at')
    ALTER TABLE erp_bookstore_payment ADD refund_requested_at DATETIME2;
