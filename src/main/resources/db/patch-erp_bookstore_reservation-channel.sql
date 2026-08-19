-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_reservation.channel 컬럼 추가 (2026-08-19)
--
-- 예약 현황 화면의 "직접 예약"/"센터 예약" 표시를 reservation_log의 changed_by_role을
-- 매번 서브쿼리로 찾아 판정했더니, 이미 만들어진 테이블엔 그 값이 반영되지 않는 문제가
-- 있었다. channel 컬럼을 예약 row에 직접 저장해 생성 시점에 고정한다. IF NOT EXISTS로
-- 감싸 여러 번 실행해도 안전하다. 기존 행은 전부 STUDENT(직접 예약)로 채워진다.
-- ════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_reservation') AND name = 'channel')
    ALTER TABLE erp_bookstore_reservation ADD channel VARCHAR(20) NOT NULL DEFAULT 'STUDENT';

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_reservation_channel')
    ALTER TABLE erp_bookstore_reservation
        ADD CONSTRAINT CK_reservation_channel CHECK (channel IN ('STUDENT', 'PARENT', 'ADMIN', 'SYSTEM'));
