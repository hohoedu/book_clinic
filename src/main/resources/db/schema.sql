-- DROP (FK 역순)
IF OBJECT_ID('erp_bookstore_attitude',              'U') IS NOT NULL DROP TABLE erp_bookstore_attitude;
IF OBJECT_ID('erp_bookstore_attitude_code',         'U') IS NOT NULL DROP TABLE erp_bookstore_attitude_code;
IF OBJECT_ID('erp_bookstore_diary_detail',          'U') IS NOT NULL DROP TABLE erp_bookstore_diary_detail;
IF OBJECT_ID('erp_bookstore_diary',                 'U') IS NOT NULL DROP TABLE erp_bookstore_diary;
IF OBJECT_ID('erp_bookstore_reading_log',           'U') IS NOT NULL DROP TABLE erp_bookstore_reading_log;
IF OBJECT_ID('erp_bookstore_clinic_session',        'U') IS NOT NULL DROP TABLE erp_bookstore_clinic_session;
IF OBJECT_ID('erp_bookstore_clinic_reservation',    'U') IS NOT NULL DROP TABLE erp_bookstore_clinic_reservation;
IF OBJECT_ID('erp_bookstore_student_card',          'U') IS NOT NULL DROP TABLE erp_bookstore_student_card;
IF OBJECT_ID('erp_bookstore_student_badge',         'U') IS NOT NULL DROP TABLE erp_bookstore_student_badge;
IF OBJECT_ID('erp_bookstore_badge',                 'U') IS NOT NULL DROP TABLE erp_bookstore_badge;
IF OBJECT_ID('erp_bookstore_quiz_reset_log',        'U') IS NOT NULL DROP TABLE erp_bookstore_quiz_reset_log;
IF OBJECT_ID('erp_bookstore_quiz_answer_log',       'U') IS NOT NULL DROP TABLE erp_bookstore_quiz_answer_log;
IF OBJECT_ID('erp_bookstore_student_info',          'U') IS NOT NULL DROP TABLE erp_bookstore_student_info;
IF OBJECT_ID('erp_bookstore_exp_rule',               'U') IS NOT NULL DROP TABLE erp_bookstore_exp_rule;      -- 폐지(EXP 제거)
IF OBJECT_ID('erp_bookstore_level',                  'U') IS NOT NULL DROP TABLE erp_bookstore_level;
IF OBJECT_ID('erp_bookstore_recommend_log',         'U') IS NOT NULL DROP TABLE erp_bookstore_recommend_log;
IF OBJECT_ID('erp_notification',                    'U') IS NOT NULL DROP TABLE erp_notification;
IF OBJECT_ID('erp_bookstore_code',                  'U') IS NOT NULL DROP TABLE erp_bookstore_code;
IF OBJECT_ID('erp_bookstore_item_loan',             'U') IS NOT NULL DROP TABLE erp_bookstore_item_loan;
IF OBJECT_ID('erp_bookstore_item',                  'U') IS NOT NULL DROP TABLE erp_bookstore_item;
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
    -- 클리닉(책방)에서 실제로 추천 기준으로 삼는 학년(S코드 01~07) — 올패스 grade_key와 별개다.
    -- grade_key는 올패스와 공유하는 값이라 book_clinic이 함부로 못 건드리는데, "초1인데 독서를
    -- 잘해서 초2 수준 책을 추천받고 싶다" 같은 경우 실제 학년을 안 바꾸고 이 값만 조정하면 된다.
    -- 처음엔 비어있다가 ClinicService.resolveSchoolyear()가 grade_key(올패스 코드)를 book_clinic
    -- 코드로 변환해 최초 1회 채워넣는다(lazy init) — 그 뒤로는 grade_key가 바뀌어도 자동으로
    -- 안 따라간다(2026-08-07).
    clinic_grade_key      VARCHAR(2),
    gender                BIT         DEFAULT 0,  -- 성별
    student_privacy_agree BIT         DEFAULT 0,  -- 개인정보 동의 여부
    is_hoho               BIT         DEFAULT 0,  -- 호호에듀 서비스 가입 여부
    -- 도움 필요 여부 ("혼자 읽기 어려워요") — 하루치 기록이 아니라 풀릴 때까지 유지되는 학생 상태값이다(2026-07-30).
    -- 직원이 실시간 모니터링에서 켜면 다음 수업에도 계속 켜진 채로 보이고, 해제하면 그때부터 안 보인다.
    -- erp_bookstore_diary.help_needed는 "그날 일지에 기록된 값"(스냅샷)이라 해제 후에도 과거 일지에 그대로 남는다.
    help_needed           BIT         NOT NULL DEFAULT 0,
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

-- 실물도서 마스터 (2026-07-29 재설계) — bcode+센터당 1행, qty/loaned_qty 카운터로 보유수량 관리.
-- 사본 1권 = 1행이던 이전 모델은 보유수량을 늘릴 때마다 행을 insert/delete해야 했다. 지금은 qty(총 보유)와
-- loaned_qty(그중 대여중)만 두고, "대여 가능 수량 = qty - loaned_qty - lost_qty"로 계산한다.
-- 대여 확정은 `UPDATE ... SET loaned_qty = loaned_qty + 1 WHERE loaned_qty < qty - lost_qty` 형태의
-- 원자적 UPDATE로 처리해 동시에 여러 학생이 같은 판본을 신청해도 재고 이상으로 나가지 않는다.
-- lost_qty는 분실 처리된 누적 수량(현재 앱 로직에서 갱신하는 경로는 없고 컬럼만 존재 — 수동 조정용).
-- 데이터는 data-items.sql이 매 기동 자동으로 다시 채운다.
CREATE TABLE erp_bookstore_item (
    item_id          INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK (bcode+센터당 1행)
    bcode            VARCHAR(50)  NOT NULL,  -- 도서 바코드 (같은 판본이 여러 센터에 있으면 센터별로 행이 나뉘되 bcode는 공유)
    content_id       INT,           -- erp_bookstore_content.content_id (어느 마스터 도서인지)
    center_code      VARCHAR(20)  NOT NULL DEFAULT 'PUS002',  -- 이 판본이 속한 센터 (data-books.sql처럼 센터를 지정하지 않는 시딩 스크립트 호환용 기본값)
    book_title       VARCHAR(255),  -- 도서명 (등록 시점 스냅샷)
    author           VARCHAR(100),  -- 저자
    publisher        VARCHAR(100),  -- 출판사
    image_url        VARCHAR(500),  -- 표지 이미지 URL
    qty              INT          NOT NULL DEFAULT 0,  -- 총 보유수량 (분실분 포함, 누적 등록량)
    loaned_qty       INT          NOT NULL DEFAULT 0,  -- qty 중 현재 대여중인 수량
    lost_qty         INT          NOT NULL DEFAULT 0,  -- qty 중 분실 처리된 수량
    registered_at    DATETIME2    DEFAULT CURRENT_TIMESTAMP,  -- 최초 등록일시
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (center_code)  REFERENCES erp_center(center_code)
);
CREATE UNIQUE INDEX ux_bookstore_item_bcode_center ON erp_bookstore_item(bcode, center_code);

-- 실물도서 대여 이력 — item_id는 이제 "판본(bcode+센터) 행"을 가리킨다 (사본 개별 식별자가 아님).
-- 같은 item_id를 참조하는 LOANED 이력이 여러 건 동시에 있을 수 있으며, 그 개수가 item.loaned_qty와 항상 같아야 한다.
IF OBJECT_ID('erp_bookstore_item_loan', 'U') IS NULL
CREATE TABLE erp_bookstore_item_loan (
    loan_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    item_id      INT           NOT NULL,  -- 대여한 판본 (erp_bookstore_item.item_id)
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
    qty              INT,           -- 로그 시점 총 보유수량
    loaned_qty       INT,           -- 로그 시점 대여중 수량
    lost_qty         INT            -- 로그 시점 분실 수량
);

IF COL_LENGTH('erp_bookstore_item_del', 'del_id') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.del_id', 'log_id', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'deleted_at') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.deleted_at', 'logged_at', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'deleted_by') IS NOT NULL EXEC sp_rename 'erp_bookstore_item_del.deleted_by', 'logged_by', 'COLUMN';
IF COL_LENGTH('erp_bookstore_item_del', 'log_type') IS NULL ALTER TABLE erp_bookstore_item_del ADD log_type VARCHAR(10) NOT NULL DEFAULT 'DELETE';

-- 2026-07-29 재설계(사본 1행→bcode+센터당 1행 qty 카운터) 마이그레이션 — 기존 DB에 새 컬럼 추가 후 옛 컬럼 제거
IF COL_LENGTH('erp_bookstore_item_del', 'qty') IS NULL ALTER TABLE erp_bookstore_item_del ADD qty INT;
IF COL_LENGTH('erp_bookstore_item_del', 'loaned_qty') IS NULL ALTER TABLE erp_bookstore_item_del ADD loaned_qty INT;
IF COL_LENGTH('erp_bookstore_item_del', 'lost_qty') IS NULL ALTER TABLE erp_bookstore_item_del ADD lost_qty INT;
IF COL_LENGTH('erp_bookstore_item_del', 'status') IS NOT NULL ALTER TABLE erp_bookstore_item_del DROP COLUMN status;
IF COL_LENGTH('erp_bookstore_item_del', 'last_student_id') IS NOT NULL ALTER TABLE erp_bookstore_item_del DROP COLUMN last_student_id;
IF COL_LENGTH('erp_bookstore_item_del', 'last_loaned_at') IS NOT NULL ALTER TABLE erp_bookstore_item_del DROP COLUMN last_loaned_at;
IF COL_LENGTH('erp_bookstore_item_del', 'last_returned_at') IS NOT NULL ALTER TABLE erp_bookstore_item_del DROP COLUMN last_returned_at;

-- 센터 보유 수량 변경 로그 (2026-07-28 보유도서 설정 화면) — 화면의 +/- 는 저장 버튼 없이 즉시
-- 사본(erp_bookstore_item) 행을 추가/삭제하므로, "언제 몇 권에서 몇 권이 됐는지"는 사본 테이블만으로는
-- 복원되지 않는다. 최근 변경일/변경 이력은 전적으로 이 로그를 근거로 표시한다.
IF OBJECT_ID('erp_bookstore_item_stock_log', 'U') IS NULL
CREATE TABLE erp_bookstore_item_stock_log (
    log_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    content_id  INT           NOT NULL,  -- erp_bookstore_content.content_id (수량은 도서 단위로 관리)
    center_code VARCHAR(20)   NOT NULL,  -- 수량이 바뀐 센터
    before_qty  INT           NOT NULL,  -- 변경 전 보유 수량
    after_qty   INT           NOT NULL,  -- 변경 후 보유 수량
    memo        NVARCHAR(500),           -- 감소 사유 (일괄 등록에서 수량을 줄일 때 필수 입력, 늘릴 때는 NULL)
    changed_by  VARCHAR(100),            -- 처리한 직원 (erp_user.username)
    changed_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 변경일시(KST)
    FOREIGN KEY (content_id)  REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
);
IF COL_LENGTH('erp_bookstore_item_stock_log', 'memo') IS NULL ALTER TABLE erp_bookstore_item_stock_log ADD memo NVARCHAR(500);

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
    content_id      INT           NOT NULL,  -- 추천된 도서 (erp_bookstore_content.content_id) — 문제(itempool)는 이 기준
    item_id         INT           NOT NULL,  -- 실제로 대여 확정된 실물 판본 (erp_bookstore_item.item_id).
                                              -- 추천 자체가 이제 item(판본) 단위다(2026-07-30) — 같은 content라도
                                              -- item이 다르면 다른 학생에게 각각 추천될 수 있고(재고만큼), 이 학생
                                              -- 기준 중복배제(dedup)도 content가 아니라 item_id로 판단한다.
    recommended_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 추천일시(KST)
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING(문제풀이 전/재도전 대기) / DONE(합격)
    correct_count   INT,      -- 기본 문제풀이(qlevel=01) 최근 제출 정답 수
    total_count     INT,      -- 기본 문제풀이 총 문항 수
    grade           VARCHAR(20),   -- KING(독서왕) / FRIEND(독서친구) — 합격 시에만 값 존재
    completed_at    DATETIME2,     -- 합격(DONE) 처리 시각
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (item_id)    REFERENCES erp_bookstore_item(item_id)
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
    submitted_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 제출일시(KST) (같은 값 = 같은 회차)
    FOREIGN KEY (recommend_id) REFERENCES erp_bookstore_recommend_log(recommend_id),
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id)
);

-- 문제풀이 기록 삭제 이력 (2026-07-31) — 학생 요청으로 직원이 모니터링 화면에서 그 책의 풀이 기록을
-- 초기화하면(MonitorService.resetQuiz) 지워지는 값들의 스냅샷을 여기 남긴다. 원본 행(quiz_answer_log,
-- student_badge, student_card)은 실제로 삭제되므로, "왜 뱃지가 사라졌나" 같은 문의를 이 표로 답한다.
-- 다른 _del 로그와 같은 규칙으로 FK 없이 값으로만 연결한다(원본 행이 이미 없어서 FK를 걸 수 없다).
CREATE TABLE erp_bookstore_quiz_reset_log (
    log_id        INT IDENTITY(1,1) PRIMARY KEY,
    logged_at     DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 삭제 처리 시각(KST)
    logged_by     VARCHAR(50),                                           -- 처리한 직원 (로그인 계정명)
    -- RESET  = 삭제 대상 책. recommend_log 행은 PENDING으로 남아 다시 풀 수 있다
    -- CANCEL = 그 뒤에 추천받은 책. 되돌아가려면 비켜줘야 해서 recommend_log 행까지 지운다
    log_type      VARCHAR(10)   NOT NULL DEFAULT 'RESET',
    recommend_id  INT           NOT NULL,  -- 초기화한 추천(도전) — 행 자체는 PENDING으로 남는다
    student_id    VARCHAR(100)  NOT NULL,
    content_id    INT           NOT NULL,
    -- 초기화 직전 recommend_log 스냅샷
    correct_count INT,
    total_count   INT,
    grade         VARCHAR(20),  -- KING / FRIEND
    status        VARCHAR(20),  -- PENDING / DONE
    -- 실제로 지운 행 수
    answer_rows   INT,          -- quiz_answer_log
    badge_rows    INT,          -- student_badge (그 책에서 딴 뱃지)
    card_rows     INT           -- student_card (NORMAL + 회수로 무효가 된 RARE)
);

-- 레벨 체계 재편 (EXP 폐지) — "단계 = 학생 학년(01~06)"이고, 각 단계 안에서 레벨 1~12가 있다.
-- 레벨은 저장하지 않고 "그 학년 도서 완독(DONE) 권수 ÷ 학년별 필요권수"로 매번 재계산한다(ClinicService).
-- 단계명/특징/필요권수(구 level_rule)는 어드민 편집 화면이 없고 배포로만 바뀌는 값이라 DB 대신
-- ClinicService.LEVEL_RULES(Java 상수)로 관리한다 — level 테이블은 칭호만 담당.

-- 레벨 칭호 — (단계=학년, 레벨 1~12)별 칭호. 미정 학년은 행이 없어도 되며, 그 경우 화면은 Lv.N만 표시
CREATE TABLE erp_bookstore_level (
    schoolyear    VARCHAR(2)    NOT NULL,          -- 단계 = 학년
    level_no      INT           NOT NULL,          -- 1 ~ 12
    title         NVARCHAR(50)  NOT NULL,          -- 칭호 (독서 씨앗, 독서 새싹 ...)
    PRIMARY KEY (schoolyear, level_no)
);

-- 뱃지 마스터 (2026-07-27 재편) — 5종 고정. id→이름/설명 조회용 룩업 테이블.
--   1 참 잘했어요 / 2 독서친구 / 3 독서왕 / 4 심화 완료 / 5 심화왕
-- 판정은 "책마다 첫 시도 결과"로 코드에서 badge_id를 직접 매핑한다(ClinicService.awardBasicBadge/awardAdvancedBadge).
--   기본 첫 시도: 불합격→1 / 합격→2 / 만점→3,  심화 첫 시도: 합격→4 / 만점→5 (불합격은 없음)
-- category/threshold/param 컬럼은 구(누적 판정) 방식의 잔재로 현재 로직에서 사용하지 않음(호환 위해 유지).
CREATE TABLE erp_bookstore_badge (
    badge_id    INT            PRIMARY KEY,      -- 1~5 고정 번호
    badge_name  NVARCHAR(50)   NOT NULL,         -- 뱃지 이름 (참 잘했어요 ...)
    badge_desc  NVARCHAR(200),                   -- 특징/설명 문구 (화면 표시용)
    category    VARCHAR(20)    NOT NULL,         -- (레거시) 판정 유형 — 현재 미사용
    threshold   INT            NOT NULL,         -- (레거시) 달성 기준치 — 현재 미사용
    param       VARCHAR(100)                     -- (레거시) 현재 미사용
);

-- 학생별 뱃지 획득 이력 — PK로 중복 획득을 원천 차단, 판정은 매 제출마다 로그 재계산(멱등)
-- 학생이 획득한 뱃지 — "책(도서)마다" 부여된다. 책당 기본 1개(참잘했어요/독서친구/독서왕 중 택1) +
-- 심화 1개(심화완료/심화왕 중 택1). 같은 학생이 같은 종류 뱃지를 여러 책에서 얻을 수 있으므로 content_id를 PK에 포함.
CREATE TABLE erp_bookstore_student_badge (
    student_id  VARCHAR(100)  NOT NULL,
    content_id  INT           NOT NULL,   -- 어느 책에서 얻은 뱃지인지
    badge_id    INT           NOT NULL,
    earned_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 획득일시(KST)
    PRIMARY KEY (student_id, content_id, badge_id),
    FOREIGN KEY (badge_id)   REFERENCES erp_bookstore_badge(badge_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 학생별 카드 지급 이력 (2026-07-28) — NORMAL(완독 시 그 책 카드, 책당 1장) / RARE(NORMAL 카드
-- 10장마다 추가 지급, 특정 책과 무관해서 content_id는 NULL) 두 종류를 한 테이블에서 관리한다.
-- id를 surrogate PK로 두는 이유: RARE는 content_id가 없어 (student_id, content_id)로 유일성을 못 잡는다.
-- NORMAL 중복 지급 방지는 UX_erp_bookstore_student_card_normal(student_id, content_id) 필터드 유니크로 막는다.
-- RARE 중복 지급 방지는 trigger_count(그 RARE를 발생시킨 시점의 누적 NORMAL 카드 수, 10/20/30 ...)로 판단한다.
CREATE TABLE erp_bookstore_student_card (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    student_id     VARCHAR(100)  NOT NULL,
    content_id     INT           NULL,      -- NORMAL만 값 있음(그 책). RARE는 NULL
    card_type      VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',  -- NORMAL / RARE
    trigger_count  INT           NULL,      -- RARE만 값 있음(발급을 유발한 누적 NORMAL 카드 수: 10, 20 ...)
    earned_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 지급일시(KST)
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);
CREATE UNIQUE INDEX UX_erp_bookstore_student_card_normal
    ON erp_bookstore_student_card (student_id, content_id)
    WHERE card_type = 'NORMAL';
CREATE UNIQUE INDEX UX_erp_bookstore_student_card_rare
    ON erp_bookstore_student_card (student_id, trigger_count)
    WHERE card_type = 'RARE';

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

-- ────────────────────────────────────────────────────────
-- 독서일지 재설계 (2026-07-28) — 구 erp_bookstore_reading_log 폐기하고 대체
--   diary(하루 1건) → diary_detail(그날 읽은 책별) / attitude(태도 체크 복수 선택)
-- 구 테이블은 삭제했고 상단 DROP 문만 남겨 기존 개발 DB의 잔재를 정리한다.
--   note → diary.memo / help_needed → diary.help_needed / attitude_codes(콤마 문자열) → attitude 행 분해
-- 세 테이블 모두 매 기동 DROP 후 재생성한다. 보존으로 두면 매 기동 리셋되는
-- erp_bookstore_clinic_session / erp_bookstore_recommend_log를 FK로 물고 있어 그쪽 DROP이 막힌다.
-- ────────────────────────────────────────────────────────

-- 독서일지 헤더 — 학생의 하루(입실 1회)에 1건.
-- in_time/out_time은 세션(entered_at/exited_at)과 같은 값이라 session_id로 세션을 물고,
-- 두 컬럼은 "일지 작성 시점 스냅샷"으로 둔다(직원이 화면에서 보정할 수 있어야 해서 파생이 아닌 저장).
-- record_time은 erp_bookstore_clinic_reservation.time_slot과 같은 도메인('1'~'4')을 쓴다.
CREATE TABLE erp_bookstore_diary (
    diary_key    INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK (기존 관례는 _id지만 설계안 이름을 유지)
    session_id   INT           NOT NULL UNIQUE,  -- erp_bookstore_clinic_session.session_id (입실 세션 1건 = 일지 1건)
    student_id   VARCHAR(20)  NOT NULL,  -- erp_student.student_id (UNIQUE 제약이 없어 FK 없이 값으로 연결)
    record_date  DATE          NOT NULL,  -- 일지 기준일 (= 세션의 session_date)
    record_time  VARCHAR(1),              -- 교시 '1'~'4' (clinic_reservation.time_slot과 동일 도메인)
    in_time      DATETIME2,               -- 입실 시각(KST) — 세션 entered_at 스냅샷, 직원 보정 가능
    out_time     DATETIME2,               -- 퇴실 시각(KST) — 세션 exited_at 스냅샷, 직원 보정 가능
    -- 도움 필요 여부 — "그날 일지에 기록된 값"(스냅샷)이다. 현재 상태는 erp_student.help_needed가
    -- 들고 있고(풀릴 때까지 유지되는 학생 상태값), 일지 생성 시 그 값을 복사해 넣는다.
    -- 그래서 직원이 상태를 해제해도 이미 작성된 과거 일지의 값은 그대로 남는다(2026-07-30).
    help_needed  BIT           NOT NULL DEFAULT 0,
    memo         VARCHAR(500),            -- 전달사항 (구 reading_log.note)
    is_send      BIT           NOT NULL DEFAULT 0,  -- 학부모 발송 여부 — 발송 결과/이력은 erp_notification에 남긴다
    send_at      DATETIME2,                         -- 발송 처리 시각(KST) — is_send=1로 바뀐 시점 스냅샷
    created_by   VARCHAR(50),             -- 작성한 직원 (구 reading_log.created_by)
    created_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    FOREIGN KEY (session_id) REFERENCES erp_bookstore_clinic_session(session_id)
);
CREATE INDEX IX_erp_bookstore_diary_student_date
    ON erp_bookstore_diary (student_id, record_date);

-- 독서일지 상세 — 그날 읽은 책 1권당 1행.
-- basic_*/advanced_* 정답 수는 erp_bookstore_quiz_answer_log(원장)에서 집계되는 값의 스냅샷이다.
-- 어느 도전의 결과인지는 recommend_id로 물린다. 기본(qlevel='01')은 recommend_log에도 같은 값이 있지만
-- 심화(qlevel='02')는 recommend_log에 컬럼이 없어 quiz_answer_log 집계로만 채울 수 있다.
-- read_minutes: erp_bookstore_content.reading_time이 이미 "예상 독서 시간"이라 이름이 겹쳐 실제 측정값은 분 단위로 따로 둔다.
CREATE TABLE erp_bookstore_diary_detail (
    id                   INT  IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    diary_key            INT  NOT NULL,  -- erp_bookstore_diary.diary_key
    content_id           INT  NOT NULL,  -- erp_bookstore_content.content_id
    recommend_id         INT,            -- erp_bookstore_recommend_log.recommend_id (어느 도전의 결과인지)
    book_name            VARCHAR(255),   -- 도서명 (작성 시점 스냅샷 — erp_bookstore_item과 같은 규칙)
    book_img             VARCHAR(100),   -- 표지 이미지 URL (작성 시점 스냅샷)
    read_minutes         INT,            -- 실제 독서 시간(분) — content.reading_time(예상)과 다른 값
    basic_correct_cnt    INT,            -- 기본(qlevel='01') 정답 수 스냅샷
    basic_total_cnt      INT,            -- 기본 총 문항 수 스냅샷
    advanced_correct_cnt INT,            -- 심화(qlevel='02') 정답 수 스냅샷
    advanced_total_cnt   INT,            -- 심화 총 문항 수 스냅샷
    CONSTRAINT UQ_erp_bookstore_diary_detail_book UNIQUE (diary_key, content_id),
    FOREIGN KEY (diary_key)    REFERENCES erp_bookstore_diary(diary_key),
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (recommend_id) REFERENCES erp_bookstore_recommend_log(recommend_id)
);

-- 독서태도 코드 마스터 (2026-07-29) — 태도 문구가 바뀔 수 있어 monitor-live.js에 하드코딩하지 않고
-- DB로 뺐다. erp_bookstore_code로 옮기지 않는 이유는 그대로 유지(그 테이블의 code가 VARCHAR(2)라
-- GOOD_POSTURE 같은 문자열 코드가 들어가지 않고, use_yn/수정이력 같은 컬럼도 없음).
-- 화면(monitor-live.js)은 /admin/monitor/live 응답의 attitudeCodeOptions(use_yn=1만)로 체크박스를 그려서,
-- 이 테이블 값을 고치면 재배포 없이 바로 반영된다.
CREATE TABLE erp_bookstore_attitude_code (
    id            INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK(표시 순서 기준)
    attitude_code VARCHAR(20)   NOT NULL UNIQUE,   -- 태도 코드값 (erp_bookstore_attitude.attitude_code가 참조)
    attitude_name VARCHAR(100)  NOT NULL,           -- 태도명(화면 표시 문구)
    use_yn        BIT           NOT NULL DEFAULT 1, -- 사용여부
    created_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_by    VARCHAR(50)                       -- 수정한 사람
);

-- 독서태도 체크 — 일지 1건에 복수 선택되므로 1선택 = 1행으로 정규화한다(구 reading_log.attitude_codes 콤마 문자열 대체).
-- attitude_code는 erp_bookstore_attitude_code.attitude_code를 참조한다.
CREATE TABLE erp_bookstore_attitude (
    id            INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    diary_key     INT           NOT NULL,  -- erp_bookstore_diary.diary_key (어느 일지의 체크인지)
    student_id    VARCHAR(20)   NOT NULL,  -- erp_student.student_id (조회 편의용 중복 저장, 값으로만 연결)
    attitude_code VARCHAR(20)   NOT NULL,  -- 독서 태도 코드
    created_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UQ_erp_bookstore_attitude_pair UNIQUE (diary_key, attitude_code),
    FOREIGN KEY (diary_key) REFERENCES erp_bookstore_diary(diary_key),
    FOREIGN KEY (attitude_code) REFERENCES erp_bookstore_attitude_code(attitude_code)
);


-- ════════════════════════════════════════════════════════
-- 이용권 / 결제 / 환불 (2026-08-04 재설계, KG이니시스)
--
-- [상품] 월 N회 횟수권. 출석(입실) 1회당 1회 차감되고 소진되면 끝난다.
-- 자동 재결제(빌키)는 없고 할부도 받지 않는다(이니시스 quotabase로 일시불 고정).
-- 그래서 빌키·할부 관련 컬럼이 없다.
--
-- [핵심 구조 — 이용권과 결제의 분리] 프로그램비를 걷는 방법이 학생에 따라 다르다.
--   · 책방만 이용   → 모바일 앱에서 학부모가 PG(이니시스)로 직접 결제
--   · 서당 병행     → 교재비에 프로그램비를 얹어 전월 20일 일괄 청구 (별도 프로젝트 all_pass 소관)
-- 두 경우 모두 "이 학생이 이번 달 몇 회 쓸 수 있는가"는 똑같이 필요하지만, 서당 학생은
-- 이 시스템에 결제 행 자체가 생기지 않는다. 그래서 횟수를 payment에 두면 서당 학생을
-- 표현할 수 없다. 이용권(erp_bookstore_pass)을 먼저 두고, PG 결제는 그 이용권이 생긴
-- 사유 중 하나로 매단다. 무상 부여·프로모션이 나중에 생겨도 이용권 쪽은 그대로다.
--
-- 이용권이 "어디서 온 횟수인지"를 source + ref_no 한 쌍으로 직접 들고 있는다.
--   source='PG'      ref_no = erp_bookstore_payment.order_no  (이 DB 안)
--   source='SEODANG' ref_no = all_pass의 청구 식별자(bill_id)  (다른 시스템)
--   source='FREE'    ref_no = 부여 근거(품의번호 등) 또는 NULL
-- 결제 쪽에서 이용권을 가리키지 않고 이용권이 결제를 가리키게 한 이유는, 서당 청구 행이
-- 이 DB에 없어서 반대 방향으로는 FK든 조인이든 경로가 두 갈래로 갈리기 때문이다.
-- 대신 ref_no에는 FK를 걸 수 없다(서당분은 외부 키다). PG분은 order_no가 UNIQUE라
-- 조인 자체는 안전하고, 짝이 안 맞는 건은 정산 대조 배치로 잡는다.
--
-- [PG 결제 흐름] Flutter가 이니시스 모바일 SDK로 인증(카드 선택/본인확인)까지만 하고, 그 결과를
-- Spring으로 넘긴다. 승인 API 호출은 반드시 서버가 한다 — 앱이 보낸 "결제 성공"만 믿으면
-- 위변조된 금액으로 결제 완료 처리가 되기 때문이다.
--   1) 서버: order_no 발급 + 금액은 product에서 읽어 status=READY로 선(先)기록
--   2) 앱  : 이니시스 인증 → authToken/authUrl을 서버로 전달
--   3) 서버: 승인 요청 → 응답 금액이 1)의 amount와 같은지 검증 → 다르면 즉시 망취소 후 FAILED
--   4) 서버: status=PAID + pass 행 발급(remain_count 충전), 원문 로그 적재 후 결과 반환
--
-- [환불] 앱에서 신청하면 규정(refund_rule)이 자동 적용돼 PG 취소까지 간다. 신청과 실행이
-- 1:1이라 신청 테이블을 따로 두지 않는다. 서당 청구분은 이 시스템에서 환불하지 않는다
-- (돈을 all_pass가 받았으므로 환불도 그쪽이다). 그래서 환불 도메인은 PG 결제 전용이다.
--   ※ "직원 승인 후 환불" 같은 심사 단계가 생기면 그때는 신청 테이블을 분리해야 한다.
--     반려·재신청이 생기면서 신청 1건 : 취소 N건이 되기 때문이다.
--
-- [컬럼을 늘리지 않는 이유] 이니시스 응답 필드를 전부 컬럼으로 펼치지 않는다. 화면에 쓰지
-- 않는 값은 erp_bookstore_payment_log의 원문(res_body)에 이미 다 들어 있어서, 컬럼으로
-- 또 꺼내면 같은 데이터를 두 군데 관리하게 된다.
--
-- [스냅샷] pass/payment는 product를 FK로 물지만 상품명·금액·제공횟수를 값으로도 복사해 둔다.
-- 본사가 가격을 올리면 과거 결제내역 금액까지 같이 올라가면 안 되기 때문이다.
--
-- [erp_student.sub_book/sub_hoho] 그 BIT는 이 테이블들에서 파생되는 캐시다. 화면들이 이미
-- BIT를 보고 있어서 남기되, 진짜 정답은 항상 유효한 pass의 remain_count 합계다.
--
-- [DROP 제외] 실제 돈이 오간 기록이고 상품·규정은 본사가 화면에서 관리하는 마스터라
-- 매 기동 리셋하면 안 된다. 파일 상단 DROP 목록에 넣지 말고 IF OBJECT_ID(...) IS NULL로만
-- 생성한다(erp_center와 같은 취급). 인덱스도 같은 이유로 sys.indexes 존재 확인을 감싼다.
--
-- [FK] student_id / center_code는 다른 테이블과 같은 관례로 값으로만 연결한다.
-- 결제 도메인 안(product↔pass↔use↔payment↔cancel)에서만 FK를 거는데,
-- 이 관계의 정합성이 곧 금액 정합성이라서다.
-- ════════════════════════════════════════════════════════

-- 상품 마스터 — 서비스별 횟수권. 본사가 관리자 화면에서 가격/횟수를 고친다.
-- 서당 일괄청구분도 같은 상품을 쓴다. 청구 방법이 다를 뿐 제공하는 이용권은 같기 때문이다.
-- 가격 변경 이력은 두지 않는다. 과거 결제는 payment의 스냅샷이 지키고 있어서,
-- 여기에 이력까지 쌓으면 같은 사실이 두 군데가 된다.
IF OBJECT_ID('erp_bookstore_product', 'U') IS NULL
CREATE TABLE erp_bookstore_product (
    product_id   INT          IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    product_code VARCHAR(20)  NOT NULL UNIQUE,  -- 상품 코드 (예: BOOK_M8). 화면/설정에서 상품을 지목하는 키
    product_name VARCHAR(50)  NOT NULL,         -- 상품명 (예: 도서 클리닉 월 8회권)
    service_code VARCHAR(10)  NOT NULL,         -- BOOK(도서) / HOHO(호호). erp_student.sub_book/sub_hoho에 대응
    total_count  SMALLINT     NOT NULL,         -- 제공 횟수. pass.remain_count의 시작값이 된다
    price        INT          NOT NULL,         -- 판매가(원). 서당 일괄청구도 이 금액을 교재비에 얹는다
    is_active    BIT          NOT NULL DEFAULT 1,  -- 판매중 여부. 단종 상품도 과거 결제가 참조하므로 삭제하지 않고 내린다
    created_at   DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 환불 규정 — "결제 후 며칠 이내 / 몇 회 이하로 썼으면 몇 % 환불"을 본사가 관리한다.
-- 적용은 priority 오름차순으로 훑어 조건에 처음 맞는 한 건만 쓴다(여러 규정이 겹쳐도 결과가 하나로 정해지게).
-- 어디에도 안 걸리면 환불 불가로 본다. PG 결제분에만 적용된다.
--
-- [개정] 기존 행을 고치면 그 규정으로 환불해 준 과거 건의 근거가 사라진다.
-- 규정이 바뀌면 기존 행은 is_active=0으로 내리고 새 rule_code로 새 행을 넣는다.
IF OBJECT_ID('erp_bookstore_refund_rule', 'U') IS NULL
CREATE TABLE erp_bookstore_refund_rule (
    rule_id     INT          IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    rule_code   VARCHAR(20)  NOT NULL UNIQUE,  -- 규정 코드. payment_cancel에 스냅샷으로 남는 값
    rule_name   VARCHAR(50),                   -- 화면 표시용 설명 (예: 7일 이내 미사용 전액환불)
    max_days    SMALLINT     NOT NULL,         -- 결제일로부터 이 일수 이내일 것
    max_count   SMALLINT     NOT NULL,         -- 사용 횟수가 이 값 이하일 것
    refund_rate SMALLINT     NOT NULL,         -- 환불율(%). 결제금액 기준이며 원 단위 절사는 서버가 처리한다
    priority    SMALLINT     NOT NULL,         -- 적용 순서. 작을수록 먼저 검사한다
    is_active   BIT          NOT NULL DEFAULT 1,  -- 현행 규정 여부. 개정된 규정도 과거 근거라 삭제하지 않는다
    created_at  DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 이용권 — "이 학생이 몇 회 남았나"의 단일 정답. 책방 학생(PG 결제)과 서당 학생(일괄청구)이
-- 같은 모양으로 들어오고, source로만 갈린다. 출석 차감은 이 테이블만 보면 되므로
-- 차감 로직에 결제 방식 분기가 들어가지 않는다.
IF OBJECT_ID('erp_bookstore_pass', 'U') IS NULL
CREATE TABLE erp_bookstore_pass (
    pass_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id   VARCHAR(100)  NOT NULL,       -- erp_student.student_id (FK 없이 값으로만 연결)
    center_code  VARCHAR(50),                  -- 소속 센터 (정산/조회 필터용 중복 저장)
    product_id   INT           NOT NULL,       -- erp_bookstore_product.product_id
    service_code VARCHAR(10)   NOT NULL,       -- 상품의 service_code 스냅샷. "이 학생 도서 잔여횟수"가 가장 잦은
                                               -- 조회라 매번 product를 조인하지 않으려고 값으로도 복사해 둔다
    source       VARCHAR(10)   NOT NULL,       -- PG(앱 카드결제) / SEODANG(서당 일괄청구) / FREE(본사 무상부여)
    ref_no       VARCHAR(40),                  -- 이 횟수가 발생한 근거의 식별자. source에 따라 가리키는 곳이 다르다.
                                               -- PG=erp_bookstore_payment.order_no / SEODANG=all_pass 청구 bill_id.
                                               -- 외부 시스템 키가 섞여 있어 FK를 걸 수 없다
    billing_ym   CHAR(6),                      -- 이 이용권이 청구된 년월(YYYYMM). 서당 일괄청구는 전월 20일에
                                               -- 다음 달치를 걷으므로 "언제 청구된 몫인지"가 결제일과 다르다.
                                               -- all_pass 청구 내역과 대조하는 키라서 PG 건에도 같은 규칙으로 채운다
    valid_from   DATE          NOT NULL,       -- 이 이용권이 적용되는 달의 1일(billing_ym에서 파생).
                                               -- SQL에서 CHAR(6) 문자열을 매번 파싱하지 않고 날짜로 바로
                                               -- 비교하려고 별도 컬럼으로 둔다(2026-08-06, 월 단위 유효기간 도입)
    valid_until  DATE          NOT NULL,       -- 그 달의 마지막 날. 오늘이 이 범위 밖이면 remain_count가
                                               -- 남아 있어도 못 쓴다 — 이월 없이 월말 소멸한다는 정책
    total_count  SMALLINT      NOT NULL,       -- 지급된 총 횟수 (product.total_count 스냅샷)
    remain_count SMALLINT      NOT NULL,       -- 잔여 횟수. 출석마다 1씩 깐다.
                                               -- pass_use 건수와 total_count - remain_count가 항상 같아야 한다.
                                               -- 매번 세지 않으려고 캐시로 둔 것이라 차감 트랜잭션에서 같이 갱신한다.
                                               -- 환불되면 잔여를 회수해 0으로 만든다
    granted_at   DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 지급일시(KST)
    revoked_at   DATETIME2,                    -- 회수일시(KST). 환불·청구취소로 무효가 된 이용권.
                                               -- NULL이 아니면 remain_count가 남아 있어도 쓸 수 없다
    FOREIGN KEY (product_id) REFERENCES erp_bookstore_product(product_id)
);

-- 마이그레이션: valid_from/valid_until 도입 이전에 이미 만들어져 있는 개발 DB용.
-- 기존 행은 billing_ym(없으면 granted_at의 월)에서 그 달의 1일/말일을 역산해 채운다.
-- BEGIN/END로 묶지 않고 독립된 문장으로 나눈다 — 이 프로젝트의 SQL 스크립트 실행기는
-- 세미콜론 단위로 문장을 쪼개서 실행하는데, BEGIN/END 블록 안에 세미콜론이 여러 개 있으면
-- 블록 중간에서 잘려 "Incorrect syntax" 오류가 난다(2026-08-06 확인).
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_from')
    ALTER TABLE erp_bookstore_pass ADD valid_from DATE NULL, valid_until DATE NULL;

UPDATE erp_bookstore_pass
SET valid_from  = DATEFROMPARTS(LEFT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 4),
                                 RIGHT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 2), 1),
    valid_until = EOMONTH(DATEFROMPARTS(LEFT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 4),
                                         RIGHT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 2), 1))
WHERE valid_from IS NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_from' AND is_nullable = 1)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_from DATE NOT NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_until' AND is_nullable = 1)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_until DATE NOT NULL;

-- 출석할 때마다 타는 경로 — 살아있는 이용권만 보면 되므로 필터드 인덱스로 좁힌다.
-- 여러 달치가 겹칠 수 있게 되면서(월 단위 유효기간 도입) 소진 순서 기준이 granted_at(먼저 산 것)에서
-- valid_until(먼저 만료되는 것)로 바뀌었다 — 인덱스도 실제 조회/정렬 조건에 맞춰 갈아탄다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_student' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    DROP INDEX IX_pass_student ON erp_bookstore_pass;
CREATE INDEX IX_pass_student ON erp_bookstore_pass (student_id, service_code, valid_until)
    WHERE revoked_at IS NULL;

-- 결제/청구 건에서 이용권을 되짚는 경로 (환불 시 회수할 이용권 찾기, 정산 대조)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_ref' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    CREATE INDEX IX_pass_ref ON erp_bookstore_pass (source, ref_no);

-- 서당 일괄청구분을 월별로 대조할 때 쓴다 (이번 달 청구한 인원 vs 실제 지급된 이용권)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_billing' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    CREATE INDEX IX_pass_billing ON erp_bookstore_pass (billing_ym, source, center_code);

-- 횟수 차감 이력 — 출석(입실) 1회당 1행. 환불 규정의 "몇 회 이하로 썼는가"를 이 테이블로 센다.
--
-- [clinic_session을 세지 않는 이유] 출석 자체는 erp_bookstore_clinic_session에 남지만
-- 그 테이블은 매 기동 DROP 대상이다. 사용 횟수를 거기서 세면 리셋 한 번에 환불 금액이 틀어진다.
-- 돈에 영향을 주는 카운트는 리셋되지 않는 곳에 따로 남겨야 한다. session_id는 추적용으로만
-- 값으로 들고, 리셋되면 사라지는 값이라 FK는 걸지 않는다.
--
-- [하루 1회] 같은 날 재입실해도 차감은 1회다. 재로그인·되돌아온 학생에게 두 번 까이면 안 되므로
-- UNIQUE로 막는다. 서비스가 둘 이상으로 늘면 이 키에 service_code를 넣어야 한다.
IF OBJECT_ID('erp_bookstore_pass_use', 'U') IS NULL
CREATE TABLE erp_bookstore_pass_use (
    use_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    pass_id     INT           NOT NULL,       -- erp_bookstore_pass.pass_id (어느 이용권을 깠는지)
    student_id  VARCHAR(100)  NOT NULL,       -- erp_student.student_id (조회 편의용 중복 저장)
    session_id  INT,                          -- erp_bookstore_clinic_session.session_id (값으로만 연결, 추적용)
    used_date   DATE          NOT NULL,       -- 차감일 (하루 1회 판정 기준)
    created_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UQ_pass_use_daily UNIQUE (student_id, used_date),
    FOREIGN KEY (pass_id) REFERENCES erp_bookstore_pass(pass_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_use_pass' AND object_id = OBJECT_ID('erp_bookstore_pass_use'))
    CREATE INDEX IX_pass_use_pass ON erp_bookstore_pass_use (pass_id);

-- PG 결제 — 책방만 이용하는 학생의 앱 카드결제. 서당 학생은 여기 행이 생기지 않는다.
-- 행은 결제 "시작" 시점에 status=READY로 먼저 생긴다(승인 실패/이탈 건도 남아야 정산 대조가 된다).
-- 승인 성공 시 이용권이 발급되며, 그 연결은 pass.ref_no = 이 행의 order_no로 맺는다.
IF OBJECT_ID('erp_bookstore_payment', 'U') IS NULL
CREATE TABLE erp_bookstore_payment (
    payment_id    INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    order_no      VARCHAR(40)   NOT NULL UNIQUE,  -- 가맹점 주문번호(이니시스 oid). 서버 생성, 이니시스 제한이 40자
    group_order_no VARCHAR(40),                   -- 형제 묶음결제 시 공통 그룹 주문번호. 단일결제는 항상 NULL.
                                                  -- PG에는 그룹 주문번호 1개로 결제 1건만 승인 요청하고, 이 컬럼으로
                                                  -- 같은 그룹에 속한 학생별 payment 행들을 되짚는다.
    tid           VARCHAR(40),                    -- 이니시스 거래번호. 환불 API에 넘기는 키.
                                                  -- 승인 전에는 NULL이라 NOT NULL 불가.
                                                  -- 중복 승인 차단은 아래 필터드 UNIQUE 인덱스가 담당한다
                                                  -- (컬럼에 UNIQUE를 걸면 SQL Server는 NULL을 한 행만 허용해서
                                                  --  두 번째 READY 행부터 INSERT가 깨진다)
    student_id    VARCHAR(100)  NOT NULL,         -- erp_student.student_id (FK 없이 값으로만 연결)
    center_code   VARCHAR(50),                    -- 결제한 학생의 소속 센터 (정산/조회 필터용 중복 저장)
    product_id    INT           NOT NULL,         -- erp_bookstore_product.product_id
    product_name  VARCHAR(50),                    -- 결제 시점 상품명 스냅샷
    -- 상품의 service_code 스냅샷(2026-08-07) — 아래 "같은 학생·서비스·청구월 중복 결제 방지"
    -- 유니크 인덱스에 쓴다. 필터드 유니크 인덱스는 조인을 못 걸어서 값으로 복사해 둬야 한다
    -- (pass 테이블의 service_code 스냅샷과 같은 이유).
    service_code  VARCHAR(10),
    billing_ym    CHAR(6),                        -- 이 결제가 몇 월치 이용권인지(YYYYMM). prepare()/prepareGroup()
                                                  -- 시점에 PassService.nextBillingYm()으로 정해서 넣고, 승인 확정
                                                  -- 때 이 값을 그대로 이용권에 옮긴다(그 사이 재계산하면 화면에
                                                  -- 보여준 달과 실제 발급된 달이 어긋날 수 있다. 2026-08-06)
    amount        INT           NOT NULL,         -- 결제 금액(원) = 결제 시점 가격 스냅샷.
                                                  -- 승인 응답 금액과 이 값을 대조해 위변조를 검증한다.
                                                  -- 검증을 통과해야만 PAID가 되므로 "승인금액" 컬럼을 따로 두지 않는다
    refund_amount INT           NOT NULL DEFAULT 0,  -- 누적 환불 금액. payment_cancel의 status='DONE' 합계와 항상 같아야 한다.
                                                     -- 잔액은 amount - refund_amount로 나온다
    status        VARCHAR(10)   NOT NULL DEFAULT 'READY',
        -- READY(결제창 띄움) / PAID(승인완료) / CLOSED(승인 시도 전 이탈 — X버튼/뒤로가기/방치)
        -- / FAILED(승인실패·망취소) / CANCELED(전액취소)
        -- CLOSED와 FAILED를 나누는 이유: 전자는 "안 샀다"이고 후자는 "사려다 실패했다"라서
        -- 정산·문의 응대에서 뜻이 다르다. 카드사 거절/시스템 오류만 FAILED로 남아야
        -- 실제 장애 로그를 사용자 단순 이탈과 섞어보지 않는다.
        -- 부분취소는 별도 상태값을 두지 않는다. status='PAID' + refund_amount > 0 이면 부분취소이고,
        -- 상태값으로 또 들고 있으면 refund_amount와 어긋날 수 있는 지점이 하나 더 늘기 때문이다
    pay_method    VARCHAR(10),                    -- 이니시스 payMethod. 현재는 Card만 받지만 값은 응답대로 저장한다
    card_name     VARCHAR(30),                    -- 카드사명 (결제내역 화면 표시용)
    card_no       VARCHAR(20),                    -- 마스킹된 카드번호. 원본 번호는 절대 저장하지 않는다
    appl_no       VARCHAR(30),                    -- 카드 승인번호 (카드사 문의 시 필요)
    result_code   VARCHAR(10),                    -- 이니시스 resultCode ('0000'=성공). 실패 건을 걸러내는 용도.
                                                  -- 실패 사유 원문은 payment_log.res_body를 본다
    requested_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 결제 시작일시(KST)
    paid_at       DATETIME2,                      -- 승인 완료일시(KST). 환불 규정의 "며칠 이내"가 이 값 기준이다
    -- 운영자 수동 확인 필요 플래그(2026-08-07) — 금액 불일치·망취소 실패·승인 확정 실패처럼 코드가
    -- 스스로 못 끝내고 사람이 이니시스 상점관리자에서 직접 봐야 하는 상태를 표시한다. 예전엔 로그에만
    -- 남아서 아무도 안 보면 그대로 묻혔다. 관리자 화면(/admin/payment/review-view)이 이 플래그가 선
    -- 건만 모아 보여주고, 처리 완료 시 reviewed_at을 채워 목록에서 빠지게 한다.
    needs_review  BIT           NOT NULL DEFAULT 0,
    review_reason VARCHAR(200),                   -- 왜 확인이 필요한지(예: "승인 금액 불일치 — 수동 취소 필요")
    reviewed_at   DATETIME2,                      -- 운영자가 처리 완료로 표시한 시각. NULL이면 미해결
    -- 동시 환불 요청 경합 방지용 선점 표시(2026-08-07). "환불 안 됨"을 확인만 하고 PG 호출까지
    -- 가는 사이 락이 없으면 거의 동시에 두 번 요청됐을 때 PG 취소가 이중으로 나갈 수 있다.
    -- PaymentService.refund()가 원자적 UPDATE(claimRefund)로 이 값을 채워 선점하고, 처리가
    -- 끝나면(성공/실패 무관) 다시 NULL로 되돌린다 — 성공은 refund_amount>0이 영구 차단하므로
    -- 이 값 자체는 재시도를 막을 필요가 없다.
    refund_requested_at DATETIME2,
    -- CLOSED 사후 재확인 완료 표시(2026-08-07). 고객이 이니시스 결제창에서 딴짓하다 10분을 넘겨
    -- 우리 배치가 CLOSED로 닫았는데, 그 직후 실제로 승인을 완료해버리는 레이스가 있을 수 있다.
    -- PaymentCleanupJob이 최근 CLOSED된 주문을 한 번 더 거래조회해서 실제로는 승인이었으면
    -- PAID로 복구하고, 이 값을 채워 같은 주문을 계속 재확인하지 않게 한다.
    closed_recheck_at DATETIME2,
    FOREIGN KEY (product_id) REFERENCES erp_bookstore_product(product_id)
);

-- 마이그레이션: erp_bookstore_payment가 group_order_no 도입 이전에 이미 만들어져 있는 개발 DB용.
-- 위 CREATE TABLE은 테이블이 아예 없을 때만 실행되므로(이 DB는 매 기동 초기화되지 않는 테이블도 있다),
-- 기존 테이블에는 컬럼을 별도로 추가해야 인덱스 생성이 실패하지 않는다.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'group_order_no')
    ALTER TABLE erp_bookstore_payment ADD group_order_no VARCHAR(40);

-- 마이그레이션: billing_ym(월 단위 유효기간) 도입 이전에 이미 만들어져 있는 개발 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'billing_ym')
    ALTER TABLE erp_bookstore_payment ADD billing_ym CHAR(6);

-- 마이그레이션: needs_review(운영자 수동 확인 플래그) 도입 이전에 이미 만들어져 있는 개발 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'needs_review')
    ALTER TABLE erp_bookstore_payment ADD needs_review BIT NOT NULL DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'review_reason')
    ALTER TABLE erp_bookstore_payment ADD review_reason VARCHAR(200);
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'reviewed_at')
    ALTER TABLE erp_bookstore_payment ADD reviewed_at DATETIME2;

-- 마이그레이션: refund_requested_at(동시 환불 경합 방지 선점 컬럼) 도입 이전에 이미 만들어져 있는 개발 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'refund_requested_at')
    ALTER TABLE erp_bookstore_payment ADD refund_requested_at DATETIME2;

-- 마이그레이션: closed_recheck_at(CLOSED 사후 재확인 완료 표시) 도입 이전에 이미 만들어져 있는 개발 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'closed_recheck_at')
    ALTER TABLE erp_bookstore_payment ADD closed_recheck_at DATETIME2;

-- 마이그레이션: service_code(상품 서비스코드 스냅샷) 도입 이전에 이미 만들어져 있는 개발 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'service_code')
    ALTER TABLE erp_bookstore_payment ADD service_code VARCHAR(10);

-- 같은 학생·서비스·청구월에 진행 중(READY)이거나 완료(PAID)된 결제가 동시에 2개 이상 있지
-- 못하게 막는다(2026-08-07). A기기로 결제창을 열어두고 방치한 사이 B기기로 새로 결제해서
-- 같은 달 결제가 이중으로 승인되는 사고를 막는 최종 방어선 — 애플리케이션 체크(PaymentService.
-- prepare/prepareGroup)만으로는 두 기기가 동시에 요청하는 순간의 레이스를 완전히 못 막는다.
-- CLOSED/FAILED/CANCELED는 걸리지 않으므로 재시도(새 결제)는 항상 가능하다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_active_billing' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE UNIQUE INDEX UX_payment_active_billing ON erp_bookstore_payment (student_id, service_code, billing_ym)
        WHERE status IN ('READY', 'PAID');

-- (2026-08-06 폐지) tid 유니크 인덱스는 형제 묶음결제와 양립할 수 없어 제거했다 — PG 승인은
-- 그룹당 1건만 나는데, 그 tid를 학생 수만큼의 payment 행에 그대로 복사해 남기므로 같은 tid를
-- 가진 행이 여러 개 있는 게 정상이다. 이중 승인 방어는 markPaid의 WHERE status='READY'
-- 조건이 이미 담당한다(재시도하면 0행 갱신되어 자연히 막힌다) — tid 유니크는 원래도 보조 방어선일
-- 뿐이었다. 이미 만들어진 개발/운영 DB에는 인덱스가 남아 있을 수 있어 제거 마이그레이션을 둔다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_tid' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    DROP INDEX UX_payment_tid ON erp_bookstore_payment;

-- 결제내역 조회는 "이 학생의 결제 목록을 최신순"이 대부분이라 정렬까지 인덱스에 태운다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_student' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_student ON erp_bookstore_payment (student_id, requested_at DESC);

-- 센터별 기간 정산용
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_center_paid' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_center_paid ON erp_bookstore_payment (center_code, paid_at);

-- 승인까지 못 간 READY 방치분을 배치로 정리하기 위한 인덱스
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_status' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_status ON erp_bookstore_payment (status, requested_at);

-- 형제 묶음결제 승인 시 group_order_no로 그룹 내 학생별 payment 행을 되짚기 위한 인덱스
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_group_order' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_group_order ON erp_bookstore_payment (group_order_no) WHERE group_order_no IS NOT NULL;

-- 학생 형제(가족) 묶음 — 결제창에서 형제를 함께 보여주고 합산 결제할 때 쓴다.
-- 자동 매칭 로직 없음: 형제 등록은 사람이 직접 INSERT한다(전화번호 자동 그룹핑 같은 것 없음).
-- student_id는 erp_bookstore_payment/erp_bookstore_pass와 동일한 이유로 FK를 걸지 않는다
-- (erp_student.student_id에 UNIQUE 제약이 없어 FK 대상이 될 수 없다).
IF OBJECT_ID('erp_student_sibling', 'U') IS NULL
CREATE TABLE erp_student_sibling (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    sibling_key  VARCHAR(40)  NOT NULL,   -- 형제 그룹 키. 자동생성 없음 — 등록할 때 사람이 정한다
                                          -- (예: 대표 학생의 student_id를 그대로 키로 쓴다)
    student_id   VARCHAR(100) NOT NULL,   -- erp_student.student_id (FK 없이 값으로만 연결)
    created_at   DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UX_sibling_key_student UNIQUE (sibling_key, student_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_sibling_student' AND object_id = OBJECT_ID('erp_student_sibling'))
    CREATE INDEX IX_sibling_student ON erp_student_sibling (student_id);

-- 환불(취소) 내역 — 부분환불과 재시도가 있어 결제 1건에 N행이다. PG 결제분 전용이다.
-- 이번 취소가 부분인지 전액인지는 cancel_amount와 payment.amount - payment.refund_amount 비교로 나오므로
-- is_partial 같은 플래그를 두지 않는다.
IF OBJECT_ID('erp_bookstore_payment_cancel', 'U') IS NULL
CREATE TABLE erp_bookstore_payment_cancel (
    cancel_id     INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    payment_id    INT           NOT NULL,         -- erp_bookstore_payment.payment_id (원거래 tid는 여기서 조인해 얻는다)
    cancel_amount INT           NOT NULL,         -- 이번에 취소한 금액 (규정 적용 결과)
    cancel_tid    VARCHAR(40),                    -- 이니시스가 돌려주는 취소 거래번호. 부분환불은 건별로 따로 나와서
                                                  -- 정산 대조 때 원거래 tid만으로는 어느 건인지 못 가린다
    reason        VARCHAR(100),                   -- 취소 사유 (이니시스 cancelmsg로 전달, 필수 파라미터라 비워둘 수 없음)
    requested_by  VARCHAR(50),                    -- 처리자 erp_user.user_code. 앱에서 학생/보호자가 신청했으면 'APP'
    -- 아래 3개는 환불 규정을 적용한 "그 시점의 근거" 스냅샷이다. 나중에 다시 계산하면 사용 횟수가
    -- 늘어 있고 규정도 개정돼 있어서 같은 답이 안 나온다. 환불 분쟁은 "왜 이 금액인가"를 증명하는
    -- 일이라 결과 금액만 남기면 방어가 안 된다.
    rule_code     VARCHAR(20),                    -- 적용한 erp_bookstore_refund_rule.rule_code.
                                                  -- 규정이 개정돼도 과거 건은 이 코드로 근거를 찾는다
    used_days     SMALLINT,                       -- paid_at부터 취소 요청일까지 경과일수
    used_count    SMALLINT,                       -- 그 시점까지의 pass_use 건수
    status        VARCHAR(10)   NOT NULL DEFAULT 'REQ',  -- REQ(요청) / DONE(취소완료) / FAIL(취소실패)
    result_code   VARCHAR(10),                    -- 이니시스 resultCode
    result_msg    VARCHAR(200),                   -- 취소 실패 사유. 앱/관리자 화면에 바로 보여줘야 해서
                                                  -- payment와 달리 컬럼으로 둔다
    requested_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 취소 요청일시(KST)
    canceled_at   DATETIME2,                      -- 취소 완료일시(KST). status=DONE일 때만 채워짐
    FOREIGN KEY (payment_id) REFERENCES erp_bookstore_payment(payment_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_cancel_payment' AND object_id = OBJECT_ID('erp_bookstore_payment_cancel'))
    CREATE INDEX IX_payment_cancel_payment ON erp_bookstore_payment_cancel (payment_id);

-- PG 통신 원문 로그 — 승인/취소 요청·응답 본문을 그대로 남긴다.
-- 결제 분쟁은 "우리가 뭘 보냈고 이니시스가 뭘 답했나"를 증명하는 싸움이라 파싱된 컬럼만으로는 부족하다.
-- 위 테이블들에서 컬럼을 덜어낼 수 있는 것도 원문이 여기 남기 때문이다.
-- 카드번호·인증정보가 섞여 들어오므로 저장 전에 마스킹한 뒤 넣는다.
IF OBJECT_ID('erp_bookstore_payment_log', 'U') IS NULL
CREATE TABLE erp_bookstore_payment_log (
    log_id      BIGINT         IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK (결제 1건당 여러 행이라 BIGINT)
    order_no    VARCHAR(40)    NOT NULL,        -- 결제 행이 아직 없는 시점의 로그도 추적되도록 order_no로 묶는다.
                                                -- payment_id는 order_no로 조인하면 나오므로 중복해서 들지 않는다
    tid         VARCHAR(40),
    log_type    VARCHAR(20)    NOT NULL,        -- APPROVE(승인) / NET_CANCEL(망취소) / CANCEL(환불) / INQUIRY(거래조회)
                                                -- 금액 불일치로 망취소를 때렸는데 그마저 실패하면 카드사에는 승인이 남는다.
                                                -- log_type='NET_CANCEL' + 실패 건은 따로 모니터링해서 수동 취소해야 한다
    http_status SMALLINT,                       -- HTTP 응답 코드. 타임아웃 등 응답 자체가 없으면 NULL
    result_code VARCHAR(10),                    -- 이니시스 resultCode (실패 로그만 빠르게 걸러내려고 별도 컬럼)
    req_body    NVARCHAR(MAX),                  -- 요청 본문(마스킹 후)
    res_body    NVARCHAR(MAX),                  -- 응답 본문(마스킹 후)
    created_at  DATETIME2      NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_log_order' AND object_id = OBJECT_ID('erp_bookstore_payment_log'))
    CREATE INDEX IX_payment_log_order ON erp_bookstore_payment_log (order_no, created_at);
