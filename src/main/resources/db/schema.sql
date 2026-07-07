-- DROP (FK 역순)
IF OBJECT_ID('erp_bookstore_recommend_log',         'U') IS NOT NULL DROP TABLE erp_bookstore_recommend_log;
IF OBJECT_ID('erp_bookstore_reading',               'U') IS NOT NULL DROP TABLE erp_bookstore_reading;
IF OBJECT_ID('erp_bookstore_student_badge',         'U') IS NOT NULL DROP TABLE erp_bookstore_student_badge;
IF OBJECT_ID('erp_bookstore_badge',                 'U') IS NOT NULL DROP TABLE erp_bookstore_badge;
IF OBJECT_ID('erp_bookstore_student_info',          'U') IS NOT NULL DROP TABLE erp_bookstore_student_info;
IF OBJECT_ID('erp_bookstore_character',             'U') IS NOT NULL DROP TABLE erp_bookstore_character;
IF OBJECT_ID('erp_bookstore_exp_rule',              'U') IS NOT NULL DROP TABLE erp_bookstore_exp_rule;
IF OBJECT_ID('erp_bookstore_level',                 'U') IS NOT NULL DROP TABLE erp_bookstore_level;
IF OBJECT_ID('erp_notification',                    'U') IS NOT NULL DROP TABLE erp_notification;
IF OBJECT_ID('erp_bookstore_code',                  'U') IS NOT NULL DROP TABLE erp_bookstore_code;
IF OBJECT_ID('erp_bookstore_itempool_del',          'U') IS NOT NULL DROP TABLE erp_bookstore_itempool_del;
IF OBJECT_ID('erp_bookstore_itempool',              'U') IS NOT NULL DROP TABLE erp_bookstore_itempool;
IF OBJECT_ID('erp_bookstore_item_del',              'U') IS NOT NULL DROP TABLE erp_bookstore_item_del;
IF OBJECT_ID('erp_bookstore_item_center',           'U') IS NOT NULL DROP TABLE erp_bookstore_item_center;
IF OBJECT_ID('erp_bookstore_item',                  'U') IS NOT NULL DROP TABLE erp_bookstore_item;
IF OBJECT_ID('erp_bookstore_priority_del',          'U') IS NOT NULL DROP TABLE erp_bookstore_priority_del;
IF OBJECT_ID('erp_bookstore_priority',              'U') IS NOT NULL DROP TABLE erp_bookstore_priority;
IF OBJECT_ID('erp_bookstore_priority_draft_del',    'U') IS NOT NULL DROP TABLE erp_bookstore_priority_draft_del;
IF OBJECT_ID('erp_bookstore_priority_draft',        'U') IS NOT NULL DROP TABLE erp_bookstore_priority_draft;
IF OBJECT_ID('erp_bookstore_content_detail_del',    'U') IS NOT NULL DROP TABLE erp_bookstore_content_detail_del;
IF OBJECT_ID('erp_bookstore_content_detail',        'U') IS NOT NULL DROP TABLE erp_bookstore_content_detail;
IF OBJECT_ID('erp_bookstore_content_curriculum_del','U') IS NOT NULL DROP TABLE erp_bookstore_content_curriculum_del;
IF OBJECT_ID('erp_bookstore_content_curriculum',    'U') IS NOT NULL DROP TABLE erp_bookstore_content_curriculum;
IF OBJECT_ID('erp_bookstore_content_recommend_del', 'U') IS NOT NULL DROP TABLE erp_bookstore_content_recommend_del;
IF OBJECT_ID('erp_bookstore_content_recommend',     'U') IS NOT NULL DROP TABLE erp_bookstore_content_recommend;
IF OBJECT_ID('erp_bookstore_content_award_del',     'U') IS NOT NULL DROP TABLE erp_bookstore_content_award_del;
IF OBJECT_ID('erp_bookstore_content_award',         'U') IS NOT NULL DROP TABLE erp_bookstore_content_award;
IF OBJECT_ID('erp_bookstore_content_del',           'U') IS NOT NULL DROP TABLE erp_bookstore_content_del;
IF OBJECT_ID('erp_bookstore_content',               'U') IS NOT NULL DROP TABLE erp_bookstore_content;
IF OBJECT_ID('erp_student',                         'U') IS NOT NULL DROP TABLE erp_student;
IF OBJECT_ID('erp_user',                            'U') IS NOT NULL DROP TABLE erp_user;
IF OBJECT_ID('erp_center',                          'U') IS NOT NULL DROP TABLE erp_center;


-- CREATE
CREATE TABLE erp_bookstore_code (
    gubun          VARCHAR(1) NOT NULL,
    code           VARCHAR(2)  NOT NULL,
    codeNm         VARCHAR(20) NOT NULL
    CONSTRAINT PK_erp_bookstore_code PRIMARY KEY CLUSTERED (gubun ASC, code ASC)
    WITH (
        PAD_INDEX = OFF,
        STATISTICS_NORECOMPUTE = OFF,
        IGNORE_DUP_KEY = OFF,
        ALLOW_ROW_LOCKS = ON,
        ALLOW_PAGE_LOCKS = ON
        ) ON [PRIMARY])
    ON [PRIMARY];

CREATE TABLE erp_center (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    opened_at       DATE,
    biz_no          VARCHAR(20),
    center_code     VARCHAR(20)  NOT NULL UNIQUE,
    region_key      VARCHAR(20),
    center_tel      VARCHAR(20),
    director_name   VARCHAR(50),
    center_address  VARCHAR(255),
    center_email    VARCHAR(100),
    center_name     VARCHAR(100),
    status          VARCHAR(20),
    manager_name    VARCHAR(50),
    manager_tel     VARCHAR(20),
    manager_email   VARCHAR(100),
    registration_no VARCHAR(50),
    biz_name        VARCHAR(100)
);

CREATE TABLE erp_user (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    created_at    DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    center_code   VARCHAR(50),
    role_key      VARCHAR(50),
    user_code     VARCHAR(50),
    user_id       VARCHAR(100) NOT NULL,
    user_name     VARCHAR(100),
    password_hash VARCHAR(255),
    salt          VARCHAR(100),
    type          VARCHAR(20),
    user_phone    VARCHAR(20),
    is_han        BIT          DEFAULT 0,
    is_book       BIT          DEFAULT 0,
    use_yn        BIT          DEFAULT 1,
    is_clinic     BIT          DEFAULT 0
);

CREATE TABLE erp_student (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    created_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    center_code           VARCHAR(50),
    grade_key             VARCHAR(50),
    status_key            VARCHAR(50),
    school                VARCHAR(100),
    student_id            VARCHAR(100),
    student_name          VARCHAR(100),
    address               VARCHAR(255),
    address_detail        VARCHAR(255),
    app_id                VARCHAR(100),
    app_password          VARCHAR(255),
    app_token             VARCHAR(255),
    birth                 VARCHAR(20),
    profile_img           VARCHAR(255),
    consult_key           VARCHAR(50),
    billing_phone         VARCHAR(20),
    serial_num            VARCHAR(100),
    gender                BIT         DEFAULT 0,
    student_privacy_agree BIT         DEFAULT 0,
    is_hoho               BIT         DEFAULT 0,
    sub_han               BIT         DEFAULT 0,
    sub_book              BIT         DEFAULT 0,
    sub_hoho              BIT         DEFAULT 0
);

CREATE TABLE erp_bookstore_content (
    content_id     INT IDENTITY(1,1) PRIMARY KEY,
    original_title VARCHAR(255),
    author         VARCHAR(100),
    genre          VARCHAR(50),   -- 장르 (code gubun='G') — 모든 도서가 1개씩 가지므로 detail이 아닌 컬럼으로 관리
    content_type   VARCHAR(50),
    schoolyear     VARCHAR(20),
    summary        VARCHAR(2000),
    keywords       VARCHAR(1000),
    state          VARCHAR(20),
    publisher      VARCHAR(100),
    image_url      VARCHAR(500),
    reading_time   VARCHAR(20),
    difficulty     VARCHAR(20)
);

CREATE TABLE erp_bookstore_content_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    content_id     INT,
    original_title VARCHAR(255),
    author         VARCHAR(100),
    genre          VARCHAR(50),
    content_type   VARCHAR(50),
    schoolyear     VARCHAR(20),
    summary        VARCHAR(2000),
    keywords       VARCHAR(1000),
    state          VARCHAR(20),
    publisher      VARCHAR(100),
    image_url      VARCHAR(500),
    reading_time   VARCHAR(20),
    difficulty     VARCHAR(20)
);

-- 분류별 전용 상세 (소분류) — content_type이 정하는 부가 값 1개 (교과연계=연계교과, 기관추천=추천기관명, 인증수상작=수상명)
-- 장르(G)는 모든 도서가 가지므로 content.genre 컬럼으로 이동, 여기는 분류 부가정보만 남음 (도서당 최대 1행)
CREATE TABLE erp_bookstore_content_detail (
    content_id     INT          NOT NULL,
    gubun          VARCHAR(1)   NOT NULL,  -- C(교과연계) / R(기관추천) / A(인증수상작)
    name           VARCHAR(200),           -- gubun별 값: 연계교과 / 추천기관명 / 수상명
    PRIMARY KEY (content_id, gubun),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

CREATE TABLE erp_bookstore_content_detail_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    content_id     INT,
    gubun          VARCHAR(1),
    name           VARCHAR(200)
);

-- 권장도서 순위 초안 (연도+학년별로 여러 건 저장 가능, 그중 하나만 적용 상태)
-- 학년탭을 각각 편집/저장하므로 초안 선택도 학년별로 독립적이어야 해서 schoolyear를 둔다 (content_detail과 달리 여긴 학년이 '어느 편집 세션인지'를 나타내는 키라 중복 저장 아님)
-- "1월 1일 반영"은 스케줄러 없이, 조회 시 year=올해 AND is_active='Y'로 필터링하는 것만으로 처리
CREATE TABLE erp_bookstore_priority_draft (
    draft_id    INT IDENTITY(1,1) PRIMARY KEY,
    year        VARCHAR(4)  NOT NULL,  -- 노출 연도 (예: '2026', '2027')
    schoolyear  VARCHAR(20) NOT NULL,  -- 학년 코드 (S코드) — 이 초안이 어느 학년 탭 편집본인지
    is_active   VARCHAR(1)  NOT NULL DEFAULT 'N',  -- 이 연도+학년에 실제 적용할 초안인지 (연도+학년당 최대 1건 'Y')
    created_by  VARCHAR(100),  -- 저장한 사용자 이름
    created_at  DATETIME2   DEFAULT CURRENT_TIMESTAMP
);

-- 초안별 순위 내용 — 학년은 content.schoolyear로 이미 정해지므로 별도 컬럼 없이 content_id로 조인해서 판별
CREATE TABLE erp_bookstore_priority (
    draft_id    INT         NOT NULL,
    content_id  INT         NOT NULL,
    sort_order  INT         NOT NULL,  -- 순위 (1부터)
    PRIMARY KEY (draft_id, content_id),
    FOREIGN KEY (draft_id)   REFERENCES erp_bookstore_priority_draft(draft_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 순위 초안 삭제 이력 (복구 기능 없음 — 삭제 시 그대로 이관만 하고 끝)
CREATE TABLE erp_bookstore_priority_draft_del (
    del_id      INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at  DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    deleted_by  VARCHAR(100),
    draft_id    INT,
    year        VARCHAR(4),
    schoolyear  VARCHAR(20),
    is_active   VARCHAR(1),
    created_by  VARCHAR(100),
    created_at  DATETIME2
);

CREATE TABLE erp_bookstore_priority_del (
    del_id      INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at  DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    draft_id    INT,
    content_id  INT,
    sort_order  INT
);

CREATE TABLE erp_bookstore_item (
    bcode          VARCHAR(50)  PRIMARY KEY,
    content_id     INT,
    book_title     VARCHAR(255),
    author         VARCHAR(100),
    publisher      VARCHAR(100),
    image_url      VARCHAR(500),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

CREATE TABLE erp_bookstore_item_center (
    bcode          VARCHAR(50)  NOT NULL,
    center_code    VARCHAR(20)  NOT NULL,
    quantity       INT          NOT NULL DEFAULT 0,
    state          VARCHAR(20),
    registered_at  DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bcode, center_code),
    FOREIGN KEY (bcode)        REFERENCES erp_bookstore_item(bcode),
    FOREIGN KEY (center_code)  REFERENCES erp_center(center_code)
);

CREATE TABLE erp_bookstore_item_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    bcode          VARCHAR(50),
    content_id     INT,
    book_title     VARCHAR(255),
    author         VARCHAR(100),
    publisher      VARCHAR(100),
    image_url      VARCHAR(500),
    center_code    VARCHAR(20),
    quantity       INT,
    state          VARCHAR(20)
);

CREATE TABLE erp_bookstore_itempool (
    content_id     INT,
    qlevel         VARCHAR(20),
    qnum           VARCHAR(20),
    q              NVARCHAR(2000),
    qex            NVARCHAR(2000),
    e1             NVARCHAR(500),
    e2             NVARCHAR(500),
    e3             NVARCHAR(500),
    e4             NVARCHAR(500),
    ans            VARCHAR(100),
    qtype          VARCHAR(20),
    qexgb          VARCHAR(20),
    state          VARCHAR(20),

    PRIMARY KEY (content_id, qlevel, qnum),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

CREATE TABLE erp_bookstore_itempool_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    content_id     INT,
    qnum           VARCHAR(20),
    q              NVARCHAR(2000),
    qex            NVARCHAR(2000),
    e1             NVARCHAR(500),
    e2             NVARCHAR(500),
    e3             NVARCHAR(500),
    e4             NVARCHAR(500),
    ans            VARCHAR(100),
    qtype          VARCHAR(20),
    qlevel         VARCHAR(20),
    qexgb          VARCHAR(20),
    state          VARCHAR(20)
);

CREATE TABLE erp_notification (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    sent_at       DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    sent_by       VARCHAR(100),
    title         NVARCHAR(200),
    body          NVARCHAR(1000),
    target_type   VARCHAR(20),
    target_id     VARCHAR(100),
    fcm_token     VARCHAR(500),
    status        VARCHAR(10),
    error_msg     VARCHAR(500)
);


-- ────────────────────────────────────────────────────────
-- 학생 독서 클리닉 (student-main 화면)
-- 참고: erp_student.student_id 에 UNIQUE 제약이 없어 학생 연결은 FK 없이
--       student_id VARCHAR 값으로만 연결한다 (erp_notification.target_id와 같은 방식)
-- ────────────────────────────────────────────────────────

-- 레벨 마스터 — 누적 EXP 기준으로 레벨이 정해지고, 레벨마다 단계/칭호/특징이 있다
CREATE TABLE erp_bookstore_level (
    level_no      INT           PRIMARY KEY,      -- 1 ~ 10
    level_name    NVARCHAR(50)  NOT NULL,         -- 단계명 (입문 / 성장 / 마스터)
    title         NVARCHAR(50),                   -- 칭호 (독서 씨앗, 독서 새싹 ...)
    feature       NVARCHAR(300),                  -- 단계별 특징 문구
    required_exp  INT           NOT NULL          -- 이 레벨에서 다음 레벨로 올라가는 데 필요한 누적 EXP (레벨10은 만렙 기준치)
);

-- 학년별 권당 EXP — 읽은 "책의 학년(content.schoolyear)"에 따라 획득 EXP가 다르다
-- (예: 초1~2 책 = 20EXP, 초5~6 책 = 80EXP)
CREATE TABLE erp_bookstore_exp_rule (
    schoolyear    VARCHAR(20)   PRIMARY KEY,      -- 학년 코드 (S코드)
    exp_per_book  INT           NOT NULL          -- 해당 학년 도서 1권 완독 시 획득 EXP
);

-- 캐릭터 마스터 — 레벨과 무관하게 학생이 직접 선택
CREATE TABLE erp_bookstore_character (
    character_id   INT IDENTITY(1,1) PRIMARY KEY,
    character_name NVARCHAR(50)  NOT NULL,
    image_url      VARCHAR(255)  NOT NULL
);

-- 학생 독서 현황 (erp_student 1:1) — 현재 상태 스냅샷
CREATE TABLE erp_bookstore_student_info (
    student_id    VARCHAR(100)  PRIMARY KEY,      -- erp_student.student_id
    level_no      INT           NOT NULL DEFAULT 1,
    exp           INT           NOT NULL DEFAULT 0,  -- 누적 경험치
    books_read    INT           NOT NULL DEFAULT 0,  -- 누적 완독 수 (reading에서 파생되는 캐시)
    character_id  INT,                               -- 학생이 선택한 캐릭터
    updated_at    DATETIME2     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (level_no)     REFERENCES erp_bookstore_level(level_no),
    FOREIGN KEY (character_id) REFERENCES erp_bookstore_character(character_id)
);

-- 뱃지 마스터
CREATE TABLE erp_bookstore_badge (
    badge_id        INT IDENTITY(1,1) PRIMARY KEY,
    badge_name      NVARCHAR(50)  NOT NULL,       -- '독서 탐험가'
    description     NVARCHAR(200),                -- '첫 번째 정독을 완료했어요!'
    image_url       VARCHAR(255),
    condition_type  VARCHAR(30),                  -- 획득 조건 유형 (FIRST_DONE / FRIEND_CNT / KING_CNT / MONTHLY_ALL ...)
    condition_value INT                           -- 조건 수치 (5번, 1회 등)
);

-- 학생-뱃지 매핑 (N:N)
CREATE TABLE erp_bookstore_student_badge (
    student_id   VARCHAR(100)  NOT NULL,
    badge_id     INT           NOT NULL,
    acquired_at  DATETIME2     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, badge_id),
    FOREIGN KEY (badge_id) REFERENCES erp_bookstore_badge(badge_id)
);

-- 독서 기록 — "이번 달에 읽은 책", 추천 제외 조건(분류/장르/기읽음), EXP 적립의 원천
CREATE TABLE erp_bookstore_reading (
    reading_id    INT IDENTITY(1,1) PRIMARY KEY,
    student_id    VARCHAR(100)  NOT NULL,
    content_id    INT           NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'READING',  -- READING / DONE
    started_at    DATETIME2     DEFAULT CURRENT_TIMESTAMP,
    completed_at  DATETIME2,                       -- 완독 시각 (월별 조회 및 EXP 적립 기준)
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 추천 이력 — 로그인/'다른 도서 추천' 클릭 시마다 다음 우선순위 도서가 추천되므로
-- 시퀀셜 로그로 쌓고, "다음 추천"은 마지막 추천 이후의 순위부터 탐색한다
CREATE TABLE erp_bookstore_recommend_log (
    recommend_id    INT IDENTITY(1,1) PRIMARY KEY,
    student_id      VARCHAR(100)  NOT NULL,
    content_id      INT           NOT NULL,
    recommended_at  DATETIME2     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);
