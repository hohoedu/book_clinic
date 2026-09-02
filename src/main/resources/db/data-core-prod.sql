-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 배포용 마스터 데이터 — 2026-08-06
--
-- [왜 이 파일이 따로 있나] src/main/resources/db/data.sql은 개발(dev) 전용이다.
-- 그 파일 자체 주석에도 "운영에는 data.sql 자체가 실행되지 않는다"고 적혀 있고,
-- 실제로 관리자 테스트 계정(erp_user), 가짜 테스트 학생 32명(erp_student),
-- 심지어 "app_password가 비어 있으면 전부 테스트 비밀번호로 바꾸는" UPDATE 문까지
-- 섞여 있다 — 그대로 운영에서 돌리면 실제 학생 계정을 건드릴 수 있어서 절대 안 된다.
--
-- 이 파일은 data.sql에서 "진짜 필요한 마스터/참조 데이터"만 골라낸 것이다:
--   - erp_bookstore_code(공통코드), erp_bookstore_attitude_code(독서태도코드),
--     erp_bookstore_level(레벨칭호), erp_bookstore_badge(뱃지마스터)
--   - 도서 난이도 자동 채움 + 권장도서 순위 초안/내용 생성
-- 가짜 테스트 계정/학생/예약/비밀번호 UPDATE는 전부 뺐다.
--
-- [실행 순서] erp_bookstore_content에 실제 도서 데이터가 이미 들어있어야 마지막
-- 난이도 채움/순위 생성 부분이 의미가 있다. 그러므로 순서는:
--   ddl-core.sql → ddl-payment.sql → data-books-prod.sql → data-itempool.sql →
--   data-items.sql → 이 파일(data-core-prod.sql)
--
-- [재실행 안전성] 코드/태도코드/레벨/뱃지 INSERT는 IF NOT EXISTS로 감싸서 중복
-- 실행해도 안전하다. 난이도 채움 UPDATE는 원래도 "값이 비어있는 책만" 채우므로
-- 재실행해도 사람이 고친 값을 덮어쓰지 않는다. 순위(priority) 생성은 "올해+해당
-- 학년"에 이미 초안이 있으면 건너뛴다.
-- ════════════════════════════════════════════════════════════════════

-- 공통 코드 (분류/장르/학년/문제유형/난이도단계)
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'C' AND code = '01')
INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES
('C',	'01',	'교과연계'),
('C',	'02',	'학년필독'),
('C',	'03',	'권장도서'),
('C',	'04',	'기관추천'),
('C',	'05',	'인증수상작'),
('G',	'01',	'창작'),
('G',	'02',	'명작'),
('G',	'03',	'전래'),
('G',	'04',	'고전'),
('G',	'05',	'외국창작'),
('G',	'06',	'환경'),
('G',	'07',	'신체동화'),
('G',	'08',	'과학동화'),
('G',	'09',	'인물'),
('G',	'10',	'동시지'),
('G',	'11',	'역사'),
('G',	'12',	'비문학(자연)'),
('G',	'13',	'비문학(예절)'),
('G',	'14',	'비문학(문화)'),
('G',	'15',	'비문학(경제)'),
('G',	'16',	'비문학(인문)'),
('G',	'17',	'비문학(과학)'),
('G',	'18',	'상식'),
('G',	'19',	'과학'),
('G',	'20',	'비문학(미술)'),
('G',	'21',	'비문학(철학)'),
('G',	'22',	'비문학(안전)'),
('G',	'23',	'비문학(국기)'),
('G',	'24',	'비문학(전통)'),
('G',	'25',	'비문학(환경)'),
('G',	'26',	'비문학'),
('G',	'27',	'만화'),
('G',	'28',	'한국단편'),
('G',	'29',	'한국소설'),
('G',	'30',	'교양'),
('G',	'31',	'국어'),
('G',	'32',	'사회'),
('G',	'33',	'시사'),
('S',	'01',	'초1'),
('S',	'02',	'초2'),
('S',	'03',	'초3'),
('S',	'04',	'초4'),
('S',	'05',	'초5'),
('S',	'06',	'초6'),
('S',	'07',	'중등'),
('T',	'01',	'이해'),
('T',	'02',	'표현'),
('T',	'03',	'논리'),
('T',	'04',	'사고'),
('T',	'05',	'감정'),
('T',	'06',	'어휘'),
('T',	'07',	'지식'),
('T',	'08',	'문법'),
('L',	'01',	'기본'),
('L',	'02',	'심화');

-- 독서태도 코드 마스터 — monitor-live.js 하드코딩 대체, 문구 수정은 이 테이블만 고치면 됨
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_attitude_code WHERE attitude_code = 'GOOD_POSTURE')
INSERT INTO erp_bookstore_attitude_code (attitude_code, attitude_name, use_yn, updated_by) VALUES
('GOOD_POSTURE',  N'바른 자세로 차분하게 정독했어요.', 1, 'seed'),
('SELF_DIRECTED', N'스스로 책 읽기를 끝까지 이어갔어요.', 1, 'seed'),
('LOW_FOCUS',     N'집중력이 자주 흐트러졌어요.', 1, 'seed'),
('RUSHED',        N'책장을 빠르게 넘기며 서둘러 읽었어요.', 1, 'seed'),
('DISTRACTED',    N'산만한 모습을 보였어요.', 1, 'seed');

-- 레벨 칭호 (단계=학년, 레벨 1~12) — Lv.12는 각 단계의 만렙 'GRADE{n} Master'
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_level WHERE schoolyear = '01' AND level_no = 1)
INSERT INTO erp_bookstore_level (schoolyear, level_no, title) VALUES
('01', 1,  N'독서 씨앗'),
('01', 2,  N'독서 새싹'),
('01', 3,  N'이야기 친구'),
('01', 4,  N'책벌레'),
('01', 5,  N'독서 탐험가'),
('01', 6,  N'생각 탐험가'),
('01', 7,  N'이야기 수집가'),
('01', 8,  N'독서 여행자'),
('01', 9,  N'상상 여행자'),
('01', 10, N'꿈꾸는 독서가'),
('01', 11, N'책방 탐험대장'),
('01', 12, N'GRADE1 Master'),
('02', 1,  N'지혜 수집가'),
('02', 2,  N'문장 연구가'),
('02', 3,  N'어휘 탐구자'),
('02', 4,  N'생각 설계자'),
('02', 5,  N'상상 모험가'),
('02', 6,  N'이야기 연구가'),
('02', 7,  N'독서 개척자'),
('02', 8,  N'지식 탐험가'),
('02', 9,  N'독서 연금술사'),
('02', 10, N'생각 마법사'),
('02', 11, N'지혜 수호자'),
('02', 12, N'GRADE2 Master'),
('03', 1,  N'질문 새싹'),
('03', 2,  N'호기심 탐정'),
('03', 3,  N'생각 수집가'),
('03', 4,  N'이야기 분석가'),
('03', 5,  N'논리 탐험가'),
('03', 6,  N'생각 건축가'),
('03', 7,  N'지식 항해사'),
('03', 8,  N'상상 설계자'),
('03', 9,  N'이야기 연구원'),
('03', 10, N'생각 연금술사'),
('03', 11, N'책방 전략가'),
('03', 12, N'GRADE3 Master'),
('04', 1,  N'지식 새싹'),
('04', 2,  N'탐구 견습생'),
('04', 3,  N'자료 수집가'),
('04', 4,  N'지식 탐정'),
('04', 5,  N'개념 사냥꾼'),
('04', 6,  N'논리 건축가'),
('04', 7,  N'탐구 항해사'),
('04', 8,  N'지식 설계자'),
('04', 9,  N'사고 연구원'),
('04', 10, N'지식 연금술사'),
('04', 11, N'책방 학자'),
('04', 12, N'GRADE4 Master'),
('05', 1,  N'어휘 새싹'),
('05', 2,  N'문장 견습생'),
('05', 3,  N'표현 수집가'),
('05', 4,  N'맥락 탐정'),
('05', 5,  N'어휘 사냥꾼'),
('05', 6,  N'문장 건축가'),
('05', 7,  N'문해 항해사'),
('05', 8,  N'논술 설계자'),
('05', 9,  N'비평 연구원'),
('05', 10, N'문해 연금술사'),
('05', 11, N'책방 현자'),
('05', 12, N'GRADE5 Master'),
('06', 1,  N'통찰 새싹'),
('06', 2,  N'사유 견습생'),
('06', 3,  N'관점 수집가'),
('06', 4,  N'진실 탐정'),
('06', 5,  N'통찰 사냥꾼'),
('06', 6,  N'사상 건축가'),
('06', 7,  N'지혜 항해사'),
('06', 8,  N'담론 설계자'),
('06', 9,  N'비평 대가'),
('06', 10, N'지혜 연금술사'),
('06', 11, N'책방 대현자'),
('06', 12, N'GRADE6 Master');

-- 뱃지 마스터 (1~4) — 2026-09-02 5종 → 4종. 구 1번 "참 잘했어요!"와 2번 "독서친구"를 "독서완료"로 합쳤다.
-- 이미 구 5종이 들어 있는 운영 DB는 이 스크립트가 건너뛰므로 patch-erp_bookstore_badge-4types.sql로 이관한다.
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_badge WHERE badge_id = 1)
INSERT INTO erp_bookstore_badge (badge_id, badge_name, badge_desc, category, threshold, param) VALUES
(1, N'독서완료', N'책을 읽고 문제풀이를 완료',     'BASIC_PASS',    1, NULL),
(2, N'독서왕',   N'책의 내용을 정확하게 이해',     'BASIC_PERFECT', 1, NULL),
(3, N'심화완료', N'한 단계 깊은 사고 활동에 도전', 'ADV_PASS',      1, NULL),
(4, N'심화왕',   N'어휘력과 문해력의 실력 증가',   'ADV_PERFECT',   1, NULL);

-- ────────────────────────────────────────────────────────
-- 난이도 자동 부여 — 도서 시드가 difficulty를 넣지 않아 전 권이 비어 있으면
-- 아래 순위 정렬(하→중→상)이 통째로 무의미해지므로 값을 채워준다.
-- 이미 값이 있는 책은 건드리지 않아 재실행해도 안전하다.
-- ────────────────────────────────────────────────────────
WITH weighted AS (
    SELECT content_id,
           NTILE(3) OVER (
               PARTITION BY schoolyear
               ORDER BY
                   CASE
                       WHEN genre IN ('01','03','05','07','10','27') THEN 1
                       WHEN genre IN ('02','06','08','09','13','18','19','22','23','24') THEN 2
                       ELSE 3
                   END,
                   content_id
           ) AS tier
    FROM erp_bookstore_content
    WHERE state = 'Y'
      AND (difficulty IS NULL OR LTRIM(RTRIM(difficulty)) = '')
)
UPDATE c
SET difficulty = CASE w.tier WHEN 1 THEN N'하' WHEN 2 THEN N'중' ELSE N'상' END
FROM erp_bookstore_content c
JOIN weighted w ON w.content_id = c.content_id;

-- 권장도서 순위 초안: 올해 + 초1~초5. 이미 올해분이 있으면 건너뛴다(재실행 안전)
IF NOT EXISTS (
    SELECT 1 FROM erp_bookstore_priority_draft
    WHERE year = CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)) AND schoolyear = '01'
)
INSERT INTO erp_bookstore_priority_draft (year, schoolyear, is_active, created_by) VALUES
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '01', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '02', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '03', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '04', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '05', 'Y', 'seed');

-- 순위 내용 — 학년별 난이도(하→중→상) 순으로, 같은 난이도 구간에서는 분류가 연달아 나오지
-- 않도록 라운드로빈으로 섞는다. 이미 순위가 채워진 초안(draft)은 다시 채우지 않는다.
WITH graded AS (
    SELECT c.content_id,
           c.schoolyear,
           c.content_type,
           CASE LTRIM(RTRIM(c.difficulty))
               WHEN N'하' THEN 1
               WHEN N'상' THEN 3
               ELSE 2
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
  AND d.year = CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4))
  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_priority p WHERE p.draft_id = d.draft_id);
