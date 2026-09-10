-- ============================================================
-- 회원가입(입회) 저장 로직 이식 (2026-09-10)
--   all_pass 의 erp_parent 에 해당. book_clinic 엔 보호자 정보를 담을 테이블이 없어 새로 만든다.
--   erp_student 와는 student_id 값으로만 연결(FK 없음) — 기존 book_clinic 관례와 동일.
-- ============================================================
IF OBJECT_ID('erp_student_guardian', 'U') IS NULL
CREATE TABLE erp_student_guardian (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    student_id    VARCHAR(100) NOT NULL,           -- erp_student.student_id
    guardian_name VARCHAR(50),                     -- 법정대리인 성명
    tel_first     VARCHAR(10),                     -- 연락처 앞자리 (010)
    tel_middle    VARCHAR(10),                     -- 연락처 중간자리
    tel_last      VARCHAR(10),                     -- 연락처 끝자리
    relation_key  VARCHAR(20),                     -- 관계 코드 (MO/FA/GM/GF/ETC — JoinViewController.RELATION_CODES)
    privacy_agree BIT          NOT NULL DEFAULT 0, -- 개인정보 수집·활용 동의
    signature_url VARCHAR(500),                    -- 서명 이미지 URL (가입 직후 별도 업로드로 채워짐)
    created_at    DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at    DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_guardian_student' AND object_id = OBJECT_ID('erp_student_guardian'))
    CREATE INDEX IX_guardian_student ON erp_student_guardian (student_id);
