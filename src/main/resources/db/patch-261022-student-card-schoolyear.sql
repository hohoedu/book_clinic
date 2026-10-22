/*
  [패치] 스페셜 카드 학년별 지급 (2026-10-22)

  정책 변경: 스페셜 카드를 "평생 누적 10장마다"가 아니라 "학년별 목표 권수를 10칸으로 나눠 칸마다" 준다.
    초1·2 = 96권 → 10, 20, … 90, 96 / 초3 = 60권 → 6, 12, … 60 / 초4~6 = 50권 → 5, 10, … 50
    학년이 오르면 0부터 다시 세고, 목표 권수를 채우면 그 학년에는 더 주지 않는다(ClinicService / SpecialCardPolicy).

  DB 변경
    1) erp_bookstore_student_card.schoolyear 추가 — 카드를 받은 때의 학생 학년. 학년마다 0부터 세려면
       카드마다 "어느 학년 때 받았는지"가 있어야 한다(책 학년으로 세면 위 학년 책을 받은 경우가 틀어진다).
    2) SPECIAL 카드에 content_id(칸을 채운 그 책)를 남긴다 — 결과 화면·모니터링이 "이 책으로 스페셜을
       받았는지"를 순번을 다시 세지 않고 바로 찾는다.
    3) SPECIAL 중복 방지 인덱스를 (학생, trigger_count) → (학생, 학년, trigger_count)로 바꾼다 —
       학년마다 다시 세면 3학년 6장째와 4학년 5장째처럼 같은 장수가 학년만 달리해 또 나온다.

  기존 데이터 채우기
    · 지금까지의 카드는 전부 이번 학년도(2026-08 운영 시작 이후)에 받은 것이라, 학생의 현재 학년
      (erp_student.clinic_grade_key)으로 채운다. 학년이 비어 있으면 NORMAL은 그 책 학년으로 채운다.
    · 기존 SPECIAL 카드의 content_id는 옛 규칙대로 "trigger_count번째 NORMAL 카드"의 책으로 채운다.
    · 이미 나간 스페셜 카드는 지우지도, 새 규칙으로 소급 지급하지도 않는다. 예: 4학년이 10장으로 받은
      스페셜 1장은 그대로 두고, 새 규칙의 5장째 칸은 이미 지나가서 따로 주지 않는다.

  배포 순서: 이 패치를 먼저 실행하고 새 코드를 배포한다. 새 코드는 schoolyear 컬럼에 쓰고 읽는다.
  재실행 안전: 컬럼·인덱스는 존재 여부를 보고, UPDATE는 비어 있는 값만 채운다.
*/

-- 1) 컬럼 추가 (ALTER는 같은 배치에서 새 컬럼을 참조할 수 없어 GO로 나눈다)
IF COL_LENGTH('erp_bookstore_student_card', 'schoolyear') IS NULL
    ALTER TABLE erp_bookstore_student_card ADD schoolyear VARCHAR(2) NULL;
GO

BEGIN TRANSACTION;

-- 2) NORMAL 학년 채우기 — erp_student.student_id가 유니크가 아니라 TOP 1로 붙인다
UPDATE sc
SET sc.schoolyear = COALESCE(NULLIF(st.clinic_grade_key, ''), c.schoolyear)
FROM erp_bookstore_student_card sc
OUTER APPLY (SELECT TOP 1 clinic_grade_key FROM erp_student WHERE student_id = sc.student_id) st
LEFT JOIN erp_bookstore_content c ON c.content_id = sc.content_id
WHERE sc.card_type = 'NORMAL' AND sc.schoolyear IS NULL;

-- 3) SPECIAL 학년 + 칸을 채운 책 채우기 — 옛 규칙: trigger_count번째(획득순) NORMAL 카드의 책
;WITH ranked AS (
    SELECT student_id, content_id, schoolyear,
           ROW_NUMBER() OVER (PARTITION BY student_id ORDER BY earned_at, id) AS seq
    FROM erp_bookstore_student_card
    WHERE card_type = 'NORMAL'
)
UPDATE sp
SET sp.content_id = COALESCE(sp.content_id, r.content_id),
    sp.schoolyear = COALESCE(sp.schoolyear, NULLIF(st.clinic_grade_key, ''), r.schoolyear)
FROM erp_bookstore_student_card sp
OUTER APPLY (SELECT TOP 1 clinic_grade_key FROM erp_student WHERE student_id = sp.student_id) st
LEFT JOIN ranked r ON r.student_id = sp.student_id AND r.seq = sp.trigger_count
WHERE sp.card_type = 'SPECIAL' AND (sp.schoolyear IS NULL OR sp.content_id IS NULL);

-- 4) SPECIAL 중복 방지 인덱스 교체
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_special' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    DROP INDEX UX_erp_bookstore_student_card_special ON erp_bookstore_student_card;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_special_grade' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    CREATE UNIQUE INDEX UX_erp_bookstore_student_card_special_grade
        ON erp_bookstore_student_card (student_id, schoolyear, trigger_count)
        WHERE card_type = 'SPECIAL';

COMMIT;
GO

-- 확인 1: 학년이 비어 있는 카드 — 0건이어야 한다. 남으면 그 학생의 clinic_grade_key를 먼저 채우고 다시 실행
SELECT card_type, COUNT(*) AS empty_schoolyear
FROM erp_bookstore_student_card
WHERE schoolyear IS NULL
GROUP BY card_type;

-- 확인 2: 책을 찾지 못한 SPECIAL 카드 — 옛 규칙의 근거 NORMAL 카드가 지워진 경우. 결과 화면·모니터링의
-- "이 책으로 받은 스페셜" 표시만 안 될 뿐 카드 컬렉션에는 그대로 보인다
SELECT student_id, trigger_count, schoolyear, earned_at
FROM erp_bookstore_student_card
WHERE card_type = 'SPECIAL' AND content_id IS NULL;

-- 확인 3: 학생·학년별 카드 현황
SELECT student_id, schoolyear,
       SUM(CASE WHEN card_type = 'NORMAL'  THEN 1 ELSE 0 END) AS normal_cards,
       SUM(CASE WHEN card_type = 'SPECIAL' THEN 1 ELSE 0 END) AS special_cards
FROM erp_bookstore_student_card
GROUP BY student_id, schoolyear
ORDER BY student_id, schoolyear;
