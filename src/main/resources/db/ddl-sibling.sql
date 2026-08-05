-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 배포용 DDL — 학생 형제(가족) 묶음 (2026-08-05)
--
-- [왜 이 파일이 따로 있나] ddl-payment.sql과 같은 이유다. application-prod.yml에는
-- spring.sql.init 설정이 없어 schema.sql이 운영에서 자동 실행되지 않고(ddl-auto도
-- validate), schema.sql을 통째로 돌리면 all_pass(서당)와 공유하는 DB의 다른 데이터까지
-- 위험해진다. 그래서 DROP 없는 이 파일을 따로 두고 사람이 직접 실행한다.
--
-- [사용법] 운영 DB에 이 파일만 실행한다. IF OBJECT_ID(...) IS NULL로 감싸여 있어
-- 여러 번 실행해도 안전하다.
--
-- [형제 등록 방법] 자동 매칭 로직이 없다. sibling_key를 정해(대표 학생의 student_id를
-- 그대로 쓰는 것을 권장) 같은 그룹에 속한 학생들의 student_id를 사람이 직접 INSERT한다.
-- 주의: 여기 student_id는 로그인용 app_id(QR값)가 아니라 erp_student.student_id 값이다
-- (로그인/결제/세션이 전부 student_id 기준으로 동작한다). app_id로 착각해 넣으면 매칭되지 않는다.
-- 예: student_id가 각각 DAE001T01, DAE001T02인 두 학생이 형제라면
--   INSERT INTO erp_student_sibling (sibling_key, student_id) VALUES ('DAE001T01', 'DAE001T01');
--   INSERT INTO erp_student_sibling (sibling_key, student_id) VALUES ('DAE001T01', 'DAE001T02');
--
-- [원본] src/main/resources/db/schema.sql의 erp_student_sibling 섹션과 같은 내용이다.
-- 스키마를 고칠 일이 있으면 두 파일을 함께 고쳐야 한다.
-- ════════════════════════════════════════════════════════════════════

-- 학생 형제(가족) 묶음 — 결제창에서 형제를 함께 보여주고 합산 결제할 때 쓴다.
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
