-- =====================================================================
-- 미운영(CLOSED) 예외에 대체 운영일 추가 (2026-11-23)
--
-- 12/19가 원래 운영일인데 행사로 미운영이 되고 12/20에 대신 여는 경우, 반복 예약이 19일을
-- 빼기만 하고 20일로 옮기지 못했다. 미운영 예외에 대체 운영일(선택)을 적어두면 반복 예약
-- 미리보기가 그 주는 대체일의 같은 회차로 잡는다.
--
-- 이미 테이블이 만들어진 DB(로컬/운영)에서 한 번 실행한다.
-- schema.sql / ddl-schedule.sql은 함께 수정돼 있어 새로 만드는 DB에는 필요 없다.
-- =====================================================================

IF COL_LENGTH('erp_bookstore_schedule_exception', 'substitute_date') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception ADD substitute_date DATE;

IF COL_LENGTH('erp_bookstore_schedule_exception_del', 'substitute_date') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception_del ADD substitute_date DATE;
GO
