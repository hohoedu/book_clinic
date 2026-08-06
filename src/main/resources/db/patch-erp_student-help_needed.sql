-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_student.help_needed 컬럼 추가 (2026-08-06)
--
-- erp_student는 운영에 실데이터가 있어 배포 작업에서 손대지 않았는데, 2026-07-30에
-- 추가된 help_needed(도움 필요 상태값) 컬럼이 운영 테이블엔 없어서 모니터링 화면이
-- "Invalid column name 'help_needed'" 에러로 죽었다. IF NOT EXISTS로 감싸 여러 번
-- 실행해도 안전하다. NOT NULL DEFAULT 0이라 기존 행은 전부 0(도움 필요 없음)으로
-- 자동 채워진다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_student') AND name = 'help_needed')
    ALTER TABLE erp_student ADD help_needed BIT NOT NULL DEFAULT 0;
