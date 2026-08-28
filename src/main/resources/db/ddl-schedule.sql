-- =====================================================================
-- 운영 스케줄 설정 (운영관리 > 운영 스케줄) — v2, 최종 11개 테이블
-- 상세 설계는 docs/DB_테이블_명세서_템플릿_운영스케줄.xlsx 참조
--
-- [v2에서 바뀐 것 3가지]
--   ① 예외 날짜 기간화   : schedule_exception이 target_date 단일 → start_date~end_date 기간.
--                          단, SLOT_CHANGE(회차별 마감)는 하루만(start_date=end_date).
--   ② 요일 규칙 버전 관리 : schedule/schedule_slot이 effective_from을 PK에 포함 —
--                          같은 요일에 미래 적용 예정 버전을 여러 개 동시에 예약 등록 가능.
--   ③ 예약 상태 전체 로그 : reservation_log 신설 — 예약 생성부터 취소·출결까지 모든 상태
--                          전환을 changed_by/changed_by_role과 함께 기록 (취소 주체 분쟁 방지).
--
-- [핵심 테이블 6개 = 규칙 4 + 실체 2]
--   schedule                : 요일별 운영 규칙(버전 관리, center+day_of_week+effective_from PK)
--   schedule_slot            : 버전에 속한 회차 템플릿
--   schedule_exception       : 특정 기간의 예외 (휴무/운영시간변경/회차변경)
--   schedule_exception_slot  : 예외(하루짜리)의 회차별 마감·정원 조정
--   slot_instance            : ★ 예약이 붙는 유일한 대상 — materialize()가 날짜별로 찍어낸 확정 슬롯
--   reservation              : 학생 예약 — slot_instance만 참조. 상태 이력은 reservation_log가 전담
--
-- [이력 로그 테이블 5개]
--   schedule_del / schedule_slot_del / schedule_exception_del / schedule_exception_slot_del
--     : 프로젝트 관례(erp_bookstore_content_del 등)를 따라 원본과 동일 컬럼 + log_type/logged_at/logged_by.
--       v2에서는 "적용 전인 미래 버전을 수정·삭제했을 때"가 사실상 유일한 사용 케이스.
--   reservation_log
--     : 예약 1건의 모든 상태 전환(from_status→to_status)을 빠짐없이 기록. 다른 4개와 달리
--       "삭제 스냅샷"이 아니라 append-only 전체 이력.
--
-- [slot_instance에 로그 테이블이 없는 이유]
--   materialize()가 계속 재생성하는 파생 데이터라 매번 로그를 남기면 실효가 없다.
--
-- [설계 원칙]
--   slot_instance(센터 x 날짜 x 회차)가 유일한 진실이다. 요일 버전과 기간 예외는 그것을
--   생성하는 입력값일 뿐이며, 예약은 오직 실체화된 슬롯만 참조한다.
--
-- [materialize(center, date) 의사코드 — v2]
--   day_ver = schedule[center, dayOfWeek(date)] 중 effective_from <= date 인 것의 최댓값 버전
--   ex = exception[center] 중 start_date <= date <= end_date 인 행 (있으면)
--   if   ex.type = CLOSED                → 슬롯 없음
--   elif not day_ver.is_open and no ex   → 슬롯 없음
--   elif ex.type = TIME_CHANGE           → ex의 회차(schedule_exception_slot)를 그대로 사용.
--                                          회차를 따로 지정하지 않았으면 요일 템플릿 중
--                                          바뀐 운영시간 안에 온전히 들어가는 회차만 남긴다
--   else                                 → day_ver에 속한 schedule_slot 사용
--   ex.type = SLOT_CHANGE 이면 해당 회차만 오버라이드 적용 (is_closed, capacity)
--   MERGE INTO slot_instance (center_code, service_date, seq)
--
--   호출 시점: 요일 버전 저장(effective_from 지정) → 저장 즉시, 오늘~예약 오픈일 중 해당 요일 전체 /
--             기간 예외 등록·삭제 → 그 예외의 start_date~end_date 전체, 즉시 /
--             일일 배치 → 새로 예약 오픈되는 1일치
--
--   갱신 규칙(v1의 "유지/취소 선택" 흐름 폐기, 단일화):
--     service_date < 오늘            → 수정 금지
--     영향받는 날짜에 예약이 있음      → 저장 자체를 거부 ("확인 후 다시 시도해주세요")
--     영향받는 날짜에 예약 없음        → 자유롭게 재생성
--
--   effective_from 최솟값: 오늘(KST) 이상. 요일 일치 여부는 별도 검증 불필요 —
--     MAX(effective_from <= 대상날짜) 조회 로직이 자동으로 다음 도래일부터 적용되게 함.
--     (SQL Server CHECK 제약은 GETDATE류를 못 써서 이 검증은 응용 계층에서 수행)
--
--   기간 예외 겹침 검증(응용 계층 — SQL Server는 range-overlap 제약 미지원):
--     기간(CLOSED/TIME_CHANGE) vs 기간           → 날짜 교차 시 등록 거부
--     기간(CLOSED/TIME_CHANGE) vs 단일일(SLOT_CHANGE) → 허용 (기간 안의 특정 하루에 추가 가능)
--     단일일 vs 단일일(같은 날짜)                  → 등록 거부(중복)
-- =====================================================================

-- ── 규칙: 운영 요일 마스터 (버전 관리) ────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule (
    center_code      VARCHAR(20)  NOT NULL,  -- 소속 센터 (erp_center.center_code)
    day_of_week      TINYINT      NOT NULL,  -- ISO 8601 · 1=월 ~ 7=일
    effective_from   DATE         NOT NULL,  -- 이 버전 적용 시작일(오늘 이후). 여러 버전 동시 등록 가능
    is_open          BIT          NOT NULL DEFAULT 1,  -- 운영/휴무 토글. 0이면 슬롯 미생성
    open_time        TIME(0),                -- 운영 시작시각 (예: 13:00). 휴무일은 NULL
    close_time       TIME(0),                -- 운영 종료시각 (예: 19:00)
    slot_minutes     SMALLINT     NOT NULL DEFAULT 50,  -- 회차 기본시간(분) — 회차 자동 생성의 기준값
    break_minutes    SMALLINT     NOT NULL DEFAULT 0,   -- 회차 간 간격(분)
    default_capacity SMALLINT     NOT NULL DEFAULT 10,  -- 최대 예약 인원 기본값. 회차 생성 시 상속
    created_at       DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 이 버전을 등록한 일시
    created_by       VARCHAR(50),            -- 등록자 (erp_user.user_code)
    updated_at       DATETIME2,              -- 적용 전 수정 시각 (미수정이면 NULL)
    updated_by       VARCHAR(50),            -- 적용 전 수정자
    CONSTRAINT PK_erp_bookstore_schedule PRIMARY KEY (center_code, day_of_week, effective_from),
    CONSTRAINT CK_schedule_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT CK_schedule_minutes CHECK (slot_minutes > 0 AND break_minutes >= 0),
    CONSTRAINT CK_schedule_open CHECK (is_open = 0 OR open_time < close_time),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
);

-- ── 이력: 운영 요일 마스터 변경 로그 ──────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_del', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_del (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',  -- DELETE(삭제) / UPDATE(수정 전 스냅샷)
    logged_at        DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    logged_by        VARCHAR(50),
    center_code      VARCHAR(20),            -- 원본 스냅샷
    day_of_week      TINYINT,
    effective_from   DATE,
    is_open          BIT,
    open_time        TIME(0),
    close_time       TIME(0),
    slot_minutes     SMALLINT,
    break_minutes    SMALLINT,
    default_capacity SMALLINT,
    created_at       DATETIME2,
    created_by       VARCHAR(50),
    updated_at       DATETIME2,
    updated_by       VARCHAR(50)
);

-- ── 규칙: 회차 템플릿 ─────────────────────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_slot', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_slot (
    slot_template_id INT IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK (대리 키)
    center_code      VARCHAR(20)  NOT NULL,
    day_of_week      TINYINT      NOT NULL,
    effective_from   DATE         NOT NULL,  -- 소속 버전 (schedule과 함께 FK)
    seq              TINYINT      NOT NULL,   -- 회차 번호 1,2,3… 드래그 정렬 시 재부여
    start_time       TIME(0)      NOT NULL,
    end_time         TIME(0)      NOT NULL,
    capacity         SMALLINT     NOT NULL,   -- 예약 가능 인원. 미지정 시 default_capacity 상속
    CONSTRAINT UX_schedule_slot_seq UNIQUE (center_code, day_of_week, effective_from, seq),
    CONSTRAINT CK_schedule_slot_time CHECK (start_time < end_time),
    FOREIGN KEY (center_code, day_of_week, effective_from)
        REFERENCES erp_bookstore_schedule (center_code, day_of_week, effective_from) ON DELETE CASCADE
);

-- ── 이력: 회차 템플릿 변경 로그 ───────────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_slot_del', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_slot_del (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',
    logged_at        DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    logged_by        VARCHAR(50),
    slot_template_id INT,
    center_code      VARCHAR(20),
    day_of_week      TINYINT,
    effective_from   DATE,
    seq              TINYINT,
    start_time       TIME(0),
    end_time         TIME(0),
    capacity         SMALLINT
);

-- ── 규칙: 특정 기간 예외 ──────────────────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_exception', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_exception (
    exception_id     INT IDENTITY(1,1) PRIMARY KEY,  -- 대리 키 — 기간이라 날짜가 자연키 불가
    center_code      VARCHAR(20)  NOT NULL,
    start_date       DATE         NOT NULL,   -- 예외 시작일
    end_date         DATE         NOT NULL,   -- 예외 종료일. SLOT_CHANGE면 start_date와 동일
    exception_type   VARCHAR(20)  NOT NULL,   -- CLOSED / TIME_CHANGE / SLOT_CHANGE
    reason           VARCHAR(200) NOT NULL,   -- 변경 사유 (예: 광복절, 공사로 인한 휴무)
    open_time        TIME(0),                 -- 변경 시작시각 — TIME_CHANGE 전용
    close_time       TIME(0),                 -- 변경 종료시각 — TIME_CHANGE 전용
    created_at       DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    created_by       VARCHAR(50),
    CONSTRAINT CK_schedule_exc_range CHECK (start_date <= end_date),
    CONSTRAINT CK_schedule_exc_slot_single CHECK (exception_type <> 'SLOT_CHANGE' OR start_date = end_date),
    CONSTRAINT CK_schedule_exc_type CHECK (exception_type IN ('CLOSED', 'TIME_CHANGE', 'SLOT_CHANGE')),
    CONSTRAINT CK_schedule_exc_time CHECK (exception_type <> 'TIME_CHANGE' OR open_time < close_time),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
    -- 기간 겹침 방지(CLOSED/TIME_CHANGE끼리): SQL Server range-overlap 제약 미지원 — 응용 계층에서 검증
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_schedule_exception_range' AND object_id = OBJECT_ID('erp_bookstore_schedule_exception'))
    CREATE INDEX IX_schedule_exception_range
        ON erp_bookstore_schedule_exception (center_code, start_date, end_date);  -- materialize()의 날짜 포함 조회 경로

-- ── 이력: 특정 기간 예외 변경 로그 ────────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_exception_del', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_exception_del (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',
    logged_at        DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    logged_by        VARCHAR(50),
    exception_id     INT,
    center_code      VARCHAR(20),
    start_date       DATE,
    end_date         DATE,
    exception_type   VARCHAR(20),
    reason           VARCHAR(200),
    open_time        TIME(0),
    close_time       TIME(0),
    created_at       DATETIME2,
    created_by       VARCHAR(50)
);

-- ── 규칙: 특정일 예외 회차 (부모는 반드시 SLOT_CHANGE, 하루짜리) ───────
IF OBJECT_ID('erp_bookstore_schedule_exception_slot', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_exception_slot (
    exception_id     INT       NOT NULL,   -- erp_bookstore_schedule_exception.exception_id
    seq              TINYINT   NOT NULL,   -- 회차 번호 (SLOT_CHANGE는 대상 회차, TIME_CHANGE는 그날 회차 번호)
    is_closed        BIT       NOT NULL DEFAULT 0,  -- 1이면 해당 회차만 예약 불가
    capacity         SMALLINT,             -- NULL이면 템플릿 인원 유지 (0과 의미 다름)
    -- 시각은 TIME_CHANGE 전용 (2026-08-18 추가). 운영시간을 바꾸면 회차를 그대로 쓸 수도,
    -- 늘어난 시간에 회차를 더할 수도, 아예 다시 짤 수도 있어야 해서 관리자가 확정한 회차를
    -- 여기 그대로 저장한다. SLOT_CHANGE는 템플릿 회차를 가리키기만 하므로 NULL이다.
    start_time       TIME(0),
    end_time         TIME(0),
    CONSTRAINT CK_schedule_exc_slot_time CHECK (
        start_time IS NULL OR end_time IS NULL OR start_time < end_time),
    CONSTRAINT PK_erp_bookstore_schedule_exception_slot PRIMARY KEY (exception_id, seq),
    FOREIGN KEY (exception_id) REFERENCES erp_bookstore_schedule_exception (exception_id) ON DELETE CASCADE
);

-- ── 이력: 특정일 예외 회차 변경 로그 ──────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule_exception_slot_del', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule_exception_slot_del (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',
    logged_at        DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    logged_by        VARCHAR(50),
    exception_id     INT,
    seq              TINYINT,
    is_closed        BIT,
    capacity         SMALLINT,
    start_time       TIME(0),
    end_time         TIME(0)
);

-- ── 실체: 예약 슬롯 (★ 예약이 붙는 유일한 대상) ───────────────────────
IF OBJECT_ID('erp_bookstore_slot_instance', 'U') IS NULL
CREATE TABLE erp_bookstore_slot_instance (
    slot_instance_id INT IDENTITY(1,1) PRIMARY KEY,  -- 예약이 참조하는 키
    center_code      VARCHAR(20)  NOT NULL,
    service_date     DATE         NOT NULL,
    seq              TINYINT      NOT NULL,
    starts_at        DATETIME2    NOT NULL,   -- 날짜+시각 직접 보유 — 템플릿 변경에 영향 없음
    ends_at          DATETIME2    NOT NULL,
    capacity         SMALLINT     NOT NULL,   -- 생성 시점에 확정된 정원
    status           VARCHAR(10)  NOT NULL DEFAULT 'OPEN',  -- OPEN 예약가능 / CLOSED 마감
    reserved_count   SMALLINT     NOT NULL DEFAULT 0,       -- 정원 판단·동시성 제어용 카운터
    source_type      VARCHAR(20)  NOT NULL DEFAULT 'TEMPLATE',  -- TEMPLATE / EXCEPTION — 역추적용
    materialized_at  DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UX_slot_instance_date_seq UNIQUE (center_code, service_date, seq),
    CONSTRAINT CK_slot_instance_count CHECK (reserved_count >= 0 AND reserved_count <= capacity),
    CONSTRAINT CK_slot_instance_time CHECK (starts_at < ends_at),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
    -- 회차 시간 중복 차단: SQL Server는 EXCLUDE 제약 미지원 — 응용 계층에서 검증 (회차 병렬 운영 시 불필요)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_slot_instance_open' AND object_id = OBJECT_ID('erp_bookstore_slot_instance'))
    CREATE INDEX IX_slot_instance_open
        ON erp_bookstore_slot_instance (center_code, service_date, seq)
        WHERE status = 'OPEN';  -- 학생 예약 화면 주 조회 경로 (필터링된 인덱스)

-- ── 실체: 예약 (현재 상태 빠른 조회용 — 전체 이력은 reservation_log) ──
IF OBJECT_ID('erp_bookstore_reservation', 'U') IS NULL
CREATE TABLE erp_bookstore_reservation (
    reservation_id   INT IDENTITY(1,1) PRIMARY KEY,
    slot_instance_id INT           NOT NULL,
    student_id       VARCHAR(100)  NOT NULL,  -- erp_student.student_id (기존 관례상 FK 없이 값으로 연결)
    -- slot_instance.service_date의 사본(2026-08-20). 조회 편의가 아니라 "하루 한 회차" 제약을
    -- 인덱스로 걸기 위해 필요하다 — 유니크 인덱스는 조인한 컬럼에 걸 수 없어서, 날짜가 예약 행
    -- 자체에 있어야만 UX_reservation_student_date가 성립한다. 값은 항상 슬롯에서 복사한다.
    service_date     DATE          NOT NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'RESERVED',  -- RESERVED/CANCELED/ATTENDED/NOSHOW
    -- 예약방법(2026-08-19) — 누가 등록했는지. STUDENT/PARENT=학생 앱 직접 예약, ADMIN=센터 직원 대리
    -- 등록. 생성 시점에 고정해서 저장한다(예약 현황 화면의 "직접 예약"/"센터 예약" 표시가 이 값을 그대로 씀).
    channel          VARCHAR(20)   NOT NULL DEFAULT 'STUDENT',
    reserved_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    canceled_at      DATETIME2,               -- 취소 일시(KST)
    cancel_reason    VARCHAR(200),            -- 휴무 지정에 의한 관리자 취소 사유 등
    CONSTRAINT CK_reservation_status CHECK (status IN ('RESERVED', 'CANCELED', 'ATTENDED', 'NOSHOW')),
    CONSTRAINT CK_reservation_channel CHECK (channel IN ('STUDENT', 'PARENT', 'ADMIN', 'SYSTEM')),
    FOREIGN KEY (slot_instance_id) REFERENCES erp_bookstore_slot_instance (slot_instance_id)
    -- 예약이 있으면 슬롯 삭제 불가 (기본 RESTRICT)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_slot_student' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    CREATE UNIQUE INDEX UX_reservation_slot_student
        ON erp_bookstore_reservation (slot_instance_id, student_id)
        WHERE status = 'RESERVED';  -- 동일 회차 중복 예약 차단. 취소 후 재예약 가능 (필터링된 인덱스)

-- 2026-08-28: "하루 한 회차" → "하루 두 회차"로 정책 변경. (student_id, service_date) 유니크로는
-- "2건까지"를 표현할 수 없어 UX_reservation_student_date는 만들지 않는다. 하루 2회차 상한은
-- 응용 계층에서 강제한다(ReservationService.reserveOne: 학생 행 UPDLOCK으로 동시 요청 직렬화 +
-- countOtherReservedOnDate >= 2 차단). 같은 회차 중복 예약은 위 UX_reservation_slot_student가 계속 막는다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_student_date' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    DROP INDEX UX_reservation_student_date ON erp_bookstore_reservation;

-- ── 이력: 예약 상태 변경 전체 로그 (★ 신규 — 취소 주체 분쟁 방지) ─────
IF OBJECT_ID('erp_bookstore_reservation_log', 'U') IS NULL
CREATE TABLE erp_bookstore_reservation_log (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    reservation_id   INT           NOT NULL,   -- erp_bookstore_reservation.reservation_id
    from_status      VARCHAR(12),              -- 전환 전 상태. 최초 예약 생성이면 NULL
    to_status        VARCHAR(12)   NOT NULL,   -- RESERVED / CANCELED / ATTENDED / NOSHOW
    changed_at       DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    changed_by       VARCHAR(100),             -- 누가 (student_id / 학부모 계정 / erp_user.user_code)
    changed_by_role  VARCHAR(20),              -- STUDENT / PARENT / ADMIN / SYSTEM
    reason           VARCHAR(200),             -- 사유(선택)
    FOREIGN KEY (reservation_id) REFERENCES erp_bookstore_reservation (reservation_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_reservation_log_reservation' AND object_id = OBJECT_ID('erp_bookstore_reservation_log'))
    CREATE INDEX IX_reservation_log_reservation
        ON erp_bookstore_reservation_log (reservation_id, changed_at);  -- 예약 1건의 전체 이력 조회 경로
