-- DROP (FK 역순)
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
