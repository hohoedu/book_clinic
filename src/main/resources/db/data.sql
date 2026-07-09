
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

-- 센터별 재고 더미 — 전체 468권을 PUS002 전부 → DAE001 전부 순서로 각 1권씩 (총 936건)
INSERT INTO erp_bookstore_item_center (bcode, center_code, quantity, state)
SELECT bcode, 'PUS002', 1, 'Y' FROM erp_bookstore_item;

INSERT INTO erp_bookstore_item_center (bcode, center_code, quantity, state)
SELECT bcode, 'DAE001', 1, 'Y' FROM erp_bookstore_item;


-- ────────────────────────────────────────────────────────
-- 학생 독서 클리닉 — 1단계(책 추천)에 이어 2단계(문제풀이/채점) 재설계 (2026-07-09)
-- ────────────────────────────────────────────────────────

-- 레벨 마스터 (1~10) — required_exp: 다음 레벨로 올라가는 데 필요한 누적 EXP (레벨10은 만렙 기준치)
INSERT INTO erp_bookstore_level (level_no, level_name, title, feature, required_exp) VALUES
(1,  N'입문',   N'독서 씨앗',     N'독서의 즐거움을 발견하는 시기',            80),
(2,  N'입문',   N'독서 새싹',     N'독서의 즐거움을 발견하는 시기',            200),
(3,  N'입문',   N'이야기 친구',   N'독서의 즐거움을 발견하는 시기',            360),
(4,  N'입문',   N'책벌레',        N'독서의 즐거움을 발견하는 시기',            560),
(5,  N'성장',   N'독서 탐험가',   N'책 속 지식과 생각을 모으는 시기',          760),
(6,  N'성장',   N'생각 탐험가',   N'책 속 지식과 생각을 모으는 시기',          980),
(7,  N'성장',   N'이야기 수집가', N'책 속 지식과 생각을 모으는 시기',          1200),
(8,  N'성장',   N'독서 여행자',   N'책 속 지식과 생각을 모으는 시기',          1440),
(9,  N'마스터', N'꿈꾸는 독서가', N'책을 통해 성장한 호호책방 대표 독서가',    1680),
(10, N'마스터', N'독서 완주자',   N'책을 통해 성장한 호호책방 대표 독서가',    1920);

-- 학년별 권당 EXP — 읽은 책의 학년(content.schoolyear) 기준. 중등(S07)은 운영에서 확정 예정이라 제외
INSERT INTO erp_bookstore_exp_rule (schoolyear, exp_per_book) VALUES
('01', 20),  -- 초1
('02', 20),  -- 초2
('03', 40),  -- 초3
('04', 40),  -- 초4
('05', 80),  -- 초5
('06', 80);  -- 초6

-- 권장도서 순위 초안: 올해 + 초5, 활성 상태
INSERT INTO erp_bookstore_priority_draft (year, schoolyear, is_active, created_by) VALUES
(CAST(YEAR(GETDATE()) AS VARCHAR(4)), '05', 'Y', 'seed');

-- 순위 내용: 초5 노출 도서 전체를 content_id 순으로 순위 부여
INSERT INTO erp_bookstore_priority (draft_id, content_id, sort_order)
SELECT (SELECT MAX(draft_id) FROM erp_bookstore_priority_draft),
       content_id,
       ROW_NUMBER() OVER (ORDER BY content_id)
FROM erp_bookstore_content
WHERE schoolyear = '05' AND state = 'Y';

-- 권장도서 순위 초안: 초1~초4 (DAE001/PUS002 테스트 학생용)
INSERT INTO erp_bookstore_priority_draft (year, schoolyear, is_active, created_by) VALUES
(CAST(YEAR(GETDATE()) AS VARCHAR(4)), '01', 'Y', 'seed'),
(CAST(YEAR(GETDATE()) AS VARCHAR(4)), '02', 'Y', 'seed'),
(CAST(YEAR(GETDATE()) AS VARCHAR(4)), '03', 'Y', 'seed'),
(CAST(YEAR(GETDATE()) AS VARCHAR(4)), '04', 'Y', 'seed');

INSERT INTO erp_bookstore_priority (draft_id, content_id, sort_order)
SELECT d.draft_id, c.content_id, ROW_NUMBER() OVER (PARTITION BY d.draft_id ORDER BY c.content_id)
FROM erp_bookstore_priority_draft d
JOIN erp_bookstore_content c ON c.schoolyear = d.schoolyear AND c.state = 'Y'
WHERE d.schoolyear IN ('01','02','03','04')
  AND d.year = CAST(YEAR(GETDATE()) AS VARCHAR(4));


-- ────────────────────────────────────────────────────────
-- 테스트 학생: DAE001 / PUS002 각 8명 (학년 초1~초4 반복 배정)
-- ────────────────────────────────────────────────────────
INSERT INTO erp_student (center_code, grade_key, status_key, school, student_id, student_name, app_id, gender, student_privacy_agree, sub_book) VALUES
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T01', N'테스트생1', '7001', 1, 1, 1),
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T02', N'테스트생2', '7002', 0, 1, 1),
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T03', N'테스트생3', '7003', 1, 1, 1),
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T04', N'테스트생4', '7004', 0, 1, 1),
('DAE001', '01', 'ACTIVE', N'월성 초등학교', 'DAE001T05', N'테스트생5', '7005', 1, 1, 1),
('DAE001', '02', 'ACTIVE', N'월성 초등학교', 'DAE001T06', N'테스트생6', '7006', 0, 1, 1),
('DAE001', '03', 'ACTIVE', N'월성 초등학교', 'DAE001T07', N'테스트생7', '7007', 1, 1, 1),
('DAE001', '04', 'ACTIVE', N'월성 초등학교', 'DAE001T08', N'테스트생8', '7008', 0, 1, 1),
('PUS002', '01', 'ACTIVE', N'남천 초등학교', 'PUS002T01', N'테스트생1', '8001', 1, 1, 1),
('PUS002', '02', 'ACTIVE', N'남천 초등학교', 'PUS002T02', N'테스트생2', '8002', 0, 1, 1),
('PUS002', '03', 'ACTIVE', N'남천 초등학교', 'PUS002T03', N'테스트생3', '8003', 1, 1, 1),
('PUS002', '04', 'ACTIVE', N'남천 초등학교', 'PUS002T04', N'테스트생4', '8004', 0, 1, 1),
('PUS002', '01', 'ACTIVE', N'남천 초등학교', 'PUS002T05', N'테스트생5', '8005', 1, 1, 1),
('PUS002', '02', 'ACTIVE', N'남천 초등학교', 'PUS002T06', N'테스트생6', '8006', 0, 1, 1),
('PUS002', '03', 'ACTIVE', N'남천 초등학교', 'PUS002T07', N'테스트생7', '8007', 1, 1, 1),
('PUS002', '04', 'ACTIVE', N'남천 초등학교', 'PUS002T08', N'테스트생8', '8008', 0, 1, 1);

