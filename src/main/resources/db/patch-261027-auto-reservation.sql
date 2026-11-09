-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 매주 자동 예약 + 결제대기(PAY_WAIT) + 10시 일괄 안내 (2026-10-27)
--
-- ① 예약 상태에 PAY_WAIT 추가 — 자동구매(빌키) 학생의 이용권이 떨어졌는데 카드 결제가
--    실패한 경우, 그 회차 자리만 먼저 잡아둔다(정원은 차지, 이용권 차감은 없음).
--    결제가 되면 RESERVED로 바뀌며 차감되고, 재시도까지 다 실패하면 CANCELED로 풀린다.
-- ② 같은 회차 중복 예약 차단(UX_reservation_slot_student)이 PAY_WAIT도 보게 한다 —
--    결제대기로 잡힌 회차를 같은 학생이 한 번 더 잡지 못하게.
-- ③ erp_bookstore_auto_reservation — 학생별 "매주 ○요일 ○회차" 패턴.
-- ④ erp_bookstore_auto_notice — 새벽 배치 결과를 쌓아뒀다가 오전 10시에 학생별로 묶어 푸시한다.
--
-- IF EXISTS / IF NOT EXISTS로 감싸 여러 번 실행해도 안전하다.
-- GO 없이 문장마다 ; 로 끝난다 — DBeaver에서는 Alt+X(스크립트 실행)로 한 문장씩 실행한다.
-- ════════════════════════════════════════════════════════════════════

-- ① 상태 CHECK 교체
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_reservation_status'
           AND definition NOT LIKE '%PAY_WAIT%')
    ALTER TABLE erp_bookstore_reservation DROP CONSTRAINT CK_reservation_status;
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_reservation_status')
    ALTER TABLE erp_bookstore_reservation
        ADD CONSTRAINT CK_reservation_status
        CHECK (status IN ('RESERVED', 'CANCELED', 'ATTENDED', 'NOSHOW', 'PAY_WAIT'));

-- ② 중복 예약 유니크 — 필터에 PAY_WAIT 포함
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_slot_student'
           AND object_id = OBJECT_ID('erp_bookstore_reservation')
           AND filter_definition NOT LIKE '%PAY_WAIT%')
    DROP INDEX UX_reservation_slot_student ON erp_bookstore_reservation;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_slot_student' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    CREATE UNIQUE INDEX UX_reservation_slot_student
        ON erp_bookstore_reservation (slot_instance_id, student_id)
        WHERE status IN ('RESERVED', 'PAY_WAIT');

-- ③ 자동 예약 패턴
IF OBJECT_ID('erp_bookstore_auto_reservation', 'U') IS NULL
CREATE TABLE erp_bookstore_auto_reservation (
    auto_id       INT IDENTITY(1,1) PRIMARY KEY,
    student_id    VARCHAR(100) NOT NULL,   -- erp_student.student_id (값으로만 연결)
    day_of_week   TINYINT      NOT NULL,   -- ISO 요일 1=월 ~ 7=일
    seq           TINYINT      NOT NULL,   -- 회차 번호
    enabled       BIT          NOT NULL DEFAULT 1,
    filled_until  DATE,                    -- 여기까지는 자동 예약을 시도했다. 다음 시도는 이 다음 날부터
    no_pass_at    DATE,                    -- 이용권이 없어 멈춘 날. NULL이면 정상. 안내를 한 번만 보내는 기준
    created_at    DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at    DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UX_auto_reservation UNIQUE (student_id, day_of_week, seq)
);

-- ④ 10시 일괄 안내 대기열
IF OBJECT_ID('erp_bookstore_auto_notice', 'U') IS NULL
CREATE TABLE erp_bookstore_auto_notice (
    notice_id   INT IDENTITY(1,1) PRIMARY KEY,
    student_id  VARCHAR(100) NOT NULL,
    message     VARCHAR(200) NOT NULL,     -- 안내 한 줄. 발송 시 학생별로 줄바꿈으로 묶는다
    created_at  DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    sent_at     DATETIME2                  -- NULL이면 아직 안 보냄
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_auto_notice_unsent' AND object_id = OBJECT_ID('erp_bookstore_auto_notice'))
    CREATE INDEX IX_auto_notice_unsent ON erp_bookstore_auto_notice (student_id, notice_id)
        WHERE sent_at IS NULL;
