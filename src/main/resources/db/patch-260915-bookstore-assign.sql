-- ============================================================
-- 책방 수강 배정(erp_bookstore_assign) 신설 (2026-09-15)
--   "학생 정보" 상세모달 3번째 탭(수강 정보)에서 쓰는 학생별 수강 등록 정보.
--   all_pass 의 erp_teacher_assign 중 책방(bookstore) 몫만 떼어온 것이다 —
--   book_clinic 엔 교사배정/반배정 개념이 없어 담당교사·반 컬럼은 가져오지 않는다.
--   erp_student 와는 student_id 값으로만 연결(FK 없음) — 기존 book_clinic 관례와 동일.
-- ============================================================
IF OBJECT_ID('erp_bookstore_assign', 'U') IS NULL
CREATE TABLE erp_bookstore_assign (
    assign_id                 INT IDENTITY(1,1) PRIMARY KEY,
    student_id                VARCHAR(100) NOT NULL,           -- erp_student.student_id
    bookstore_state           BIT          NOT NULL DEFAULT 1, -- 1=수강 / 0=미수강
    bookstore_edu_fee         INT,                             -- 교육비(원). 미입력이면 NULL
    -- 저장 시점의 자동 계산 레벨 스냅샷(ClinicService.getMainLevelInfo().levelNo, 1~30).
    -- 완독 권수가 늘어도 따라 오르지 않는다 — 화면에서도 읽기 전용이다.
    bookstore_level           INT,
    -- 화면 입력은 날짜(yyyy-MM-dd)뿐이지만 타입은 DATETIME2 다. 저장할 때
    -- StudentMapper.saveBookstoreAssign 이 CONVERT(DATETIME2, ..., 23) 으로 명시 변환해 넣는다.
    entry_bookstore_date      DATETIME2,                       -- 수강 시작일
    inactive_bookstore_date   DATETIME2,                       -- 미수강 전환일 (수강중이면 NULL)
    inactive_bookstore_reason VARCHAR(200),                    -- 미수강 사유 (수강중이면 NULL)
    created_at                DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at                DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 학생 1명당 수강 정보 1행. StudentMapper.saveBookstoreAssign 의 MERGE 가 이 유니크에 기댄다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_bookstore_assign_student' AND object_id = OBJECT_ID('erp_bookstore_assign'))
    CREATE UNIQUE INDEX UX_bookstore_assign_student ON erp_bookstore_assign (student_id);

-- 위 CREATE 를 DATE 타입이던 초판으로 이미 실행했다면 여기서 DATETIME2 로 올린다(같은 날 안에 바뀐 설계).
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_assign')
             AND name = 'entry_bookstore_date' AND system_type_id = TYPE_ID('date'))
    ALTER TABLE erp_bookstore_assign ALTER COLUMN entry_bookstore_date DATETIME2;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_assign')
             AND name = 'inactive_bookstore_date' AND system_type_id = TYPE_ID('date'))
    ALTER TABLE erp_bookstore_assign ALTER COLUMN inactive_bookstore_date DATETIME2;
