-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 서당 병행 센터 구분 (2026-11-19)
--
-- erp_center.with_seodang = 1 이면 서당+책방을 함께 하는 센터다.
--   · 운영스케줄(예약 가능 회차)을 달 단위로 연다 — 전월 19일에 다음 달 1일~말일이 열린다
--   · 0(기본)이면 책방만 하는 센터 — 지금처럼 오늘부터 30일치를 매일 굴린다
-- 값은 센터별로 직접 UPDATE 한다(아래 예시). 바꾼 뒤엔 서버 재기동 또는 다음 03:10 배치 때 반영된다.
--
-- 여러 번 실행해도 안전하다. GO 없이 문장마다 ; — DBeaver에서 Alt+X로 실행.
-- ════════════════════════════════════════════════════════════════════

IF COL_LENGTH('erp_center', 'with_seodang') IS NULL
    ALTER TABLE erp_center ADD with_seodang BIT NOT NULL CONSTRAINT DF_erp_center_with_seodang DEFAULT 0;

-- 예시: 서당 병행 센터 지정
-- UPDATE erp_center SET with_seodang = 1 WHERE center_code IN ('PUS002');

SELECT center_code, center_name, with_seodang FROM erp_center ORDER BY center_code;
