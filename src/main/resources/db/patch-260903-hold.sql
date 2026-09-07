-- ============================================================================
-- 260903 책 홀딩(자물쇠) — erp_bookstore_recommend_log 컬럼 추가
--
-- 홀딩 = "다 못 읽고 넘어간 책". 책을 잠그는 기능이 아니다 — 홀딩된 책도 재고만 있으면 다른
-- 학생 누구에게나 추천된다(대여는 입실~퇴실 사이에만 존재하고, 퇴실 시 반납되기 때문).
-- 홀딩한 학생이 요일·회차와 무관하게 언제 다시 오든 그 책을 최우선으로 다시 받는다(이어 읽기).
--
-- status 값이 하나 늘어난다: PENDING(읽는 중) / HOLD(다 못 읽고 넘어감) / DONE(첫 제출 완료)
-- CHECK 제약이 없는 컬럼이라 값만 추가하면 되고 DDL 변경은 필요 없다.
-- ============================================================================

IF COL_LENGTH('erp_bookstore_recommend_log', 'hold_page') IS NULL
    ALTER TABLE erp_bookstore_recommend_log
        ADD hold_page INT NULL;  -- 홀딩 시점까지 읽은 페이지 (선생님이 자물쇠로 입력, 없을 수 있음)

-- 홀딩 유효 여부. 폐기는 행 삭제가 아니라 논리삭제 'N' (erp_bookstore_priority_draft.is_active 관례와 동일)
-- 유효 홀딩은 학생당 최대 1건 — 다음 책을 받는 순간 이전 홀딩은 'N'이 된다.
-- (2주 뒤에 60쪽부터 이어 읽으라고 하면 앞부분이 기억나지 않는다. 최근 책을 이어서 완독하고,
--  폐기된 책은 나중에 새로 추천받아 처음부터 읽는 편이 낫다는 판단 — 2026-09-03 확정)
IF COL_LENGTH('erp_bookstore_recommend_log', 'hold_use') IS NULL
    ALTER TABLE erp_bookstore_recommend_log
        ADD hold_use VARCHAR(1) NULL;
GO

-- 이어 읽을 책 조회 경로 — (학생, 상태) 로 유효 홀딩 1건을 바로 찾는다
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_recommend_log_hold' AND object_id = OBJECT_ID('erp_bookstore_recommend_log'))
    CREATE INDEX IX_recommend_log_hold
        ON erp_bookstore_recommend_log (student_id, status)
        INCLUDE (hold_use, hold_page);
GO
