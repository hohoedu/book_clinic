-- ===================== 최초 1회만 수동 실행 (DBeaver 등). schema.sql/data.sql 자동 실행 대상에서 제외 =====================
-- 권장도서 순위(erp_bookstore_priority_draft / erp_bookstore_priority) 초기 시딩.
-- 화면(데이터 > 권장도서 순위)에서 편집·저장한 순위가 재기동 때 날아가지 않도록 priority 계열은 리셋 제외 그룹으로 뺐다 (2026-09-02).
-- schema.sql은 이 테이블들을 DROP 하지 않고 IF OBJECT_ID ... IS NULL 로만 생성하므로, 데이터가 필요하면 이 파일을 한 번 실행한다.
-- 이미 순위가 들어 있는 DB에서는 실행하지 말 것 (중복 INSERT). 처음 구축하는 DB에서만 실행.
--
-- 정렬 규칙 확정 (2026-07-31). ClinicService.pickNextItem이 이 sort_order를 그대로
-- 훑으며 추천하므로, "어떤 순서로 추천할지"는 추천 코드가 아니라 이 정렬이 결정한다.
--   1) 학년: 초안(draft)이 학년별로 나뉘어 있어 c.schoolyear = d.schoolyear 조인으로 이미 걸러진다
--   2) 난이도: 하 → 중 → 상. content.difficulty는 코드가 아니라 자유 입력 텍스트라(도서 관리 화면에서
--      직접 타이핑) 공백을 털어내고 비교하고, 값이 비어 있으면 중간(중)으로 취급한다
--   3) 분류: 같은 난이도 구간 안에서 분류(content_type)가 연달아 나오지 않게 라운드로빈으로 섞는다.
--      분류별로 번호를 매긴 뒤(type_seq) 그 번호 순으로 뽑으면 "각 분류의 1번째들 → 2번째들 → ..."이
--      되어 분류가 자연히 번갈아 나온다. 어떤 분류의 책이 유독 많으면 뒤쪽엔 그 분류만 남는데,
--      남은 게 그것뿐이라 이건 피할 수 없다.

-- 권장도서 순위 초안: 올해 + 초1~초5, 활성 상태 (초6/중등은 아직 시딩하지 않는다)
INSERT INTO erp_bookstore_priority_draft (year, schoolyear, is_active, created_by) VALUES
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '01', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '02', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '03', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '04', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '05', 'Y', 'seed');

WITH graded AS (
    SELECT c.content_id,
           c.schoolyear,
           c.content_type,
           CASE LTRIM(RTRIM(c.difficulty))
               WHEN N'하' THEN 1
               WHEN N'상' THEN 3
               ELSE 2                      -- '중' 그리고 미입력(NULL/공백)
           END AS diff_rank
    FROM erp_bookstore_content c
    WHERE c.state = 'Y'
), spread AS (
    SELECT g.content_id,
           g.schoolyear,
           g.content_type,
           g.diff_rank,
           ROW_NUMBER() OVER (
               PARTITION BY g.schoolyear, g.diff_rank, g.content_type
               ORDER BY g.content_id
           ) AS type_seq
    FROM graded g
)
INSERT INTO erp_bookstore_priority (draft_id, content_id, sort_order)
SELECT d.draft_id,
       s.content_id,
       ROW_NUMBER() OVER (
           PARTITION BY d.draft_id
           ORDER BY s.diff_rank, s.type_seq, s.content_type, s.content_id
       )
FROM erp_bookstore_priority_draft d
JOIN spread s ON s.schoolyear = d.schoolyear
WHERE d.schoolyear IN ('01','02','03','04','05')
  AND d.year = CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4));
