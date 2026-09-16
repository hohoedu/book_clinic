-- ════════════════════════════════════════════════════════════════════
-- 도서 마스터(erp_bookstore_content) 1~3학년 최신화 — content_id 보존형 (2026-09-16)
--
-- [범위] schoolyear '01'(초1) / '02'(초2) / '03'(초3) 만 대상.
--        초4~초6(175권)은 조회도 수정도 하지 않는다.
--
-- [처리 방식] 제목+저자로 기존 도서와 대조해서 세 갈래
--   - 양쪽에 있음 (유지) → UPDATE      : content_id 유지 → 문제은행·실물도서·학생이력 그대로 살아있음
--   - 새 목록에만 있음   → INSERT      : 새 content_id 채번
--   - 기존에만 있음      → state='N'   : DELETE 금지 (FK가 막고, 학생 이력이 깨진다)
--
-- [매칭 범위 주의] 중복 등록을 막으려고 매칭은 전 학년을 대상으로 한다.
--   초4 도서가 새 1~3학년 목록에 들어 있으면 그 행을 UPDATE 해서 학년을 옮긴다
--   (새로 INSERT 하면 같은 책이 두 건이 된다).
--   반면 state='N' 처리는 초1~초3 안에서만 한다 → 초4~초6은 절대 안 건드린다.
--
-- [실행] 0) 백업 → 1) 스테이징 적재 → 2) 사전 대조 → 3) 반영 → 4) 검증
--        2)번까지는 DB가 전혀 바뀌지 않는다. 3)번만 트랜잭션으로 묶여 있다.
-- ════════════════════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────────
-- 0) 백업 — 되돌릴 유일한 수단
-- ───────────────────────────────────────────────────────────
DECLARE @stamp VARCHAR(8) = CONVERT(VARCHAR(8), GETDATE(), 112);
DECLARE @sql NVARCHAR(MAX) =
    N'SELECT * INTO erp_bookstore_content_bak_' + @stamp + N' FROM erp_bookstore_content;' +
    N'SELECT * INTO erp_bookstore_content_detail_bak_' + @stamp + N' FROM erp_bookstore_content_detail;';
EXEC sp_executesql @sql;
PRINT '백업 완료: erp_bookstore_content_bak_' + @stamp;
GO

-- ───────────────────────────────────────────────────────────
-- 1) 스테이징 — 새 1~3학년 목록(96+60+60=216권)을 여기에 넣는다
--    (a) DBeaver: 테이블 우클릭 → Import Data → 엑셀/CSV
--    (b) 또는 INSERT 문 직접 작성
-- ───────────────────────────────────────────────────────────
IF OBJECT_ID('erp_bookstore_content_stage', 'U') IS NOT NULL
    DROP TABLE erp_bookstore_content_stage;
GO

CREATE TABLE erp_bookstore_content_stage (
    original_title VARCHAR(255) NOT NULL,  -- 매칭 키 1
    author         VARCHAR(100),           -- 매칭 키 2
    genre          VARCHAR(2),             -- erp_bookstore_code gubun='G'
    content_type   VARCHAR(2),             -- erp_bookstore_code gubun='C'
    schoolyear     VARCHAR(2),             -- '01'/'02'/'03' 만 들어와야 한다
    summary        VARCHAR(2000),
    keywords       VARCHAR(1000),
    state          VARCHAR(20)  DEFAULT 'Y',
    publisher      VARCHAR(100),
    image_url      VARCHAR(500),
    reading_time   VARCHAR(20),
    difficulty     VARCHAR(20)
);
GO

-- ★ 여기에 새 도서 목록 적재 ★

-- ───────────────────────────────────────────────────────────
-- 2) 사전 대조 — 반영 전 확인. DB는 바뀌지 않는다.
-- ───────────────────────────────────────────────────────────

-- 2-A) 스테이징 자체 검증 — 학년이 01~03이 아닌 행이 있으면 먼저 고칠 것
SELECT '스테이징 학년 분포' AS 구분, schoolyear, COUNT(*) AS 건수,
       CASE WHEN schoolyear IN ('01','02','03') THEN 'OK' ELSE '범위밖 — 확인 필요' END AS 판정
FROM erp_bookstore_content_stage
GROUP BY schoolyear
ORDER BY schoolyear;

-- 2-B) 코드 테이블에 없는 코드값 색출 (있으면 화면에 빈칸으로 보인다)
SELECT DISTINCT '장르' AS 구분, s.genre AS 잘못된_코드
FROM erp_bookstore_content_stage s
WHERE s.genre IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_code c WHERE c.gubun = 'G' AND c.code = s.genre)
UNION ALL
SELECT DISTINCT '분류', s.content_type
FROM erp_bookstore_content_stage s
WHERE s.content_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_code c WHERE c.gubun = 'C' AND c.code = s.content_type)
UNION ALL
SELECT DISTINCT '학년', s.schoolyear
FROM erp_bookstore_content_stage s
WHERE s.schoolyear IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_code c WHERE c.gubun = 'S' AND c.code = s.schoolyear);

-- 2-C) 핵심 — 유지 / 추가 / 제외가 각각 몇 권인가
SELECT '유지(내용만 수정)' AS 구분, COUNT(*) AS 건수
FROM erp_bookstore_content_stage s
JOIN erp_bookstore_content c
  ON c.original_title = s.original_title
 AND ISNULL(c.author, '') = ISNULL(s.author, '')
UNION ALL
SELECT '신규 추가', COUNT(*)
FROM erp_bookstore_content_stage s
WHERE NOT EXISTS (
    SELECT 1 FROM erp_bookstore_content c
    WHERE c.original_title = s.original_title
      AND ISNULL(c.author, '') = ISNULL(s.author, ''))
UNION ALL
SELECT '제외(state=N 처리)', COUNT(*)
FROM erp_bookstore_content c
WHERE c.schoolyear IN ('01','02','03')     -- ← 초1~초3 한정
  AND c.state = 'Y'
  AND NOT EXISTS (
    SELECT 1 FROM erp_bookstore_content_stage s
    WHERE c.original_title = s.original_title
      AND ISNULL(c.author, '') = ISNULL(s.author, ''))
UNION ALL
SELECT '초4~초6(손대지 않음)', COUNT(*)
FROM erp_bookstore_content WHERE schoolyear IN ('04','05','06');

-- 2-D) 초4~초6 도서가 새 목록에 섞여 들어와 학년이 바뀌는 경우 — 있으면 눈으로 확인할 것
SELECT c.content_id, c.original_title, c.author,
       c.schoolyear AS 기존학년, s.schoolyear AS 새학년
FROM erp_bookstore_content c
JOIN erp_bookstore_content_stage s
  ON c.original_title = s.original_title
 AND ISNULL(c.author, '') = ISNULL(s.author, '')
WHERE c.schoolyear NOT IN ('01','02','03');

-- 2-E) 제외될 도서에 걸린 학생 활동 — 많으면 정말 빼도 되는지 재확인
SELECT c.content_id, c.original_title, c.author, c.schoolyear,
       (SELECT COUNT(*) FROM erp_bookstore_itempool        p WHERE p.content_id = c.content_id) AS 문제수,
       (SELECT COUNT(*) FROM erp_bookstore_quiz_answer_log q WHERE q.content_id = c.content_id) AS 퀴즈이력,
       (SELECT COUNT(*) FROM erp_bookstore_diary_detail    d WHERE d.content_id = c.content_id) AS 독서일기,
       (SELECT COUNT(*) FROM erp_bookstore_item            i WHERE i.content_id = c.content_id) AS 실물도서
FROM erp_bookstore_content c
WHERE c.schoolyear IN ('01','02','03')
  AND c.state = 'Y'
  AND NOT EXISTS (
    SELECT 1 FROM erp_bookstore_content_stage s
    WHERE c.original_title = s.original_title
      AND ISNULL(c.author, '') = ISNULL(s.author, ''))
ORDER BY 퀴즈이력 DESC, 독서일기 DESC, 문제수 DESC;

-- ───────────────────────────────────────────────────────────
-- 3) 반영 — 2-C 결과가 예상과 맞을 때만 실행. 전부 한 트랜잭션.
-- ───────────────────────────────────────────────────────────
BEGIN TRY
    BEGIN TRANSACTION;

    -- 3-1) 수정 전 스냅샷 (기존 로그 규약 그대로)
    INSERT INTO erp_bookstore_content_del
        (log_type, logged_by, content_id, original_title, author, genre, content_type,
         schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
    SELECT 'UPDATE', 'RELOAD-G1TO3', c.content_id, c.original_title, c.author, c.genre, c.content_type,
           c.schoolyear, c.summary, c.keywords, c.state, c.publisher, c.image_url, c.reading_time, c.difficulty
    FROM erp_bookstore_content c
    JOIN erp_bookstore_content_stage s
      ON c.original_title = s.original_title
     AND ISNULL(c.author, '') = ISNULL(s.author, '');

    -- 3-2) 유지되는 도서 수정 (content_id 유지)
    UPDATE c
       SET c.genre        = s.genre,
           c.content_type = s.content_type,
           c.schoolyear   = s.schoolyear,
           c.summary      = s.summary,
           c.keywords     = s.keywords,
           c.state        = ISNULL(s.state, 'Y'),
           c.publisher    = s.publisher,
           c.image_url    = s.image_url,
           c.reading_time = s.reading_time,
           c.difficulty   = s.difficulty
    FROM erp_bookstore_content c
    JOIN erp_bookstore_content_stage s
      ON c.original_title = s.original_title
     AND ISNULL(c.author, '') = ISNULL(s.author, '');

    -- 3-3) 신규 도서 등록
    INSERT INTO erp_bookstore_content
        (original_title, author, genre, content_type, schoolyear, summary,
         keywords, state, publisher, image_url, reading_time, difficulty)
    SELECT s.original_title, s.author, s.genre, s.content_type, s.schoolyear, s.summary,
           s.keywords, ISNULL(s.state, 'Y'), s.publisher, s.image_url, s.reading_time, s.difficulty
    FROM erp_bookstore_content_stage s
    WHERE NOT EXISTS (
        SELECT 1 FROM erp_bookstore_content c
        WHERE c.original_title = s.original_title
          AND ISNULL(c.author, '') = ISNULL(s.author, ''));

    -- 3-4) 새 목록에서 빠진 초1~초3 도서만 미사용 처리 (초4~초6은 제외)
    UPDATE c SET c.state = 'N'
    FROM erp_bookstore_content c
    WHERE c.schoolyear IN ('01','02','03')
      AND c.state = 'Y'
      AND NOT EXISTS (
        SELECT 1 FROM erp_bookstore_content_stage s
        WHERE c.original_title = s.original_title
          AND ISNULL(c.author, '') = ISNULL(s.author, ''));

    -- 3-5) 미사용 처리된 도서가 권장도서 순위에 남아 있으면 추천 로직이 죽은 책을 집는다 → 순위에서 제거
    DELETE p
    FROM erp_bookstore_priority p
    JOIN erp_bookstore_content c ON c.content_id = p.content_id
    WHERE c.state = 'N';

    COMMIT TRANSACTION;
    PRINT '반영 완료';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT '실패 — 전부 롤백됨: ' + ERROR_MESSAGE();
    THROW;
END CATCH;
GO

-- ───────────────────────────────────────────────────────────
-- 4) 검증
-- ───────────────────────────────────────────────────────────

-- 4-A) 학년별 사용중 권수 — 초1~초3은 새 목록 수(96/60/60), 초4~초6은 기존 그대로(58/58/59)여야 한다
SELECT schoolyear AS 학년,
       SUM(CASE WHEN state = 'Y' THEN 1 ELSE 0 END) AS 사용중,
       SUM(CASE WHEN state = 'N' THEN 1 ELSE 0 END) AS 미사용,
       COUNT(*) AS 전체
FROM erp_bookstore_content
GROUP BY schoolyear
ORDER BY schoolyear;

-- 4-B) 고아 참조 — 전부 0이어야 정상 (UPDATE만 했으므로 생길 수 없지만 확인)
SELECT 'content_detail 고아' AS 구분, COUNT(*) AS 건수 FROM erp_bookstore_content_detail d
  WHERE NOT EXISTS (SELECT 1 FROM erp_bookstore_content c WHERE c.content_id = d.content_id)
UNION ALL
SELECT 'itempool 고아', COUNT(*) FROM erp_bookstore_itempool p
  WHERE NOT EXISTS (SELECT 1 FROM erp_bookstore_content c WHERE c.content_id = p.content_id)
UNION ALL
SELECT 'item 고아', COUNT(*) FROM erp_bookstore_item i
  WHERE NOT EXISTS (SELECT 1 FROM erp_bookstore_content c WHERE c.content_id = i.content_id);

-- 4-C) 신규 추가된 도서 중 문제은행이 없는 것 — 여기 나오는 책은 문제를 새로 만들어야 한다
SELECT c.content_id, c.schoolyear, c.original_title, c.author
FROM erp_bookstore_content c
WHERE c.state = 'Y'
  AND c.schoolyear IN ('01','02','03')
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_itempool p WHERE p.content_id = c.content_id)
ORDER BY c.schoolyear, c.original_title;

-- 정리: 검증이 끝나면 스테이징은 지워도 된다 (백업 테이블은 남겨둘 것)
-- DROP TABLE erp_bookstore_content_stage;
