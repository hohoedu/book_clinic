-- ============================================================
-- 전체 학생 책방 "미등록" 행 채우기 (2026-10-16)
--   등록 상태 규칙(StudentMapper.findStudentByCenterCode 주석):
--     1 = 이용중 / 0 + 시작일자 있음 = 탈퇴 / 0 + 시작일자 없음 = 미등록
--   책방 정식 운영(실제 가입) 전이라, erp_student 의 모든 학생에게 "0 + 시작일자 없음" 행을 만든다.
--   이후 책방을 시작하는 학생은 수강 정보 저장(all_pass 수강상태 저장 / book_clinic 수강 정보 탭)에서
--   수강 + 시작일자를 넣으면 이용중이 된다. 새 입회는 all_pass createBookstoreAssign 이 같은 값으로 만든다.
--
--   - erp_student.student_id 는 UNIQUE 가 아니라 DISTINCT 로 1명 1행만 만든다.
--   - 이미 erp_bookstore_assign 행이 있는 학생은 건드리지 않는다(UX_bookstore_assign_student 유니크).
--     테스트 가입 등으로 이미 있는 행은 아래 2) 로 따로 확인해서, 필요하면 직접 정리한다.
--
-- 1)~2) 로 대상을 확인하고, 맞으면 3) INSERT 주석을 풀어 실행한다.
-- ============================================================

-- 1) 확인용 — 대상(책방 행이 아직 없는 학생) 인원과 목록
SELECT COUNT(DISTINCT s.student_id) AS target_count
FROM erp_student s
WHERE s.student_id IS NOT NULL
  AND s.student_id <> ''
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_assign ba WHERE ba.student_id = s.student_id);

SELECT s.student_id, s.student_name, s.center_code, s.grade_key, s.status_key
FROM erp_student s
WHERE s.student_id IS NOT NULL
  AND s.student_id <> ''
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_assign ba WHERE ba.student_id = s.student_id)
ORDER BY s.center_code, s.student_name;

-- 2) 확인용 — 이미 행이 있어 이번에 건너뛰는 학생 (테스트 가입 데이터인지 확인)
SELECT ba.student_id, s.student_name, ba.bookstore_state,
       CONVERT(VARCHAR(10), ba.entry_bookstore_date, 23) AS entry_date, ba.created_at
FROM erp_bookstore_assign ba
OUTER APPLY (SELECT TOP 1 student_name FROM erp_student WHERE student_id = ba.student_id) s
ORDER BY ba.created_at;

-- 3) 실행용 — 위 결과를 확인한 뒤 주석을 풀고 실행 (all_pass createBookstoreAssign 과 같은 값)
/*
INSERT INTO erp_bookstore_assign (student_id, bookstore_state, bookstore_level)
SELECT DISTINCT s.student_id, 0, 1
FROM erp_student s
WHERE s.student_id IS NOT NULL
  AND s.student_id <> ''
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_assign ba WHERE ba.student_id = s.student_id);
*/
