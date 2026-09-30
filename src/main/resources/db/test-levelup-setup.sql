/*
  [테스트용] 레벨업 연출 확인 세팅 (2026-09-30)

  지정한 학생을 "다음 한 권만 완독하면 레벨업" 상태로 만든다. 실행 후 그 학생으로 로그인해
  책 추천 → 기본 문제 첫 제출을 하면 결과화면에서 레벨업 연출이 나온다.

  - 레벨 = (DONE 권수 전체(책 학년 무관) / 학생 독서학년의 필요권수) + 1
    필요권수는 ClinicService.LEVEL_RULES 와 같다: 초1·2=8, 초3=5, 초4~6=4
  - 모자란 만큼 학생 독서학년 도서를 DONE 행으로 끼워 넣는다(가짜 완독). 기존 행은 건드리지 않는다.
  - 넣은 DONE 행은 지우기 전까지 남는다(자동 리셋 없음) — 완독 권수·카드·이번 달 읽은 책에도 잡힌다.
  - 운영 DB에서는 실행하지 말 것.
*/
DECLARE @studentId VARCHAR(100) = '여기에_학생ID';

DECLARE @schoolyear VARCHAR(2) = (SELECT clinic_grade_key FROM erp_student WHERE student_id = @studentId);
DECLARE @perLevel INT = CASE @schoolyear WHEN '01' THEN 8 WHEN '02' THEN 8 WHEN '03' THEN 5
                                         WHEN '04' THEN 4 WHEN '05' THEN 4 WHEN '06' THEN 4 END;

IF @perLevel IS NULL
BEGIN
    -- clinic_grade_key 는 학생이 한 번 로그인하면 채워진다(ClinicService.resolveSchoolyear)
    RAISERROR('독서학년(clinic_grade_key)이 없거나 레벨 규칙이 없는 학년입니다: %s', 16, 1, @schoolyear);
    RETURN;
END

DECLARE @done INT = (
    SELECT COUNT(*)
    FROM erp_bookstore_recommend_log
    WHERE student_id = @studentId AND status = 'DONE' 
);
-- 레벨 구간 안에서 "한 권 모자란" 권수까지 채운다 (이미 그 상태면 0)
DECLARE @need INT = (@perLevel - 1) - (@done % @perLevel);

INSERT INTO erp_bookstore_recommend_log (student_id, content_id, item_id, status, completed_at)
SELECT TOP (@need) @studentId, c.content_id, i.item_id, 'DONE', DATEADD(HOUR, 9, GETUTCDATE())
FROM erp_bookstore_content c
CROSS APPLY (SELECT MIN(item_id) AS item_id FROM erp_bookstore_item WHERE content_id = c.content_id) i
WHERE c.schoolyear = @schoolyear
  AND i.item_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_recommend_log rl
                  WHERE rl.student_id = @studentId AND rl.content_id = c.content_id)
ORDER BY c.content_id DESC;

SELECT @schoolyear AS schoolyear,
       @perLevel   AS booksPerLevel,
       @done       AS doneBefore,
       @need       AS inserted,
       @done + @need AS doneNow,
       (@done + @need) / @perLevel + 1 AS levelNow,
       (@done + @need) / @perLevel + 2 AS levelAfterNextBook;
