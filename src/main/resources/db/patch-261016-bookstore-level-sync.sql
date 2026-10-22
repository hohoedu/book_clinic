-- ============================================================
-- erp_bookstore_assign.bookstore_level 일괄 보정 (2026-10-16)
--   지금까지 이 컬럼은 가입(1)·수강정보 저장 때만 찍혀서, 완독으로 레벨이 올라도 그대로였다.
--   (학생 목록은 실시간 계산이라 Lv.2, 수강 정보 탭은 Lv.1 처럼 어긋남)
--   앞으로는 완독 시 ClinicService 가 갱신하지만, 이미 어긋난 기존 행은 이 스크립트로 한 번 맞춘다.
--
--   아래 계산은 ClinicService 의 규칙을 그대로 옮긴 것 — 규칙이 바뀌면 이 파일은 다시 쓰지 말 것(1회용).
--     학년(schoolyear) = erp_student.clinic_grade_key, 없으면 grade_key 를 OLPASS_GRADE_TO_SCHOOLYEAR 로 변환,
--                        매핑에 없으면 grade_key 원본, 그것도 없으면 '01'   (resolveSchoolyear)
--     레벨당 필요권수  = 01·02=8, 03=5, 04~06=4, 그 외(07 등)는 규칙 없음 → 레벨 1   (LEVEL_RULES)
--     레벨            = MIN(12, 완독권수 / 필요권수 + 1), 완독 0권이면 1             (levelFor, MAX_LEVEL)
--     완독권수        = erp_bookstore_recommend_log status='DONE' 전체                (countDoneBooks)
--
-- 1) 확인 SELECT 로 바뀔 행을 보고, 2) 맞으면 UPDATE 주석을 풀어 실행한다.
-- 두 문장은 같은 계산(WITH calc)을 각자 들고 있다 — 하나만 고치지 말 것.
-- ============================================================

-- 1) 확인용
WITH calc AS (
    SELECT a.student_id,
           a.bookstore_level AS old_level,
           CASE
               WHEN r.books_per_level IS NULL OR ISNULL(d.done_books, 0) <= 0 THEN 1
               WHEN d.done_books / r.books_per_level + 1 > 12 THEN 12
               ELSE d.done_books / r.books_per_level + 1
           END AS new_level,
           y.schoolyear,
           ISNULL(d.done_books, 0) AS done_books
    FROM erp_bookstore_assign a
    OUTER APPLY (SELECT TOP 1 s.clinic_grade_key, s.grade_key
                 FROM erp_student s
                 WHERE s.student_id = a.student_id) s
    CROSS APPLY (SELECT COALESCE(
                     NULLIF(s.clinic_grade_key, ''),
                     CASE s.grade_key
                         WHEN '05' THEN '01' WHEN '06' THEN '01' WHEN '07' THEN '01'
                         WHEN '11' THEN '01' WHEN '12' THEN '02' WHEN '13' THEN '03'
                         WHEN '14' THEN '04' WHEN '15' THEN '05' WHEN '16' THEN '06'
                         WHEN '21' THEN '07' WHEN '22' THEN '07' WHEN '23' THEN '07'
                     END,
                     NULLIF(s.grade_key, ''),
                     '01') AS schoolyear) y
    OUTER APPLY (SELECT CASE y.schoolyear
                            WHEN '01' THEN 8 WHEN '02' THEN 8 WHEN '03' THEN 5
                            WHEN '04' THEN 4 WHEN '05' THEN 4 WHEN '06' THEN 4
                        END AS books_per_level) r
    OUTER APPLY (SELECT COUNT(*) AS done_books
                 FROM erp_bookstore_recommend_log l
                 WHERE l.student_id = a.student_id
                   AND l.status = 'DONE') d
)
SELECT student_id, schoolyear, done_books, old_level, new_level
FROM calc
WHERE old_level IS NULL OR old_level <> new_level
ORDER BY student_id;

-- 2) 실행용 — 위 결과를 확인한 뒤 주석을 풀고 실행
/*
WITH calc AS (
    SELECT a.student_id,
           CASE
               WHEN r.books_per_level IS NULL OR ISNULL(d.done_books, 0) <= 0 THEN 1
               WHEN d.done_books / r.books_per_level + 1 > 12 THEN 12
               ELSE d.done_books / r.books_per_level + 1
           END AS new_level
    FROM erp_bookstore_assign a
    OUTER APPLY (SELECT TOP 1 s.clinic_grade_key, s.grade_key
                 FROM erp_student s
                 WHERE s.student_id = a.student_id) s
    CROSS APPLY (SELECT COALESCE(
                     NULLIF(s.clinic_grade_key, ''),
                     CASE s.grade_key
                         WHEN '05' THEN '01' WHEN '06' THEN '01' WHEN '07' THEN '01'
                         WHEN '11' THEN '01' WHEN '12' THEN '02' WHEN '13' THEN '03'
                         WHEN '14' THEN '04' WHEN '15' THEN '05' WHEN '16' THEN '06'
                         WHEN '21' THEN '07' WHEN '22' THEN '07' WHEN '23' THEN '07'
                     END,
                     NULLIF(s.grade_key, ''),
                     '01') AS schoolyear) y
    OUTER APPLY (SELECT CASE y.schoolyear
                            WHEN '01' THEN 8 WHEN '02' THEN 8 WHEN '03' THEN 5
                            WHEN '04' THEN 4 WHEN '05' THEN 4 WHEN '06' THEN 4
                        END AS books_per_level) r
    OUTER APPLY (SELECT COUNT(*) AS done_books
                 FROM erp_bookstore_recommend_log l
                 WHERE l.student_id = a.student_id
                   AND l.status = 'DONE') d
)
UPDATE a
SET a.bookstore_level = c.new_level,
    a.updated_at      = DATEADD(HOUR, 9, GETUTCDATE())
FROM erp_bookstore_assign a
JOIN calc c ON c.student_id = a.student_id
WHERE a.bookstore_level IS NULL OR a.bookstore_level <> c.new_level;
*/
