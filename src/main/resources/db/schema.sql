-- DROP (FK 역순)
IF OBJECT_ID('erp_bookstore_reading_log',           'U') IS NOT NULL DROP TABLE erp_bookstore_reading_log;
IF OBJECT_ID('erp_bookstore_clinic_session',        'U') IS NOT NULL DROP TABLE erp_bookstore_clinic_session;
IF OBJECT_ID('erp_bookstore_clinic_reservation',    'U') IS NOT NULL DROP TABLE erp_bookstore_clinic_reservation;
IF OBJECT_ID('erp_bookstore_student_badge',         'U') IS NOT NULL DROP TABLE erp_bookstore_student_badge;
IF OBJECT_ID('erp_bookstore_badge',                 'U') IS NOT NULL DROP TABLE erp_bookstore_badge;
IF OBJECT_ID('erp_bookstore_quiz_answer_log',       'U') IS NOT NULL DROP TABLE erp_bookstore_quiz_answer_log;
IF OBJECT_ID('erp_bookstore_student_info',          'U') IS NOT NULL DROP TABLE erp_bookstore_student_info;
IF OBJECT_ID('erp_bookstore_exp_rule',               'U') IS NOT NULL DROP TABLE erp_bookstore_exp_rule;      -- 폐지(EXP 제거)
IF OBJECT_ID('erp_bookstore_level',                  'U') IS NOT NULL DROP TABLE erp_bookstore_level;         -- level_rule의 자식이라 먼저 DROP
IF OBJECT_ID('erp_bookstore_level_rule',             'U') IS NOT NULL DROP TABLE erp_bookstore_level_rule;
IF OBJECT_ID('erp_bookstore_recommend_log',         'U') IS NOT NULL DROP TABLE erp_bookstore_recommend_log;
IF OBJECT_ID('erp_notification',                    'U') IS NOT NULL DROP TABLE erp_notification;
IF OBJECT_ID('erp_bookstore_code',                  'U') IS NOT NULL DROP TABLE erp_bookstore_code;
IF OBJECT_ID('erp_bookstore_item_loan',             'U') IS NOT NULL DROP TABLE erp_bookstore_item_loan;
IF OBJECT_ID('erp_bookstore_item',                  'U') IS NOT NULL DROP TABLE erp_bookstore_item;
IF OBJECT_ID('erp_bookstore_item_center',           'U') IS NOT NULL DROP TABLE erp_bookstore_item_center;
IF OBJECT_ID('erp_bookstore_priority_del',          'U') IS NOT NULL DROP TABLE erp_bookstore_priority_del;
IF OBJECT_ID('erp_bookstore_priority',              'U') IS NOT NULL DROP TABLE erp_bookstore_priority;
IF OBJECT_ID('erp_bookstore_priority_draft_del',    'U') IS NOT NULL DROP TABLE erp_bookstore_priority_draft_del;
IF OBJECT_ID('erp_bookstore_priority_draft',        'U') IS NOT NULL DROP TABLE erp_bookstore_priority_draft;
IF OBJECT_ID('erp_student',                         'U') IS NOT NULL DROP TABLE erp_student;
IF OBJECT_ID('erp_user',                            'U') IS NOT NULL DROP TABLE erp_user;


-- CREATE

-- 공통 코드 테이블 — gubun(구분)별 코드/코드명 목록 (C=분류, G=장르, S=학년 등 화면 select/뱃지 표시에 공용으로 사용)
CREATE TABLE erp_bookstore_code (
    gubun          VARCHAR(1) NOT NULL,   -- 코드 구분 (C=분류, G=장르, S=학년 등)
    code           VARCHAR(2)  NOT NULL,  -- 코드값
    codeNm         VARCHAR(20) NOT NULL   -- 코드명 (화면 표시용)
    CONSTRAINT PK_erp_bookstore_code PRIMARY KEY CLUSTERED (gubun ASC, code ASC)
    WITH (
        PAD_INDEX = OFF,
        STATISTICS_NORECOMPUTE = OFF,
        IGNORE_DUP_KEY = OFF,
        ALLOW_ROW_LOCKS = ON,
        ALLOW_PAGE_LOCKS = ON
        ) ON [PRIMARY])
    ON [PRIMARY];

-- 센터(지점) 마스터
IF OBJECT_ID('erp_center', 'U') IS NULL
CREATE TABLE erp_center (
    id              INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    opened_at       DATE,           -- 개원일
    biz_no          VARCHAR(20),    -- 사업자등록번호
    center_code     VARCHAR(20)  NOT NULL UNIQUE,  -- 센터 코드 (본사=PUS001, 지점 로그인/데이터 구분 키)
    region_key      VARCHAR(20),    -- 지역 코드
    center_tel      VARCHAR(20),    -- 센터 대표 전화번호
    director_name   VARCHAR(50),    -- 원장명
    center_address  VARCHAR(255),   -- 센터 주소
    center_email    VARCHAR(100),   -- 센터 이메일
    center_name     VARCHAR(100),   -- 센터명
    status          VARCHAR(20),    -- 운영 상태
    manager_name    VARCHAR(50),    -- 담당자명
    manager_tel     VARCHAR(20),    -- 담당자 전화번호
    manager_email   VARCHAR(100),   -- 담당자 이메일
    registration_no VARCHAR(50),    -- 등록번호
    biz_name        VARCHAR(100)    -- 상호명
);

-- ERP 로그인 사용자(관리자/센터 직원) 계정
CREATE TABLE erp_user (
    id            INT IDENTITY(1,1) PRIMARY KEY,   -- 내부 PK
    created_at    DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 생성일시
    center_code   VARCHAR(50),    -- 소속 센터 코드 (본사 센터코드면 전체 마스터 데이터 편집 권한)
    role_key      VARCHAR(50),    -- 권한(역할) 코드
    user_code     VARCHAR(50),    -- 사용자 코드
    user_id       VARCHAR(100) NOT NULL,  -- 로그인 아이디
    user_name     VARCHAR(100),   -- 사용자명
    password_hash VARCHAR(255),   -- 비밀번호 해시
    salt          VARCHAR(100),   -- 비밀번호 솔트
    type          VARCHAR(20),    -- 사용자 유형
    user_phone    VARCHAR(20),    -- 전화번호
    is_han        BIT          DEFAULT 0,  -- 한자 서비스 사용 여부
    is_book       BIT          DEFAULT 0,  -- 도서 서비스 사용 여부
    use_yn        BIT          DEFAULT 1,  -- 계정 사용 여부
    is_clinic     BIT          DEFAULT 0   -- 독서클리닉 서비스 사용 여부
);

-- 학생 마스터
CREATE TABLE erp_student (
    id                    INT IDENTITY(1,1) PRIMARY KEY,   -- 내부 PK
    created_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,  -- 생성일시
    updated_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,  -- 수정일시
    center_code           VARCHAR(50),    -- 소속 센터 코드
    grade_key             VARCHAR(50),    -- 학년 코드
    status_key            VARCHAR(50),    -- 재원 상태 코드
    school                VARCHAR(100),   -- 학교명
    student_id            VARCHAR(100),   -- 학생 식별자 (UNIQUE 제약 없음 — 클리닉/앱 로그인 키로 값만으로 연결)
    student_name          VARCHAR(100),   -- 학생명
    address               VARCHAR(255),   -- 주소
    address_detail        VARCHAR(255),   -- 상세 주소
    app_id                VARCHAR(100),   -- 학생 앱 로그인 아이디
    app_password          VARCHAR(255),   -- 학생 앱 로그인 비밀번호
    app_token             VARCHAR(255),   -- 학생 앱 인증 토큰
    birth                 VARCHAR(20),    -- 생년월일
    profile_img           VARCHAR(255),   -- 프로필 이미지 URL
    consult_key           VARCHAR(50),    -- 상담 구분 코드
    billing_phone         VARCHAR(20),    -- 결제/청구용 전화번호
    serial_num            VARCHAR(100),   -- 일련번호
    gender                BIT         DEFAULT 0,  -- 성별
    student_privacy_agree BIT         DEFAULT 0,  -- 개인정보 동의 여부
    is_hoho               BIT         DEFAULT 0,  -- 호호에듀 서비스 가입 여부
    sub_han               BIT         DEFAULT 0,  -- 한자 서비스 구독 여부
    sub_book              BIT         DEFAULT 0,  -- 도서 서비스 구독 여부
    sub_hoho              BIT         DEFAULT 0   -- 호호 서비스 구독 여부
);

-- 도서(콘텐츠) 마스터 — 본사(HQ_CENTER_CODE)만 편집 가능한 표준 도서 정보
IF OBJECT_ID('erp_bookstore_content', 'U') IS NULL
CREATE TABLE erp_bookstore_content (
    content_id     INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    original_title VARCHAR(255),  -- 원제(도서명)
    author         VARCHAR(100),  -- 저자
    genre          VARCHAR(2),    -- 장르 코드 (erp_bookstore_code gubun='G') — 모든 도서가 1개씩 가지므로 detail이 아닌 컬럼으로 관리
    content_type   VARCHAR(2),    -- 분류 코드 (erp_bookstore_code gubun='C', 예: 교과연계/기관추천/인증수상작)
    schoolyear     VARCHAR(2),    -- 권장 학년 코드 (erp_bookstore_code gubun='S')
    summary        VARCHAR(2000), -- 줄거리 요약
    keywords       VARCHAR(1000), -- 키워드 (콤마 구분, 화면에서 태그로 변환)
    state          VARCHAR(20),   -- 사용 여부 (Y/N)
    publisher      VARCHAR(100),  -- 출판사
    image_url      VARCHAR(500),  -- 표지 이미지 URL
    reading_time   VARCHAR(20),   -- 예상 독서 시간
    difficulty     VARCHAR(20)    -- 난이도
);

-- 도서(erp_bookstore_content) 삭제/수정 로그 — 복구는 원본 테이블로 복사만 하고 로그는 보존
IF OBJECT_ID('erp_bookstore_content_del', 'U') IS NULL
CREATE TABLE erp_bookstore_content_del (
    log_id         INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type       VARCHAR(10)  NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at      DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    logged_by      VARCHAR(100),  -- 처리한 사용자
    content_id     INT,           -- 원본 content_id
    original_title VARCHAR(255),  -- 원제(도서명)
    author         VARCHAR(100),  -- 저자
    genre          VARCHAR(2),    -- 장르 코드
    content_type   VARCHAR(2),    -- 분류 코드
    schoolyear     VARCHAR(2),    -- 권장 학년 코드
    summary        VARCHAR(2000), -- 줄거리 요약
    keywords       VARCHAR(1000), -- 키워드
    state          VARCHAR(20),   -- 사용 여부
    publisher      VARCHAR(100),  -- 출판사
    image_url      VARCHAR(500),  -- 표지 이미지 URL
    reading_time   VARCHAR(20),   -- 예상 독서 시간
    difficulty     VARCHAR(20)    -- 난이도
);

-- 분류별 전용 상세 (소분류) — content_type이 정하는 부가 값 1개 (교과연계=연계교과, 기관추천=추천기관명, 인증수상작=수상명)
IF OBJECT_ID('erp_bookstore_content_detail', 'U') IS NULL
CREATE TABLE erp_bookstore_content_detail (
    content_id     INT          NOT NULL,  -- erp_bookstore_content.content_id
    gubun          VARCHAR(1)   NOT NULL,  -- C(교과연계) / R(기관추천) / A(인증수상작)
    name           VARCHAR(200),           -- gubun별 값: 연계교과 / 추천기관명 / 수상명
    PRIMARY KEY (content_id, gubun),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 도서 상세(erp_bookstore_content_detail) 삭제/수정 로그
IF OBJECT_ID('erp_bookstore_content_detail_del', 'U') IS NULL
CREATE TABLE erp_bookstore_content_detail_del (
    log_id         INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type       VARCHAR(10) NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at      DATETIME2 DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    logged_by      VARCHAR(100),  -- 처리한 사용자
    content_id     INT,           -- 원본 content_id
    gubun          VARCHAR(1),    -- C/R/A 구분
    name           VARCHAR(200)   -- gubun별 값
);

-- 구버전 _del 테이블 마이그레이션 — 매 기동 시 리셋되지 않는 테이블은 컬럼이 옛 이름이면 개명하고 log_type을 추가한다
IF COL_LENGTH('erp_bookstore_content_del', 'del_id') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_del.del_id', 'log_id', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_del', 'deleted_at') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_del.deleted_at', 'logged_at', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_del', 'deleted_by') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_del.deleted_by', 'logged_by', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_del', 'log_type') IS NULL ALTER TABLE erp_bookstore_content_del ADD log_type VARCHAR(10) NOT NULL DEFAULT 'DELETE';
IF COL_LENGTH('erp_bookstore_content_detail_del', 'del_id') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_detail_del.del_id', 'log_id', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_detail_del', 'deleted_at') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_detail_del.deleted_at', 'logged_at', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_detail_del', 'deleted_by') IS NOT NULL EXEC sp_rename 'erp_bookstore_content_detail_del.deleted_by', 'logged_by', 'COLUMN';
IF COL_LENGTH('erp_bookstore_content_detail_del', 'log_type') IS NULL ALTER TABLE erp_bookstore_content_detail_del ADD log_type VARCHAR(10) NOT NULL DEFAULT 'DELETE';

-- 권장도서 순위 초안 (연도+학년별로 여러 건 저장 가능, 그중 하나만 적용 상태)
-- 학년탭을 각각 편집/저장하므로 초안 선택도 학년별로 독립적이어야 해서 schoolyear를 둔다 (content_detail과 달리 여긴 학년이 '어느 편집 세션인지'를 나타내는 키라 중복 저장 아님)
-- "1월 1일 반영"은 스케줄러 없이, 조회 시 year=올해 AND is_active='Y'로 필터링하는 것만으로 처리
CREATE TABLE erp_bookstore_priority_draft (
    draft_id    INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    year        VARCHAR(4)  NOT NULL,  -- 노출 연도 (예: '2026', '2027')
    schoolyear  VARCHAR(2)  NOT NULL,  -- 학년 코드 (S코드) — 이 초안이 어느 학년 탭 편집본인지
    is_active   VARCHAR(1)  NOT NULL DEFAULT 'N',  -- 이 연도+학년에 실제 적용할 초안인지 (연도+학년당 최대 1건 'Y')
    created_by  VARCHAR(100),  -- 저장한 사용자 이름
    created_at  DATETIME2   DEFAULT CURRENT_TIMESTAMP  -- 저장일시
);

-- 초안별 순위 내용 — 학년은 content.schoolyear로 이미 정해지므로 별도 컬럼 없이 content_id로 조인해서 판별
CREATE TABLE erp_bookstore_priority (
    draft_id    INT         NOT NULL,  -- erp_bookstore_priority_draft.draft_id
    content_id  INT         NOT NULL,  -- erp_bookstore_content.content_id
    sort_order  INT         NOT NULL,  -- 순위 (1부터, 추천 로직이 이 순서로 스캔)
    PRIMARY KEY (draft_id, content_id),
    FOREIGN KEY (draft_id)   REFERENCES erp_bookstore_priority_draft(draft_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 순위 초안 삭제 로그 (복구 기능 없음 — 삭제 시 로그만 남기고 끝)
CREATE TABLE erp_bookstore_priority_draft_del (
    log_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type    VARCHAR(10) NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at   DATETIME2   DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    logged_by   VARCHAR(100),  -- 처리한 사용자
    draft_id    INT,           -- 원본 draft_id
    year        VARCHAR(4),    -- 노출 연도
    schoolyear  VARCHAR(2),    -- 학년 코드
    is_active   VARCHAR(1),    -- 적용 여부(삭제 시점 값)
    created_by  VARCHAR(100),  -- 저장한 사용자 이름
    created_at  DATETIME2      -- 원본 저장일시
);

-- 순위(erp_bookstore_priority) 삭제 로그
CREATE TABLE erp_bookstore_priority_del (
    log_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type    VARCHAR(10) NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at   DATETIME2   DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    draft_id    INT,        -- 원본 draft_id
    content_id  INT,        -- 원본 content_id
    sort_order  INT         -- 원본 순위
);

-- 실물도서 마스터 (2026-07-13 재설계) — 개별 사본 1권 = 1행으로 관리 (erp_bookstore_item_center 통합, 수량 집계 컬럼 폐지)
-- bcode는 같은 도서의 여러 사본이 공유하는 도서 식별 바코드이며, 사본 자체의 식별자는 item_id다.
-- 보유수량이 필요하면 COUNT(*)/status 집계로 조회하고, 최근 대여자/대여일시는 스냅샷 컬럼으로 바로 노출한다.
-- 재기동마다 DROP 후 재생성한다 (2026-07-14) — status가 리셋 안 되는 대여이력(recommend_log/item_loan)과
-- 어긋나면서 반납 안 된 LOANED 상태가 영구히 남아 추천이 계속 다음 순위 책으로 밀리는 문제가 있었다.
-- 데이터는 data-items.sql이 매 기동 자동으로 다시 채운다.
CREATE TABLE erp_bookstore_item (
    item_id          INT IDENTITY(1,1) PRIMARY KEY,  -- 사본 내부 PK
    bcode            VARCHAR(50)  NOT NULL,  -- 도서 바코드 (동일 도서의 여러 사본이 공유)
    content_id       INT,           -- erp_bookstore_content.content_id (어느 마스터 도서인지)
    center_code      VARCHAR(20)  NOT NULL DEFAULT 'PUS002',  -- 사본이 속한 센터 (data-books.sql처럼 센터를 지정하지 않는 시딩 스크립트 호환용 기본값)
    book_title       VARCHAR(255),  -- 도서명 (등록 시점 스냅샷)
    author           VARCHAR(100),  -- 저자
    publisher        VARCHAR(100),  -- 출판사
    image_url        VARCHAR(500),  -- 표지 이미지 URL
    status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE(대여가능) / LOANED(대여중) / LOST(분실·파손)
    last_student_id  VARCHAR(100),  -- 마지막으로 대여한 학생 (erp_student.student_id, FK 없이 값으로만 연결)
    last_loaned_at   DATETIME2,     -- 마지막 대여일시
    last_returned_at DATETIME2,     -- 마지막 반납일시
    registered_at    DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 등록일시
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (center_code)  REFERENCES erp_center(center_code)
);

-- 실물도서 대여 이력 — 사본(item)의 status/last_* 스냅샷의 원장(대여/반납/분실 시 함께 갱신)
IF OBJECT_ID('erp_bookstore_item_loan', 'U') IS NULL
CREATE TABLE erp_bookstore_item_loan (
    loan_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    item_id      INT           NOT NULL,  -- 대여한 실물도서 사본 (erp_bookstore_item.item_id)
    student_id   VARCHAR(100)  NOT NULL,  -- 대여한 학생 (erp_student.student_id)
    loaned_at    DATETIME2     DEFAULT CURRENT_TIMESTAMP,  -- 대여일시
    returned_at  DATETIME2,     -- 반납일시 (미반납이면 NULL)
    status       VARCHAR(20)   NOT NULL DEFAULT 'LOANED',  -- LOANED / RETURNED / LOST
    FOREIGN KEY (item_id) REFERENCES erp_bookstore_item(item_id)
);

-- 실물도서(erp_bookstore_item) 삭제/수정 로그
IF OBJECT_ID('erp_bookstore_item_del', 'U') IS NULL
CREATE TABLE erp_bookstore_item_del (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at        DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    logged_by        VARCHAR(100),  -- 처리한 사용자
    item_id          INT,           -- 원본 item_id
    bcode            VARCHAR(50),   -- 원본 바코드
    content_id       INT,           -- 원본 content_id
    book_title       VARCHAR(255),  -- 도서명
    author           VARCHAR(100),  -- 저자
    publisher        VARCHAR(100),  -- 출판사
    image_url        VARCHAR(500),  -- 표지 이미지 URL
    center_code      VARCHAR(20),   -- 원본 센터 코드
    status           VARCHAR(20),   -- 로그 시점 상태
    last_student_id  VARCHAR(100),  -- 로그 시점 마지막 대여 학생
    last_loaned_at   DATETIME2,     -- 로그 시점 마지막 대여일시
    last_returned_at DATETIME2      -- 로그 시점 마지막 반납일시
);

IF COL_LENGTH('erp_bookstore_item_del', 'del_id') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.del_id', 'log_id', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'deleted_at') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.deleted_at', 'logged_at', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'deleted_by') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.deleted_by', 'logged_by', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'log_type') IS NULL ALTER TABLE erp_bookstore_item_del ADD log_type VARCHAR(10) NOT NULL DEFAULT 'DELETE';

-- 문제은행 — 도서(content_id)별 독후활동 문제 (관리자가 book-data 화면에서 직접 등록/편집)
IF OBJECT_ID('erp_bookstore_itempool', 'U') IS NULL
CREATE TABLE erp_bookstore_itempool (
    content_id     INT,            -- erp_bookstore_content.content_id
    qlevel         VARCHAR(2),     -- 난이도 코드 (erp_bookstore_code gubun='L', 01=기본/02=심화)
    qnum           VARCHAR(20),    -- 문제 번호 (도서+난이도 내 순번)
    q              NVARCHAR(2000), -- 문제 지문
    qex            NVARCHAR(2000), -- 보기/추가 지문
    e1             NVARCHAR(500),  -- 선택지 1
    e2             NVARCHAR(500),  -- 선택지 2
    e3             NVARCHAR(500),  -- 선택지 3
    e4             NVARCHAR(500),  -- 선택지 4
    ans            VARCHAR(100),   -- 정답
    qtype          VARCHAR(2),     -- 문제 유형 코드 (erp_bookstore_code gubun='T', 이해/표현/논리/사고/감정/어휘/지식/문법)
    qexgb          VARCHAR(20),    -- 보기 구분
    state          VARCHAR(20),    -- 사용 여부

    PRIMARY KEY (content_id, qlevel, qnum),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 문제(erp_bookstore_itempool) 삭제/수정 로그
IF OBJECT_ID('erp_bookstore_itempool_del', 'U') IS NULL
CREATE TABLE erp_bookstore_itempool_del (
    log_id         INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type       VARCHAR(10)  NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at      DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    logged_by      VARCHAR(100),  -- 처리한 사용자
    content_id     INT,           -- 원본 content_id
    qnum           VARCHAR(20),   -- 문제 번호
    q              NVARCHAR(2000), -- 문제 지문
    qex            NVARCHAR(2000), -- 보기/추가 지문
    e1             NVARCHAR(500), -- 선택지 1
    e2             NVARCHAR(500), -- 선택지 2
    e3             NVARCHAR(500), -- 선택지 3
    e4             NVARCHAR(500), -- 선택지 4
    ans            VARCHAR(100),  -- 정답
    qtype          VARCHAR(2),    -- 문제 유형 코드
    qlevel         VARCHAR(2),    -- 난이도 코드
    qexgb          VARCHAR(20),   -- 보기 구분
    state          VARCHAR(20)    -- 로그 시점 사용 여부
);

IF COL_LENGTH('erp_bookstore_itempool_del', 'del_id') IS NOT NULL EXEC sp_rename 'erp_bookstore_itempool_del.del_id', 'log_id', 'COLUMN';
IF COL_LENGTH('erp_bookstore_itempool_del', 'deleted_at') IS NOT NULL EXEC sp_rename 'erp_bookstore_itempool_del.deleted_at', 'logged_at', 'COLUMN';
IF COL_LENGTH('erp_bookstore_itempool_del', 'deleted_by') IS NOT NULL EXEC sp_rename 'erp_bookstore_itempool_del.deleted_by', 'logged_by', 'COLUMN';
IF COL_LENGTH('erp_bookstore_itempool_del', 'log_type') IS NULL ALTER TABLE erp_bookstore_itempool_del ADD log_type VARCHAR(10) NOT NULL DEFAULT 'DELETE';

-- 알림 발송 이력 (FCM 등)
CREATE TABLE erp_notification (
    id            INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    sent_at       DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 발송일시
    sent_by       VARCHAR(100),  -- 발송한 사용자
    title         NVARCHAR(200), -- 알림 제목
    body          NVARCHAR(1000),-- 알림 본문
    target_type   VARCHAR(20),   -- 수신 대상 유형 (예: STUDENT)
    target_id     VARCHAR(100),  -- 수신 대상 식별자 (FK 없이 값으로만 연결)
    fcm_token     VARCHAR(500),  -- 발송 시점 FCM 토큰
    status        VARCHAR(10),   -- 발송 상태
    error_msg     VARCHAR(500)   -- 발송 실패 시 에러 메시지
);


-- ────────────────────────────────────────────────────────
-- 학생 독서 클리닉 (student-main 화면) — 1단계(책 추천)부터 재설계 (2026-07-09)
-- 참고: erp_student.student_id 에 UNIQUE 제약이 없어 학생 연결은 FK 없이
--       student_id VARCHAR 값으로만 연결한다 (erp_notification.target_id와 같은 방식)
-- ────────────────────────────────────────────────────────

-- 추천 이력 — "이미 추천받은 책" 판정(재추천 방지)과 "직전 추천 도서의 분류/장르" 조회의 기준이 되는
-- 핵심 테이블. 추천이 확정되는 순간(=실물 대여 확정) 함께 기록된다.
-- status='PENDING'인 동안은 재로그인해도 같은 책이 그대로 나온다(재도전 포함). 기본 문제풀이(qlevel=01)에서
-- 합격선을 넘어 status='DONE'이 되면, 다음 추천 때부터 이 책은 후보에서 계속 제외되고 새 책이 추천된다.
CREATE TABLE erp_bookstore_recommend_log (
    recommend_id    INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id      VARCHAR(100)  NOT NULL,  -- erp_student.student_id
    content_id      INT           NOT NULL,  -- 추천된 도서 (erp_bookstore_content.content_id)
    recommended_at  DATETIME2     DEFAULT CURRENT_TIMESTAMP,  -- 추천일시
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING(문제풀이 전/재도전 대기) / DONE(합격)
    correct_count   INT,      -- 기본 문제풀이(qlevel=01) 최근 제출 정답 수
    total_count     INT,      -- 기본 문제풀이 총 문항 수
    grade           VARCHAR(20),   -- KING(독서왕) / FRIEND(독서친구) — 합격 시에만 값 존재
    completed_at    DATETIME2,     -- 합격(DONE) 처리 시각
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 문제 풀이 이력 — 학생이 문항별로 몇 번 보기를 선택했는지 기록 (2026-07-10)
-- 채점 제출(/clinic/quiz/submit) 1회당 답안 문항 수만큼 적재하며, 재도전 제출도 지우지 않고
-- 모두 남긴다(submitted_at으로 회차 구분). is_correct는 서버 채점 결과 스냅샷.
CREATE TABLE erp_bookstore_quiz_answer_log (
    answer_id     INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    recommend_id  INT           NOT NULL,  -- 어느 추천(도전)에 대한 제출인지 (erp_bookstore_recommend_log)
    student_id    VARCHAR(100)  NOT NULL,  -- erp_student.student_id (FK 없이 값으로만 연결)
    content_id    INT           NOT NULL,  -- 풀이한 도서
    qlevel        VARCHAR(2)    NOT NULL DEFAULT '01',  -- 난이도 코드 (erp_bookstore_code gubun='L', 01=기본/02=심화)
    qnum          VARCHAR(20)   NOT NULL,  -- 문제 번호 (erp_bookstore_itempool.qnum)
    qtype         VARCHAR(2),   -- 문제 유형 코드 스냅샷 (erp_bookstore_code gubun='T') — 제출 시점 itempool.qtype, 이후 문제가 수정/삭제돼도 이력 보존
    selected      INT           NOT NULL,  -- 학생이 선택한 보기 번호 (1~4)
    is_correct    BIT           NOT NULL,  -- 서버 채점 결과 (제출 시점 itempool.ans 기준)
    submitted_at  DATETIME2     DEFAULT CURRENT_TIMESTAMP,  -- 제출일시 (같은 값 = 같은 회차)
    FOREIGN KEY (recommend_id) REFERENCES erp_bookstore_recommend_log(recommend_id),
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id)
);

-- 레벨 체계 재편 (EXP 폐지) — "단계 = 학생 학년(01~06)"이고, 각 단계 안에서 레벨 1~12가 있다.
-- 레벨은 저장하지 않고 "그 학년 도서 완독(DONE) 권수 ÷ 학년별 필요권수"로 매번 재계산한다(ClinicService).
--   level_rule : 단계(학년)별 단계명/특징/레벨업 1회당 필요 완독 권수 — 6개 학년 전부 존재해야 계산 가능
CREATE TABLE erp_bookstore_level_rule (
    schoolyear      VARCHAR(2)    PRIMARY KEY,     -- 01~06 = 1~6단계
    stage_name      NVARCHAR(50),                  -- 단계명 (입문 ...) — 2~6단계는 미정
    feature         NVARCHAR(300),                 -- 단계 특징 문구 — 2~6단계는 미정
    books_per_level INT           NOT NULL         -- 레벨업 1회당 필요 완독 권수 (초1·2=8, 초3=5, 초4~6=4)
);

-- 레벨 칭호 — (단계=학년, 레벨 1~12)별 칭호. 미정 학년은 행이 없어도 되며, 그 경우 화면은 Lv.N만 표시
CREATE TABLE erp_bookstore_level (
    schoolyear    VARCHAR(2)    NOT NULL,          -- 단계 = 학년
    level_no      INT           NOT NULL,          -- 1 ~ 12
    title         NVARCHAR(50)  NOT NULL,          -- 칭호 (독서 씨앗, 독서 새싹 ...)
    PRIMARY KEY (schoolyear, level_no),
    FOREIGN KEY (schoolyear) REFERENCES erp_bookstore_level_rule(schoolyear)
);

-- 뱃지 마스터 (2026-07-10) — 달성 조건을 category+threshold+param 3개 값으로 데이터화한다.
-- 판정 로직은 조건별 if문이 아니라 category 단위 쿼리 하나씩으로 처리 (ClinicService.checkAndAwardBadges)
--   FIRST_BOOK    : 완독(DONE) 권수 >= threshold
--   MONTH_STREAK  : 역대 최장 "연속 완독 개월 수"(달력 월 기준, 매달 1권 이상) >= threshold
--   CROWN         : 독서왕(grade=KING) 달성 횟수 >= threshold
--   QTYPE_PERFECT : param=문제유형 T코드. 합격 회차에서 해당 유형 전 문항 정답인 책 수 >= threshold (책당 1회)
--   TOPIC         : param=콤마 구분 키워드. content.keywords에 키워드가 포함된 완독 책 수 >= threshold
--   ADV_PERFECT   : 심화(qlevel=02) 전 문항 정답 달성 책 수 >= threshold (책당 1회)
--   META          : 다른 뱃지 threshold개 전부 보유 시
CREATE TABLE erp_bookstore_badge (
    badge_id    INT            PRIMARY KEY,      -- 1~22 고정 번호 (기획 문서 순서)
    badge_name  NVARCHAR(50)   NOT NULL,         -- 뱃지 이름 (독서 탐험가 ...)
    badge_desc  NVARCHAR(200),                   -- 달성 조건 문구 (화면 표시용)
    category    VARCHAR(20)    NOT NULL,         -- 판정 유형 (위 주석 참고)
    threshold   INT            NOT NULL,         -- 달성 기준치
    param       VARCHAR(100)                     -- QTYPE_PERFECT=T코드 / TOPIC=키워드 목록
);

-- 학생별 뱃지 획득 이력 — PK로 중복 획득을 원천 차단, 판정은 매 제출마다 로그 재계산(멱등)
-- 학생이 획득한 뱃지 — "책(도서)마다" 부여된다. 책당 기본 1개(참잘했어요/독서친구/독서왕 중 택1) +
-- 심화 1개(심화완료/심화왕 중 택1). 같은 학생이 같은 종류 뱃지를 여러 책에서 얻을 수 있으므로 content_id를 PK에 포함.
CREATE TABLE erp_bookstore_student_badge (
    student_id  VARCHAR(100)  NOT NULL,
    content_id  INT           NOT NULL,   -- 어느 책에서 얻은 뱃지인지
    badge_id    INT           NOT NULL,
    earned_at   DATETIME2     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, content_id, badge_id),
    FOREIGN KEY (badge_id)   REFERENCES erp_bookstore_badge(badge_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 클리닉 입실/퇴실 세션 (2026-07-15 실시간 모니터링) — 학생이 로그인하는 시점에 자동으로
-- 입실 기록이 생긴다. 같은 날 이미 ENTERED 상태 세션이 있으면 재사용하고(재로그인은 새 세션이
-- 아님), 직원이 "퇴실 처리"를 눌러야 EXITED로 바뀐다.
-- 시각 컬럼은 CURRENT_TIMESTAMP 대신 DATEADD(HOUR,9,GETUTCDATE())를 쓴다 — CURRENT_TIMESTAMP는
-- DB 서버 OS의 로컬 타임존을 그대로 따라가서(개발 DB가 UTC로 떠 있으면 그 값이 그대로 저장됨)
-- 화면에 "입실 시각"으로 그대로 노출하면 실제 한국 시각과 어긋난다. GETUTCDATE()는 서버
-- 타임존 설정과 무관하게 항상 UTC를 반환하므로 +9시간 하면 서버 설정에 상관없이 KST가 된다.
CREATE TABLE erp_bookstore_clinic_session (
    session_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id      VARCHAR(100)  NOT NULL,   -- erp_student.student_id (FK 없이 값으로만 연결)
    session_date    DATE          NOT NULL,   -- 입실일 (조회 필터 기준)
    entered_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 입실(로그인)일시(KST)
    exited_at       DATETIME2,    -- 퇴실 처리일시(KST)
    status          VARCHAR(20)   NOT NULL DEFAULT 'ENTERED',  -- ENTERED(입실중) / EXITED(퇴실완료)
    quiz_started_at DATETIME2,    -- 문제풀이 화면 진입 시각(KST) — 채점 제출 시 다시 NULL로 초기화
    result_viewed_at DATETIME2    -- 결과 화면 진입 시각(KST) — 홈으로/재도전 등 화면 이탈 시 NULL로 초기화. "결과 확인중" 카드 상태의 기준
);

-- 클리닉 예약 (2026-07-23 실시간 모니터링 — 예약 기준 미입실/입실 전환) — "해당 타임에 올 예정인
-- 학생" 마스터. 상태 컬럼을 따로 두지 않는다: 같은 (student_id, reservation_date)로 매칭되는
-- erp_bookstore_clinic_session 행이 있으면 입실, 없으면 미입실로 조회 시점에 파생시킨다.
-- 예약 등록 화면은 별도 작업 범위라 아직 없음 — 현재는 시드/수동 INSERT로만 채워짐.
CREATE TABLE erp_bookstore_clinic_reservation (
    reservation_id   INT           IDENTITY(1,1) PRIMARY KEY,
    student_id       VARCHAR(100)  NOT NULL,   -- erp_student.student_id (FK 없이 값으로만 연결)
    reservation_date DATE          NOT NULL,   -- 예약일 (조회 필터 기준)
    time_slot        VARCHAR(10)   NOT NULL,   -- '1'~'4' (monitor-live.js TIME_SLOTS.key와 매칭)
    created_at       DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 독서일지 — 직원이 실시간 모니터링 화면에서 입실 세션 1건당 직접 작성(태도/도움필요/전달사항)
CREATE TABLE erp_bookstore_reading_log (
    log_id         INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    session_id     INT           NOT NULL,  -- erp_bookstore_clinic_session.session_id
    student_id     VARCHAR(100)  NOT NULL,  -- erp_student.student_id
    attitude_codes VARCHAR(200),  -- 독서 태도 체크(콤마 구분 코드, 복수 선택)
    help_needed    VARCHAR(50),   -- 도움 필요 코드 (예: ALONE_HARD=혼자 읽기 어려워요)
    note           VARCHAR(1000), -- 기타 전달사항
    created_by     VARCHAR(100),  -- 작성한 직원
    created_at     DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at     DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    FOREIGN KEY (session_id) REFERENCES erp_bookstore_clinic_session(session_id)
);
