-- ════════════════════════════════════════════════════════════════════
-- 운영 배포 패치 — 2026-08-21
--
-- 아래 6개 파일을 실행 순서 그대로 한 파일에 모았다. 전부 IF OBJECT_ID/COL_LENGTH/
-- sys.columns IS NULL 가드가 걸려 있어 여러 번 실행해도 안전하다.
--   1) ddl-schedule.sql                              — 운영스케줄/예약 v2, 11개 테이블 신규 생성
--   2) patch-erp_bookstore_payment-needs_review.sql  — needs_review/review_reason/reviewed_at
--   3) patch-erp_bookstore_payment-refund_lock.sql   — refund_requested_at
--   4) patch-erp_bookstore_payment-closed_recheck.sql— closed_recheck_at
--   5) patch-erp_student-help_needed.sql             — help_needed
--   6) patch-erp_student-clinic_grade_key.sql        — clinic_grade_key
--
-- [정리한 것] 2~4번 세 파일 모두 closed_recheck_at/needs_review/review_reason/
-- reviewed_at 구문을 중복으로 갖고 있어서(서로 베껴 쓴 흔적) 여기서는 컬럼당 한 번씩만
-- 남겼다. 6번 원본 파일에는 clinic_grade_key와 무관한 payment 컬럼 구문이 잘못 섞여
-- 들어가 있었는데(별도로 보고함), 여기서는 제외하고 clinic_grade_key 추가문만 담았다.
-- ════════════════════════════════════════════════════════════════════


-- ═══════════════════ 1) ddl-schedule.sql ═══════════════════════════
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
-- =====================================================================

-- ── 규칙: 운영 요일 마스터 (버전 관리) ────────────────────────────────
IF OBJECT_ID('erp_bookstore_schedule', 'U') IS NULL
CREATE TABLE erp_bookstore_schedule (
    center_code      VARCHAR(20)  NOT NULL,
    day_of_week      TINYINT      NOT NULL,
    effective_from   DATE         NOT NULL,
    is_open          BIT          NOT NULL DEFAULT 1,
    open_time        TIME(0),
    close_time       TIME(0),
    slot_minutes     SMALLINT     NOT NULL DEFAULT 50,
    break_minutes    SMALLINT     NOT NULL DEFAULT 0,
    default_capacity SMALLINT     NOT NULL DEFAULT 10,
    created_at       DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    created_by       VARCHAR(50),
    updated_at       DATETIME2,
    updated_by       VARCHAR(50),
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
    log_type         VARCHAR(10)  NOT NULL DEFAULT 'DELETE',
    logged_at        DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    logged_by        VARCHAR(50),
    center_code      VARCHAR(20),
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
    slot_template_id INT IDENTITY(1,1) PRIMARY KEY,
    center_code      VARCHAR(20)  NOT NULL,
    day_of_week      TINYINT      NOT NULL,
    effective_from   DATE         NOT NULL,
    seq              TINYINT      NOT NULL,
    start_time       TIME(0)      NOT NULL,
    end_time         TIME(0)      NOT NULL,
    capacity         SMALLINT     NOT NULL,
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
    exception_id     INT IDENTITY(1,1) PRIMARY KEY,
    center_code      VARCHAR(20)  NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    exception_type   VARCHAR(20)  NOT NULL,
    reason           VARCHAR(200) NOT NULL,
    open_time        TIME(0),
    close_time       TIME(0),
    created_at       DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    created_by       VARCHAR(50),
    CONSTRAINT CK_schedule_exc_range CHECK (start_date <= end_date),
    CONSTRAINT CK_schedule_exc_slot_single CHECK (exception_type <> 'SLOT_CHANGE' OR start_date = end_date),
    CONSTRAINT CK_schedule_exc_type CHECK (exception_type IN ('CLOSED', 'TIME_CHANGE', 'SLOT_CHANGE')),
    CONSTRAINT CK_schedule_exc_time CHECK (exception_type <> 'TIME_CHANGE' OR open_time < close_time),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_schedule_exception_range' AND object_id = OBJECT_ID('erp_bookstore_schedule_exception'))
    CREATE INDEX IX_schedule_exception_range
        ON erp_bookstore_schedule_exception (center_code, start_date, end_date);

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
    exception_id     INT       NOT NULL,
    seq              TINYINT   NOT NULL,
    is_closed        BIT       NOT NULL DEFAULT 0,
    capacity         SMALLINT,
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
    slot_instance_id INT IDENTITY(1,1) PRIMARY KEY,
    center_code      VARCHAR(20)  NOT NULL,
    service_date     DATE         NOT NULL,
    seq              TINYINT      NOT NULL,
    starts_at        DATETIME2    NOT NULL,
    ends_at          DATETIME2    NOT NULL,
    capacity         SMALLINT     NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    reserved_count   SMALLINT     NOT NULL DEFAULT 0,
    source_type      VARCHAR(20)  NOT NULL DEFAULT 'TEMPLATE',
    materialized_at  DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UX_slot_instance_date_seq UNIQUE (center_code, service_date, seq),
    CONSTRAINT CK_slot_instance_count CHECK (reserved_count >= 0 AND reserved_count <= capacity),
    CONSTRAINT CK_slot_instance_time CHECK (starts_at < ends_at),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_slot_instance_open' AND object_id = OBJECT_ID('erp_bookstore_slot_instance'))
    CREATE INDEX IX_slot_instance_open
        ON erp_bookstore_slot_instance (center_code, service_date, seq)
        WHERE status = 'OPEN';

-- ── 실체: 예약 (현재 상태 빠른 조회용 — 전체 이력은 reservation_log) ──
IF OBJECT_ID('erp_bookstore_reservation', 'U') IS NULL
CREATE TABLE erp_bookstore_reservation (
    reservation_id   INT IDENTITY(1,1) PRIMARY KEY,
    slot_instance_id INT           NOT NULL,
    student_id       VARCHAR(100)  NOT NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'RESERVED',
    channel          VARCHAR(20)   NOT NULL DEFAULT 'STUDENT',
    reserved_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    canceled_at      DATETIME2,
    cancel_reason    VARCHAR(200),
    CONSTRAINT CK_reservation_status CHECK (status IN ('RESERVED', 'CANCELED', 'ATTENDED', 'NOSHOW')),
    CONSTRAINT CK_reservation_channel CHECK (channel IN ('STUDENT', 'PARENT', 'ADMIN', 'SYSTEM')),
    FOREIGN KEY (slot_instance_id) REFERENCES erp_bookstore_slot_instance (slot_instance_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_slot_student' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    CREATE UNIQUE INDEX UX_reservation_slot_student
        ON erp_bookstore_reservation (slot_instance_id, student_id)
        WHERE status = 'RESERVED';

-- ── 이력: 예약 상태 변경 전체 로그 (★ 신규 — 취소 주체 분쟁 방지) ─────
IF OBJECT_ID('erp_bookstore_reservation_log', 'U') IS NULL
CREATE TABLE erp_bookstore_reservation_log (
    log_id           INT IDENTITY(1,1) PRIMARY KEY,
    reservation_id   INT           NOT NULL,
    from_status      VARCHAR(12),
    to_status        VARCHAR(12)   NOT NULL,
    changed_at       DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    changed_by       VARCHAR(100),
    changed_by_role  VARCHAR(20),
    reason           VARCHAR(200),
    FOREIGN KEY (reservation_id) REFERENCES erp_bookstore_reservation (reservation_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_reservation_log_reservation' AND object_id = OBJECT_ID('erp_bookstore_reservation_log'))
    CREATE INDEX IX_reservation_log_reservation
        ON erp_bookstore_reservation_log (reservation_id, changed_at);


-- ═══════ 2~4) erp_bookstore_payment 컬럼 추가 (needs_review/refund_lock/closed_recheck 통합) ═══════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'closed_recheck_at')
    ALTER TABLE erp_bookstore_payment ADD closed_recheck_at DATETIME2;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'needs_review')
    ALTER TABLE erp_bookstore_payment ADD needs_review BIT NOT NULL DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'review_reason')
    ALTER TABLE erp_bookstore_payment ADD review_reason VARCHAR(200);
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'reviewed_at')
    ALTER TABLE erp_bookstore_payment ADD reviewed_at DATETIME2;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'refund_requested_at')
    ALTER TABLE erp_bookstore_payment ADD refund_requested_at DATETIME2;


-- ═══════════════════ 5) erp_student.help_needed ═══════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_student') AND name = 'help_needed')
    ALTER TABLE erp_student ADD help_needed BIT NOT NULL DEFAULT 0;


-- ═══════════════════ 6) erp_student.clinic_grade_key ═══════════════════
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_student') AND name = 'clinic_grade_key')
    ALTER TABLE erp_student ADD clinic_grade_key VARCHAR(2);
