-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — "하루 한 회차" → "하루 두 회차" 정책 변경 (2026-08-28)
--
-- 학생 앱 프로세스 정책 변경:
--   1) 하루 2회차까지 예약 가능 (기존: 1회차)
--   2) 입실/퇴실은 여전히 1일 1회 — 첫 입실 시 그날 예약 전체를 ATTENDED로 전환
--      (두 번째 회차는 시간대가 아직 아니어도 첫 입실로 출석 인정)
--   3) 책 추천 상한: 회차당 2권 → 그날 출석(ATTENDED) 회차 수 × 2권
--
-- (student_id, service_date) 유니크 인덱스로는 "2건까지"를 표현할 수 없어 제거한다.
-- 하루 2회차 상한은 이제 응용 계층에서 강제한다:
--   ReservationService.reserveOne 이 erp_student 행을 UPDLOCK 으로 잡아 같은 학생의
--   동시 예약 요청을 직렬화한 뒤, countOtherReservedOnDate >= 2 이면 차단한다.
-- 같은 회차 중복 예약은 UX_reservation_slot_student(그대로 유지)가 계속 막는다.
--
-- service_date 컬럼은 그대로 둔다 — countOtherReservedOnDate 판정에 계속 쓴다.
-- ddl-schedule.sql / schema.sql 은 이 패치와 함께 최신 구조로 수정했다.
-- ════════════════════════════════════════════════════════════════════

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_student_date' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    DROP INDEX UX_reservation_student_date ON erp_bookstore_reservation;
GO
