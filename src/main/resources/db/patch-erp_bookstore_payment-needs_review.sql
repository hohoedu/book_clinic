-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_payment.needs_review/review_reason/reviewed_at 컬럼 추가 (2026-08-07)
--
-- 금액 불일치·망취소 실패·승인 확정 실패처럼 코드가 스스로 못 끝내고 사람이 이니시스
-- 상점관리자에서 직접 확인해야 하는 상태를 표시하는 플래그. 예전엔 로그에만 남아서 아무도
-- 안 보면 그대로 묻혔다. 새 관리자 화면(/admin/payment/review-view)이 이 플래그가 선 건만
-- 모아 보여준다. IF NOT EXISTS로 감싸 여러 번 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'closed_recheck_at')
    ALTER TABLE erp_bookstore_payment ADD closed_recheck_at DATETIME2;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'needs_review')
    ALTER TABLE erp_bookstore_payment ADD needs_review BIT NOT NULL DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'review_reason')
    ALTER TABLE erp_bookstore_payment ADD review_reason VARCHAR(200);
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'reviewed_at')
    ALTER TABLE erp_bookstore_payment ADD reviewed_at DATETIME2;
