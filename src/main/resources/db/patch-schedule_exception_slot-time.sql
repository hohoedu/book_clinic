-- =====================================================================
-- 예외 회차에 시각 컬럼 추가 (2026-08-18)
--
-- 운영시간 변경(TIME_CHANGE) 예외에서 그날 회차를 관리자가 직접 확정할 수 있게 한다.
-- 그전에는 요일 템플릿 회차 중 새 운영시간 안에 들어가는 것만 남기는 방식이라
-- 운영시간을 늘리거나 다른 시간대로 옮기면 회차를 만들 방법이 없었다.
--
-- 이미 테이블이 만들어진 DB(로컬/운영)에서 한 번 실행한다.
-- schema.sql / ddl-schedule.sql은 CREATE TABLE 시점에 컬럼이 포함되도록 함께 수정돼 있어
-- 새로 만드는 DB에는 이 스크립트가 필요 없다.
-- =====================================================================

IF COL_LENGTH('erp_bookstore_schedule_exception_slot', 'start_time') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception_slot ADD start_time TIME(0);

IF COL_LENGTH('erp_bookstore_schedule_exception_slot', 'end_time') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception_slot ADD end_time TIME(0);
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_schedule_exc_slot_time')
    ALTER TABLE erp_bookstore_schedule_exception_slot
        ADD CONSTRAINT CK_schedule_exc_slot_time CHECK (
            start_time IS NULL OR end_time IS NULL OR start_time < end_time);
GO

IF COL_LENGTH('erp_bookstore_schedule_exception_slot_del', 'start_time') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception_slot_del ADD start_time TIME(0);

IF COL_LENGTH('erp_bookstore_schedule_exception_slot_del', 'end_time') IS NULL
    ALTER TABLE erp_bookstore_schedule_exception_slot_del ADD end_time TIME(0);
GO
