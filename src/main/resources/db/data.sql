
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

-- 독서태도 코드 마스터 (2026-07-29) — monitor-live.js 하드코딩 대체, 문구 수정은 이 테이블만 고치면 됨
INSERT INTO erp_bookstore_attitude_code (attitude_code, attitude_name, use_yn, updated_by) VALUES
('GOOD_POSTURE',  N'바른 자세로 차분하게 정독했어요.', 1, 'seed'),
('SELF_DIRECTED', N'스스로 책 읽기를 끝까지 이어갔어요.', 1, 'seed'),
('LOW_FOCUS',     N'집중력이 자주 흐트러졌어요.', 1, 'seed'),
('RUSHED',        N'책장을 빠르게 넘기며 서둘러 읽었어요.', 1, 'seed'),
('DISTRACTED',    N'산만한 모습을 보였어요.', 1, 'seed');

-- 테스트 계정 비밀번호 해시 생성 방법:
-- 앱 실행 후 GET /test/hash?password=0000&salt=test_salt 호출
-- 반환된 hash 값으로 아래 password_hash를 교체하세요
INSERT INTO erp_user (center_code, role_key, user_code, user_id, user_name, password_hash, salt, type, use_yn, is_han, is_book, is_clinic)
VALUES ('PUS001', 'ADMIN', 'USR001', 'admin', '관리자', '96ce7949d922462c4abff4cf507abb65299a3bd61b2d34285630d5054a6e53f6', 'test_salt', 'ADMIN', 1, 1, 1, 1);

-- 비본사(지점) 테스트 계정 — admin과 동일 비밀번호, 센터별 1명씩 / 실물도서 등록 테스트용
INSERT INTO erp_user (center_code, role_key, user_code, user_id, user_name, password_hash, salt, type, use_yn, is_han, is_book, is_clinic) VALUES
('PUS002', 'ADMIN', 'USR002', 'branch', '지점관리자', '96ce7949d922462c4abff4cf507abb65299a3bd61b2d34285630d5054a6e53f6', 'test_salt', 'ADMIN', 1, 1, 1, 1),
('DAE001', 'ADMIN', 'USR003', 'branch2', '지점관리자', '96ce7949d922462c4abff4cf507abb65299a3bd61b2d34285630d5054a6e53f6', 'test_salt', 'ADMIN', 1, 1, 1, 1),
('ULS001', 'ADMIN', 'USR004', 'branch3', '지점관리자', '96ce7949d922462c4abff4cf507abb65299a3bd61b2d34285630d5054a6e53f6', 'test_salt', 'ADMIN', 1, 1, 1, 1);

INSERT INTO erp_student (gender, student_privacy_agree, created_at, updated_at, app_id, center_code, grade_key, status_key, school, student_id, student_name, address, address_detail, app_password, app_token, birth, is_hoho, billing_phone, sub_han, sub_book, sub_hoho, serial_num)
VALUES (1, 1, '2025-12-02 19:54:24.214', '2026-03-12 15:06:29.502', '629548880', 'PUS001', '05', 'ACTIVE', '호호 초등학교', 'PUS001251202FDA6E', '김호이', '부산 해운대구 센텀중앙로 97', 'A동 2810호', '62da5956da04fdedd0ff08a3f8c812793ef4219cd0405d44bf5412f3264fecf0', 'el7jugjIxU2ikFMRCZyTK8:APA91bE2fkQ6TDmwXcy7sHIdScsIMJ7mGrkixZxYLwpVuDWogq3aXHxbAStlasw7h1VeByp0Ey0iU9MUiiiMCqyOGwNpYOqDOHeq7A4rwp17ATklirE2oOU', '2015-12-12',  0, '01062954886', 1, 1, 0, '002260002');

-- 센터별 재고 더미 (2026-07-13 재설계) — data-items.sql이 적재한 사본(기본 center_code='PUS002')을
-- DAE001에도 1권씩 복제 (총 2개 센터 재고). erp_bookstore_item은 이제 매 기동 DROP 후 재생성되고
-- data-items.sql이 그 직후 자동으로 다시 채우므로, 이 시점엔 DAE001 재고가 없는 게 항상 보장된다.
INSERT INTO erp_bookstore_item (bcode, content_id, center_code, book_title, author, publisher, image_url, qty)
SELECT bcode, content_id, 'DAE001', book_title, author, publisher, image_url, qty
FROM erp_bookstore_item WHERE center_code = 'PUS002';


-- ────────────────────────────────────────────────────────
-- 학생 독서 클리닉 — 1단계(책 추천)에 이어 2단계(문제풀이/채점) 재설계 (2026-07-09)
-- ────────────────────────────────────────────────────────

-- 레벨 규칙(단계명/특징/필요권수)은 ClinicService.LEVEL_RULES(Java 상수)로 관리 — DB 시딩 없음

-- 레벨 칭호 (단계=학년, 레벨 1~12) — Lv.12는 각 단계의 만렙 'GRADE{n} Master'
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

-- 뱃지 마스터 (1~5) — 달성 조건은 category로 데이터화 (판정: ClinicService.checkAndAwardBadges)
-- 모두 "처음 1회 달성 시" 획득하는 단발 업적이라 threshold는 1, param은 사용하지 않는다.
--   BASIC_ATTEMPT : 기본(01) 문제를 한 번이라도 제출(완독=책을 끝까지 읽고 도전)
--   BASIC_PASS    : 기본(01) 문제 합격(recommend_log.status='DONE')
--   BASIC_PERFECT : 기본(01) 문제 만점(recommend_log.grade='KING')
--   ADV_PASS      : 심화(02) 문제 합격(합격선 이상 회차 존재)
--   ADV_PERFECT   : 심화(02) 문제 만점(전 문항 정답 회차 존재)
INSERT INTO erp_bookstore_badge (badge_id, badge_name, badge_desc, category, threshold, param) VALUES
(1, N'참 잘했어요!', N'책을 끝까지 읽음',                        'BASIC_ATTEMPT', 1, NULL),
(2, N'독서친구',     N'책의 내용을 이해하고 문제풀이 완료',      'BASIC_PASS',    1, NULL),
(3, N'독서왕',       N'책의 내용을 정확하게 이해',              'BASIC_PERFECT', 1, NULL),
(4, N'심화 완료',    N'한 단계 깊은 사고 활동에 도전',          'ADV_PASS',      1, NULL),
(5, N'심화왕',       N'어휘력과 문해력의 실력 증가',            'ADV_PERFECT',   1, NULL);

-- ────────────────────────────────────────────────────────
-- 난이도 자동 부여 (2026-07-31) — 도서 시드(data-books.sql)가 difficulty를 넣지 않아 전 권이 비어
-- 있었다. 비어 있으면 아래 순위 정렬의 규칙 2(하→중→상)가 통째로 무의미해지므로 값을 채워준다.
--
-- 기준: 학년 안에서 "장르 난이도" 순으로 줄 세운 뒤 NTILE(3)으로 균등 3등분한다. 장르는 난이도가
-- 아니지만 이 데이터에서 유일하게 난이도와 상관이 있는 값이라 대용으로 쓴다 — 창작·전래 계열이
-- 앞(하), 인물·과학 계열이 가운데(중), 고전·비문학·소설 계열이 뒤(상)로 간다.
--
-- 근거가 강한 값이 아니라 "일단 순서가 돌아가게" 하는 출발점이다. 실제 난이도는 도서 관리 화면에서
-- 고쳐야 하고, 아래 WHERE 절이 그 값을 지켜준다 — 이미 값이 있는 책은 건드리지 않으므로 매 기동
-- 다시 돌아도 사람이 입력한 값을 덮어쓰지 않는다(content 테이블은 매 기동 리셋 대상이 아니다).
-- ────────────────────────────────────────────────────────
WITH weighted AS (
    SELECT content_id,
           NTILE(3) OVER (
               PARTITION BY schoolyear
               ORDER BY
                   CASE
                       -- 쉬움: 창작 / 전래 / 외국창작 / 신체동화 / 동시지 / 만화
                       WHEN genre IN ('01','03','05','07','10','27') THEN 1
                       -- 보통: 명작 / 환경 / 과학동화 / 인물 / 상식 / 과학 / 생활 비문학
                       WHEN genre IN ('02','06','08','09','13','18','19','22','23','24') THEN 2
                       -- 어려움: 고전 / 역사 / 소설 / 교양 / 그 밖의 비문학 전반
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

-- 권장도서 순위 초안: 올해 + 초1~초5, 활성 상태 (초6/중등은 아직 시딩하지 않는다)
INSERT INTO erp_bookstore_priority_draft (year, schoolyear, is_active, created_by) VALUES
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '01', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '02', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '03', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '04', 'Y', 'seed'),
(CAST(YEAR(DATEADD(HOUR, 9, GETUTCDATE())) AS VARCHAR(4)), '05', 'Y', 'seed');

-- 순위 내용 — 정렬 규칙 확정 (2026-07-31). ClinicService.pickNextItem이 이 sort_order를 그대로
-- 훑으며 추천하므로, "어떤 순서로 추천할지"는 추천 코드가 아니라 이 정렬이 결정한다.
--   1) 학년: 초안(draft)이 학년별로 나뉘어 있어 c.schoolyear = d.schoolyear 조인으로 이미 걸러진다
--   2) 난이도: 하 → 중 → 상. content.difficulty는 코드가 아니라 자유 입력 텍스트라(도서 관리 화면에서
--      직접 타이핑) 공백을 털어내고 비교하고, 값이 비어 있으면 중간(중)으로 취급한다
--   3) 분류: 같은 난이도 구간 안에서 분류(content_type)가 연달아 나오지 않게 라운드로빈으로 섞는다.
--      분류별로 번호를 매긴 뒤(type_seq) 그 번호 순으로 뽑으면 "각 분류의 1번째들 → 2번째들 → ..."이
--      되어 분류가 자연히 번갈아 나온다. 어떤 분류의 책이 유독 많으면 뒤쪽엔 그 분류만 남는데,
--      남은 게 그것뿐이라 이건 피할 수 없다.
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


-- ────────────────────────────────────────────────────────
-- 테스트 학생: DAE001 / PUS002 각 16명, 1~4교시 x 4명씩 예약 (모니터링 화면 부하 테스트용, 260729)
-- 학년(grade_key)은 활성 priority_draft가 있는 01~05만 사용(06/07은 draft 미시딩 상태라 추천이 나지 않음).
-- 재고가 "책 1권당 센터당 1부"뿐이라, 같은 센터+같은 교시에 같은 학년을 4명 몰아넣으면
-- 1순위 도서 재고가 곧장 소진되어 2~4번째 학생부터 우선순위 폴백(다음 순위 도서로 자동 대체)이
-- 실제로 타는지 확인할 수 있다. 5개 교시는 학년을 통일해 이 충돌 케이스를 재현하고,
-- 나머지 3개 교시는 학년을 섞어 정상(비충돌) 케이스와 대비해서 볼 수 있게 배치했다.
-- ────────────────────────────────────────────────────────
INSERT INTO erp_student (center_code, grade_key, status_key, school, student_id, student_name, app_id, gender, student_privacy_agree, sub_book) VALUES
-- DAE001 1교시: 초1 x4 (동일 학년 충돌 케이스)
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T01', N'테스트생1', '7001', 1, 1, 1),
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T02', N'테스트생2', '7002', 0, 1, 1),
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T03', N'테스트생3', '7003', 1, 1, 1),
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T04', N'테스트생4', '7004', 0, 1, 1),
-- DAE001 2교시: 초2 x4 (동일 학년 충돌 케이스)
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T05', N'테스트생5', '7005', 1, 1, 1),
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T06', N'테스트생6', '7006', 0, 1, 1),
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T07', N'테스트생7', '7007', 1, 1, 1),
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T08', N'테스트생8', '7008', 0, 1, 1),
-- DAE001 3교시: 초3 x4 (동일 학년 충돌 케이스)
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T09', N'테스트생9', '7009', 1, 1, 1),
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T10', N'테스트생10', '7010', 0, 1, 1),
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T11', N'테스트생11', '7011', 1, 1, 1),
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T12', N'테스트생12', '7012', 0, 1, 1),
-- DAE001 4교시: 초4 x4 (동일 학년 충돌 케이스)
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T13', N'테스트생13', '7013', 1, 1, 1),
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T14', N'테스트생14', '7014', 0, 1, 1),
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T15', N'테스트생15', '7015', 1, 1, 1),
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T16', N'테스트생16', '7016', 0, 1, 1),
-- PUS002 1교시: 초5 x4 (동일 학년 충돌 케이스)
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T01', N'테스트생1', '8001', 1, 1, 1),
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T02', N'테스트생2', '8002', 0, 1, 1),
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T03', N'테스트생3', '8003', 1, 1, 1),
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T04', N'테스트생4', '8004', 0, 1, 1),
-- PUS002 2교시: 초1~초4 혼합 (정상/비충돌 대비 케이스)
('PUS002', '01', 'ACTIVE', N'남천 초등학교', 'PUS002T05', N'테스트생5', '8005', 1, 1, 1),
('PUS002', '02', 'ACTIVE', N'남천 초등학교', 'PUS002T06', N'테스트생6', '8006', 0, 1, 1),
('PUS002', '03', 'ACTIVE', N'남천 초등학교', 'PUS002T07', N'테스트생7', '8007', 1, 1, 1),
('PUS002', '04', 'ACTIVE', N'남천 초등학교', 'PUS002T08', N'테스트생8', '8008', 0, 1, 1),
-- PUS002 3교시: 초2~초5 혼합 (정상/비충돌 대비 케이스)
('PUS002', '02', 'ACTIVE', N'남천 초등학교', 'PUS002T09', N'테스트생9', '8009', 1, 1, 1),
('PUS002', '03', 'ACTIVE', N'남천 초등학교', 'PUS002T10', N'테스트생10', '8010', 0, 1, 1),
('PUS002', '04', 'ACTIVE', N'남천 초등학교', 'PUS002T11', N'테스트생11', '8011', 1, 1, 1),
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T12', N'테스트생12', '8012', 0, 1, 1),
-- PUS002 4교시: 초1/초3/초4/초5 혼합 (정상/비충돌 대비 케이스, 2교시와 다른 조합)
('PUS002', '01', 'ACTIVE', N'남천 초등학교', 'PUS002T13', N'테스트생13', '8013', 1, 1, 1),
('PUS002', '03', 'ACTIVE', N'남천 초등학교', 'PUS002T14', N'테스트생14', '8014', 0, 1, 1),
('PUS002', '04', 'ACTIVE', N'남천 초등학교', 'PUS002T15', N'테스트생15', '8015', 1, 1, 1),
('PUS002', '05', 'ACTIVE', N'남천 초등학교', 'PUS002T16', N'테스트생16', '8016', 0, 1, 1);

-- ────────────────────────────────────────────────────────
-- 클리닉 예약 테스트 데이터: 센터별 16명을 1~4교시에 4명씩 오늘자로 예약 (매 재기동마다 자동 등록)
-- ────────────────────────────────────────────────────────
INSERT INTO erp_bookstore_clinic_reservation (student_id, reservation_date, time_slot)
SELECT student_id,
       CAST(DATEADD(HOUR, 9, GETUTCDATE()) AS DATE),
       CASE
           WHEN RIGHT(student_id, 2) BETWEEN '01' AND '04' THEN '1'
           WHEN RIGHT(student_id, 2) BETWEEN '05' AND '08' THEN '2'
           WHEN RIGHT(student_id, 2) BETWEEN '09' AND '12' THEN '3'
           ELSE '4'
       END
FROM erp_student
WHERE student_id LIKE 'DAE001T%' OR student_id LIKE 'PUS002T%';







-- ── 테스트 학생 앱 비밀번호 (2026-08-04, 학부모 앱 로그인 테스트용) ──────────────
-- 학생 PWA는 QR(appId)만으로 로그인해서 비밀번호가 필요 없었지만, 학부모 앱은 결제를
-- 다루므로 비밀번호까지 확인한다(AppAuthController). 시드 학생들은 app_password가
-- 비어 있어 그대로면 로그인이 안 되므로 여기서 채운다.
--
-- 값은 sha256('1234')이다. erp_student에는 salt 컬럼이 없어 salt 없이 해시한다(HashUtils 참고).
-- 개발 시드 전용이며 운영에는 data.sql 자체가 실행되지 않는다.
UPDATE erp_student
SET app_password = '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4'
WHERE app_password IS NULL OR app_password = '';


-- ── 테스트 형제 등록 (2026-08-05, 형제 묶음결제 개발용) ──────────────────────
-- app_id 7001/7002(테스트생1/테스트생2)를 형제로 묶는다. 형제 매핑은 로그인/결제/세션에서
-- 실제로 쓰는 erp_student.student_id 기준이라 app_id(7001/7002)가 아니라
-- student_id(DAE001T01/DAE001T02)로 저장한다. sibling_key는 대표 학생의 student_id를 그대로 쓴다.
-- erp_student_sibling도 결제 테이블과 같은 이유로 IF OBJECT_ID(...) IS NULL로만 생성돼
-- 매 기동 리셋되지 않는다 — 재기동 시 UNIQUE 제약 위반이 나지 않도록 존재 여부를 먼저 확인한다.
IF NOT EXISTS (SELECT 1 FROM erp_student_sibling WHERE sibling_key = 'DAE001T01' AND student_id = 'DAE001T01')
    INSERT INTO erp_student_sibling (sibling_key, student_id) VALUES ('DAE001T01', 'DAE001T01');
IF NOT EXISTS (SELECT 1 FROM erp_student_sibling WHERE sibling_key = 'DAE001T01' AND student_id = 'DAE001T02')
    INSERT INTO erp_student_sibling (sibling_key, student_id) VALUES ('DAE001T01', 'DAE001T02');
