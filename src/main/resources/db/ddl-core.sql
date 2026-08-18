-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 배포용 DDL — 독서클리닉 핵심 도메인 (2026-08-06)
--
-- [왜 이 파일이 따로 있나]
-- application-prod.yml에는 spring.sql.init 설정이 없어서 운영에서는 schema.sql이
-- 전혀 실행되지 않는다(ddl-auto도 validate). 그래서 이 프로젝트의 독서클리닉 관련
-- 테이블(추천/문제풀이/일지/뱃지/실물도서재고 등)은 운영 DB에 아직 하나도 없다.
-- schema.sql 상단에는 이 테이블들을 매 기동 DROP하는 구문이 있는데, 그건 dev 전용이고
-- 그 파일을 통째로 운영에서 돌리면 erp_student/erp_user까지 DROP된다 — 그 두 테이블은
-- all_pass(서당)와 공유하는 운영 데이터라 절대 손대면 안 된다(ddl-payment.sql과 같은 이유).
-- 그래서 DROP이 한 줄도 없고 erp_student/erp_user/erp_center도 건드리지 않는
-- 이 파일을 따로 둔다.
--
-- [erp_student / erp_user / erp_center를 이 파일에 넣지 않은 이유]
-- 세 테이블은 이미 운영 DB에 실데이터(학생/직원/센터)가 있는 기존 테이블이다.
-- schema.sql에서도 이 세 테이블 중 erp_center는 IF OBJECT_ID(...) IS NULL로 보호돼
-- 있지만, 굳이 이 파일에 다시 넣어 실수로라도 손댈 여지를 만들지 않는다.
--
-- [erp_bookstore_content / erp_bookstore_item 등 마스터 데이터]
-- 이 파일은 테이블 구조(껍데기)만 만든다. 도서 목록(data-books.sql), 문제은행
-- (data-itempool.sql) 같은 실제 데이터는 이 파일 실행 후 별도로 넣어야 한다
-- (project_db_seed_structure 메모 참고 — content/item/itempool/center는 dev에서도
-- 매 기동 리셋 대상이 아니라 수동 실행 스크립트로 채운다).
--
-- [사용법] 운영 DB에 이 파일만 실행한다(ddl-payment.sql과는 독립적으로, 순서 무관).
-- 모든 구문이 IF OBJECT_ID(...) IS NULL / IF NOT EXISTS로 감싸여 있어 여러 번
-- 실행해도 안전하다. 2026-08-06 시점 기준 운영 DB에는 이 도메인 테이블이 전혀
-- 없는 상태라(독서클리닉 기능 자체가 아직 운영에서 돈 적이 없음) 컬럼 단위
-- 마이그레이션 없이 최신 구조 그대로 생성한다.
--
-- [주의] 이후 이 도메인이 운영에서 실제로 돌기 시작한 뒤에 schema.sql을 고치면,
-- 그 변경분(컬럼 추가 등)은 이 파일에도 ddl-payment.sql과 같은 방식(IF NOT EXISTS
-- 가드를 건 ALTER TABLE ADD)으로 반드시 반영해야 한다. 그렇지 않으면 그 변경은
-- 운영에 영원히 반영되지 않는다(운영은 schema.sql을 실행하지 않으므로).
--
-- [원본] src/main/resources/db/schema.sql의 독서클리닉 섹션과 같은 내용이다.
-- 스키마를 고칠 일이 있으면 두 파일을 함께 고쳐야 한다.
-- ════════════════════════════════════════════════════════════════════

-- 공통 코드 테이블 — gubun(구분)별 코드/코드명 목록 (C=분류, G=장르, S=학년 등 화면 select/뱃지 표시에 공용으로 사용)
IF OBJECT_ID('erp_bookstore_code', 'U') IS NULL
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

-- 권장도서 순위 초안 (연도+학년별로 여러 건 저장 가능, 그중 하나만 적용 상태)
IF OBJECT_ID('erp_bookstore_priority_draft', 'U') IS NULL
CREATE TABLE erp_bookstore_priority_draft (
    draft_id    INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    year        VARCHAR(4)  NOT NULL,  -- 노출 연도 (예: '2026', '2027')
    schoolyear  VARCHAR(2)  NOT NULL,  -- 학년 코드 (S코드) — 이 초안이 어느 학년 탭 편집본인지
    is_active   VARCHAR(1)  NOT NULL DEFAULT 'N',  -- 이 연도+학년에 실제 적용할 초안인지 (연도+학년당 최대 1건 'Y')
    created_by  VARCHAR(100),  -- 저장한 사용자 이름
    created_at  DATETIME2   DEFAULT CURRENT_TIMESTAMP  -- 저장일시
);

-- 초안별 순위 내용 — 학년은 content.schoolyear로 이미 정해지므로 별도 컬럼 없이 content_id로 조인해서 판별
IF OBJECT_ID('erp_bookstore_priority', 'U') IS NULL
CREATE TABLE erp_bookstore_priority (
    draft_id    INT         NOT NULL,  -- erp_bookstore_priority_draft.draft_id
    content_id  INT         NOT NULL,  -- erp_bookstore_content.content_id
    sort_order  INT         NOT NULL,  -- 순위 (1부터, 추천 로직이 이 순서로 스캔)
    PRIMARY KEY (draft_id, content_id),
    FOREIGN KEY (draft_id)   REFERENCES erp_bookstore_priority_draft(draft_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 순위 초안 삭제 로그 (복구 기능 없음 — 삭제 시 로그만 남기고 끝)
IF OBJECT_ID('erp_bookstore_priority_draft_del', 'U') IS NULL
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
IF OBJECT_ID('erp_bookstore_priority_del', 'U') IS NULL
CREATE TABLE erp_bookstore_priority_del (
    log_id      INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    log_type    VARCHAR(10) NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at   DATETIME2   DEFAULT CURRENT_TIMESTAMP,  -- 로그 일시
    draft_id    INT,        -- 원본 draft_id
    content_id  INT,        -- 원본 content_id
    sort_order  INT         -- 원본 순위
);

-- 실물도서 마스터 (2026-07-29 재설계) — bcode+센터당 1행, qty/loaned_qty 카운터로 보유수량 관리.
-- 데이터는 data-items.sql이 채운다(운영에서는 이 파일 실행 후 수동으로 채워야 함).
IF OBJECT_ID('erp_bookstore_item', 'U') IS NULL
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_bookstore_item_bcode_center' AND object_id = OBJECT_ID('erp_bookstore_item'))
    CREATE UNIQUE INDEX ux_bookstore_item_bcode_center ON erp_bookstore_item(bcode, center_code);

-- 실물도서 대여 이력 — item_id는 "판본(bcode+센터) 행"을 가리킨다(사본 개별 식별자가 아님).
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

-- 센터 보유 수량 변경 로그 (2026-07-28 보유도서 설정 화면)
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

-- 문제은행 — 도서(content_id)별 독후활동 문제
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

-- 알림 발송 이력 (FCM 등)
IF OBJECT_ID('erp_notification', 'U') IS NULL
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
-- 학생 독서 클리닉 (student-main 화면)
-- 참고: erp_student.student_id 에 UNIQUE 제약이 없어 학생 연결은 FK 없이
--       student_id VARCHAR 값으로만 연결한다 (erp_notification.target_id와 같은 방식)
-- ────────────────────────────────────────────────────────

-- 추천 이력 — "이미 추천받은 책" 판정(재추천 방지)과 "직전 추천 도서의 분류/장르" 조회의 기준
IF OBJECT_ID('erp_bookstore_recommend_log', 'U') IS NULL
CREATE TABLE erp_bookstore_recommend_log (
    recommend_id    INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id      VARCHAR(100)  NOT NULL,  -- erp_student.student_id
    content_id      INT           NOT NULL,  -- 추천된 도서 (erp_bookstore_content.content_id) — 문제(itempool)는 이 기준
    item_id         INT           NOT NULL,  -- 실제로 대여 확정된 실물 판본 (erp_bookstore_item.item_id)
    recommended_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 추천일시(KST)
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING(문제풀이 전/재도전 대기) / DONE(합격)
    correct_count   INT,      -- 기본 문제풀이(qlevel=01) 최근 제출 정답 수
    total_count     INT,      -- 기본 문제풀이 총 문항 수
    grade           VARCHAR(20),   -- KING(독서왕) / FRIEND(독서친구) — 합격 시에만 값 존재
    completed_at    DATETIME2,     -- 합격(DONE) 처리 시각
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (item_id)    REFERENCES erp_bookstore_item(item_id)
);

-- 문제 풀이 이력 — 학생이 문항별로 몇 번 보기를 선택했는지 기록
IF OBJECT_ID('erp_bookstore_quiz_answer_log', 'U') IS NULL
CREATE TABLE erp_bookstore_quiz_answer_log (
    answer_id     INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    recommend_id  INT           NOT NULL,  -- 어느 추천(도전)에 대한 제출인지 (erp_bookstore_recommend_log)
    student_id    VARCHAR(100)  NOT NULL,  -- erp_student.student_id (FK 없이 값으로만 연결)
    content_id    INT           NOT NULL,  -- 풀이한 도서
    qlevel        VARCHAR(2)    NOT NULL DEFAULT '01',  -- 난이도 코드 (erp_bookstore_code gubun='L', 01=기본/02=심화)
    qnum          VARCHAR(20)   NOT NULL,  -- 문제 번호 (erp_bookstore_itempool.qnum)
    qtype         VARCHAR(2),   -- 문제 유형 코드 스냅샷 (erp_bookstore_code gubun='T')
    selected      INT           NOT NULL,  -- 학생이 선택한 보기 번호 (1~4)
    is_correct    BIT           NOT NULL,  -- 서버 채점 결과 (제출 시점 itempool.ans 기준)
    submitted_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 제출일시(KST) (같은 값 = 같은 회차)
    FOREIGN KEY (recommend_id) REFERENCES erp_bookstore_recommend_log(recommend_id),
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id)
);

-- 문제풀이 기록 삭제 이력 — 학생 요청으로 직원이 모니터링 화면에서 초기화하면 지워지는 값들의 스냅샷
IF OBJECT_ID('erp_bookstore_quiz_reset_log', 'U') IS NULL
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

-- 레벨 칭호 — (단계=학년, 레벨 1~12)별 칭호. 미정 학년은 행이 없어도 되며, 그 경우 화면은 Lv.N만 표시
IF OBJECT_ID('erp_bookstore_level', 'U') IS NULL
CREATE TABLE erp_bookstore_level (
    schoolyear    VARCHAR(2)    NOT NULL,          -- 단계 = 학년
    level_no      INT           NOT NULL,          -- 1 ~ 12
    title         NVARCHAR(50)  NOT NULL,          -- 칭호 (독서 씨앗, 독서 새싹 ...)
    PRIMARY KEY (schoolyear, level_no)
);

-- 뱃지 마스터 — 5종 고정. id→이름/설명 조회용 룩업 테이블
IF OBJECT_ID('erp_bookstore_badge', 'U') IS NULL
CREATE TABLE erp_bookstore_badge (
    badge_id    INT            PRIMARY KEY,      -- 1~5 고정 번호
    badge_name  NVARCHAR(50)   NOT NULL,         -- 뱃지 이름 (참 잘했어요 ...)
    badge_desc  NVARCHAR(200),                   -- 특징/설명 문구 (화면 표시용)
    category    VARCHAR(20)    NOT NULL,         -- (레거시) 판정 유형 — 현재 미사용
    threshold   INT            NOT NULL,         -- (레거시) 달성 기준치 — 현재 미사용
    param       VARCHAR(100)                     -- (레거시) 현재 미사용
);

-- 학생별 뱃지 획득 이력 — "책(도서)마다" 부여된다
IF OBJECT_ID('erp_bookstore_student_badge', 'U') IS NULL
CREATE TABLE erp_bookstore_student_badge (
    student_id  VARCHAR(100)  NOT NULL,
    content_id  INT           NOT NULL,   -- 어느 책에서 얻은 뱃지인지
    badge_id    INT           NOT NULL,
    earned_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 획득일시(KST)
    PRIMARY KEY (student_id, content_id, badge_id),
    FOREIGN KEY (badge_id)   REFERENCES erp_bookstore_badge(badge_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- 학생별 카드 지급 이력 — NORMAL(완독 시 그 책 카드, 책당 1장) / RARE(NORMAL 카드 10장마다 추가 지급)
IF OBJECT_ID('erp_bookstore_student_card', 'U') IS NULL
CREATE TABLE erp_bookstore_student_card (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    student_id     VARCHAR(100)  NOT NULL,
    content_id     INT           NULL,      -- NORMAL만 값 있음(그 책). RARE는 NULL
    card_type      VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',  -- NORMAL / RARE
    trigger_count  INT           NULL,      -- RARE만 값 있음(발급을 유발한 누적 NORMAL 카드 수: 10, 20 ...)
    earned_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 지급일시(KST)
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_normal' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    CREATE UNIQUE INDEX UX_erp_bookstore_student_card_normal
        ON erp_bookstore_student_card (student_id, content_id)
        WHERE card_type = 'NORMAL';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_rare' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    CREATE UNIQUE INDEX UX_erp_bookstore_student_card_rare
        ON erp_bookstore_student_card (student_id, trigger_count)
        WHERE card_type = 'RARE';

-- 클리닉 입실/퇴실 세션 (실시간 모니터링) — 학생이 로그인하는 시점에 자동으로 입실 기록이 생긴다
IF OBJECT_ID('erp_bookstore_clinic_session', 'U') IS NULL
CREATE TABLE erp_bookstore_clinic_session (
    session_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id      VARCHAR(100)  NOT NULL,   -- erp_student.student_id (FK 없이 값으로만 연결)
    session_date    DATE          NOT NULL,   -- 입실일 (조회 필터 기준)
    entered_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 입실(로그인)일시(KST)
    exited_at       DATETIME2,    -- 퇴실 처리일시(KST)
    status          VARCHAR(20)   NOT NULL DEFAULT 'ENTERED',  -- ENTERED(입실중) / EXITED(퇴실완료)
    quiz_started_at DATETIME2,    -- 문제풀이 화면 진입 시각(KST) — 채점 제출 시 다시 NULL로 초기화
    result_viewed_at DATETIME2    -- 결과 화면 진입 시각(KST) — 홈으로/재도전 등 화면 이탈 시 NULL로 초기화
);

-- 클리닉 예약 — DEPRECATED (2026-08-18). erp_bookstore_reservation(+ slot_instance)로 이관 완료.
-- 신규 설치에서는 만들지 않는다. 기존 환경의 실 테이블/데이터는 별도 정리 전까지 그대로 둔다 —
-- 이 CREATE 문을 지운다고 이미 만들어진 테이블이 없어지지는 않는다(IF OBJECT_ID NULL 가드라 신규
-- 설치에만 영향).
-- IF OBJECT_ID('erp_bookstore_clinic_reservation', 'U') IS NULL
-- CREATE TABLE erp_bookstore_clinic_reservation (
--     reservation_id   INT           IDENTITY(1,1) PRIMARY KEY,
--     student_id       VARCHAR(100)  NOT NULL,
--     reservation_date DATE          NOT NULL,
--     time_slot        VARCHAR(10)   NOT NULL,
--     created_at       DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
-- );

-- 독서일지 헤더 — 학생의 하루(입실 1회)에 1건
IF OBJECT_ID('erp_bookstore_diary', 'U') IS NULL
CREATE TABLE erp_bookstore_diary (
    diary_key    INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    session_id   INT           NOT NULL UNIQUE,  -- erp_bookstore_clinic_session.session_id (입실 세션 1건 = 일지 1건)
    student_id   VARCHAR(20)  NOT NULL,  -- erp_student.student_id (UNIQUE 제약이 없어 FK 없이 값으로 연결)
    record_date  DATE          NOT NULL,  -- 일지 기준일 (= 세션의 session_date)
    record_time  VARCHAR(2),              -- 회차 번호(slot_instance.seq) — 2026-08-18 신규 예약 이관
    in_time      DATETIME2,               -- 입실 시각(KST) — 세션 entered_at 스냅샷, 직원 보정 가능
    out_time     DATETIME2,               -- 퇴실 시각(KST) — 세션 exited_at 스냅샷, 직원 보정 가능
    help_needed  BIT           NOT NULL DEFAULT 0,  -- 도움 필요 여부(그날 일지 스냅샷)
    memo         VARCHAR(500),            -- 전달사항
    is_send      BIT           NOT NULL DEFAULT 0,  -- 학부모 발송 여부
    send_at      DATETIME2,                         -- 발송 처리 시각(KST)
    created_by   VARCHAR(50),             -- 작성한 직원
    created_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at   DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    FOREIGN KEY (session_id) REFERENCES erp_bookstore_clinic_session(session_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_erp_bookstore_diary_student_date' AND object_id = OBJECT_ID('erp_bookstore_diary'))
    CREATE INDEX IX_erp_bookstore_diary_student_date
        ON erp_bookstore_diary (student_id, record_date);

-- 독서일지 상세 — 그날 읽은 책 1권당 1행
IF OBJECT_ID('erp_bookstore_diary_detail', 'U') IS NULL
CREATE TABLE erp_bookstore_diary_detail (
    id                   INT  IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    diary_key            INT  NOT NULL,  -- erp_bookstore_diary.diary_key
    content_id           INT  NOT NULL,  -- erp_bookstore_content.content_id
    recommend_id         INT,            -- erp_bookstore_recommend_log.recommend_id (어느 도전의 결과인지)
    book_name            VARCHAR(255),   -- 도서명 (작성 시점 스냅샷)
    book_img             VARCHAR(100),   -- 표지 이미지 URL (작성 시점 스냅샷)
    read_minutes         INT,            -- 실제 독서 시간(분)
    basic_correct_cnt    INT,            -- 기본(qlevel='01') 정답 수 스냅샷
    basic_total_cnt      INT,            -- 기본 총 문항 수 스냅샷
    advanced_correct_cnt INT,            -- 심화(qlevel='02') 정답 수 스냅샷
    advanced_total_cnt   INT,            -- 심화 총 문항 수 스냅샷
    CONSTRAINT UQ_erp_bookstore_diary_detail_book UNIQUE (diary_key, content_id),
    FOREIGN KEY (diary_key)    REFERENCES erp_bookstore_diary(diary_key),
    FOREIGN KEY (content_id)   REFERENCES erp_bookstore_content(content_id),
    FOREIGN KEY (recommend_id) REFERENCES erp_bookstore_recommend_log(recommend_id)
);

-- 독서태도 코드 마스터 — 태도 문구가 바뀔 수 있어 DB로 뺐다
IF OBJECT_ID('erp_bookstore_attitude_code', 'U') IS NULL
CREATE TABLE erp_bookstore_attitude_code (
    id            INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK(표시 순서 기준)
    attitude_code VARCHAR(20)   NOT NULL UNIQUE,   -- 태도 코드값 (erp_bookstore_attitude.attitude_code가 참조)
    attitude_name VARCHAR(100)  NOT NULL,           -- 태도명(화면 표시 문구)
    use_yn        BIT           NOT NULL DEFAULT 1, -- 사용여부
    created_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_by    VARCHAR(50)                       -- 수정한 사람
);

-- 독서태도 체크 — 일지 1건에 복수 선택되므로 1선택 = 1행으로 정규화
IF OBJECT_ID('erp_bookstore_attitude', 'U') IS NULL
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
