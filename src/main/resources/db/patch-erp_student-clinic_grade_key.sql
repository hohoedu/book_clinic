-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_student.clinic_grade_key 컬럼 추가 (2026-08-07)
--
-- erp_student.grade_key는 올패스(외부 학생 마스터)와 공유하는 값이라 book_clinic이 함부로
-- 못 건드린다. 그런데 "초1인데 독서를 잘해서 초2 수준 책을 추천받고 싶다"처럼 실제 학년은
-- 그대로 두고 클리닉 추천 기준만 조정해야 하는 경우가 있어, 별도 컬럼으로 분리했다.
-- NULL이면 ClinicService.resolveSchoolyear()가 grade_key(올패스 코드)를 book_clinic 코드로
-- 변환해 최초 1회 채워넣는다(lazy init) — 이후로는 grade_key가 바뀌어도 이 값은 자동으로
-- 안 따라간다. IF NOT EXISTS로 감싸 여러 번 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_student') AND name = 'clinic_grade_key')
    ALTER TABLE erp_student ADD clinic_grade_key VARCHAR(2);
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
