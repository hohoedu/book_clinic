-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 이용권 차감을 "입실일당 1회" → "그날 예약 회차(타임) 수만큼"으로 (2026-08-28)
--
-- 학생 앱 정책 변경(하루 최대 4회차 예약)에 맞춰, 입실 시 그날 출석 확정된 회차 수만큼
-- erp_bookstore_pass_use 에 행을 남기고 remain_count 를 그만큼 깐다.
--
-- 하루 여러 행이 생기므로 UNIQUE (student_id, used_date) 제약을 제거한다.
-- 재입실 이중차감은 PassService.consume 이 "그날 목표 차감수 − 이미 차감한 수"만큼만
-- 채우는 방식으로 막는다("행 수 = remain_count 감소량" 불변식은 그대로 유지 — 1행 = 1차감).
--
-- 환불 로직(PaymentService.usedCount ↔ rule.maxCount, payment.used_count 스냅샷)은
-- pass_use 행 수(COUNT(*))에 그대로 의존한다 — 이제 그 값이 실제 차감 횟수라 정확하다.
-- schema.sql 은 이 패치와 함께 최신 구조로 수정했다.
-- ════════════════════════════════════════════════════════════════════

-- 자동 생성명일 수 있어 컬럼 기준으로도 한 번 더 찾아 지운다.
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_pass_use_daily' AND parent_object_id = OBJECT_ID('erp_bookstore_pass_use'))
    ALTER TABLE erp_bookstore_pass_use DROP CONSTRAINT UQ_pass_use_daily;
GO

DECLARE @uq SYSNAME;
SELECT @uq = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('erp_bookstore_pass_use')
  AND kc.type = 'UQ'
  AND c.name IN ('student_id', 'used_date')
GROUP BY kc.name
HAVING COUNT(DISTINCT c.name) = 2;
IF @uq IS NOT NULL
    EXEC('ALTER TABLE erp_bookstore_pass_use DROP CONSTRAINT ' + @uq);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_use_student_date' AND object_id = OBJECT_ID('erp_bookstore_pass_use'))
    CREATE INDEX IX_pass_use_student_date ON erp_bookstore_pass_use (student_id, used_date);
GO
