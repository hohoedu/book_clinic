package com.hohoedu.book_clinic._core.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 애플리케이션 시작 시 저장 프로시저를 자동 생성하는 초기화 컴포넌트
 *
 * SQL Server는 SET IDENTITY_INSERT가 세션 단위로 동작하므로,
 * 동일 세션 내에서 실행을 보장하기 위해 저장 프로시저를 사용
 *
 * - sp_delete_book: 마스터 도서 논리삭제 (2026-11-18~) — 권장도서 순위만 빼고, content는 state='D'로 남긴다 (학생 기록 화면이 제목/표지를 계속 보여야 하므로)
 * - sp_restore_book: 마스터 도서 복구 (content_del → content 복원 시 IDENTITY_INSERT 사용, content_detail도 함께 복원 — 로그는 지우지 않고 복사만)
 */
@Component
@RequiredArgsConstructor
public class StoredProcedureInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** 애플리케이션 시작 시 기존 프로시저 삭제 후 재생성 */
    @Override
    public void run(ApplicationArguments args) {
        dropProcedures();
        createDeleteBookProcedure();
        createRestoreBookProcedure();
    }

    private void dropProcedures() {
        jdbcTemplate.execute("IF OBJECT_ID('sp_delete_book', 'P') IS NOT NULL DROP PROCEDURE sp_delete_book");
        jdbcTemplate.execute("IF OBJECT_ID('sp_restore_book', 'P') IS NOT NULL DROP PROCEDURE sp_restore_book");
    }

    /**
     * 마스터 도서 삭제 프로시저 생성
     */
    private void createDeleteBookProcedure() {
        jdbcTemplate.execute("""
            CREATE PROCEDURE sp_delete_book
                @contentId INT,
                @deletedBy VARCHAR(100)
            AS
            BEGIN
                SET NOCOUNT ON;

                INSERT INTO erp_bookstore_priority_del (log_type, draft_id, content_id, sort_order)
                SELECT 'DELETE', draft_id, content_id, sort_order
                FROM erp_bookstore_priority WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_priority WHERE content_id = @contentId;

                -- 논리삭제(2026-11-18): 학생 기록 화면이 도서 제목/표지를 계속 보여야 해서 행은 지우지 않고 state='D'로 둔다.
                -- 사본·문제·상세·카드·센터번호도 그대로 둬서 복구 시 원래대로 돌아온다. 삭제 직전 스냅샷만 content_del에 남긴다.
                INSERT INTO erp_bookstore_content_del (log_type, logged_by, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
                SELECT 'DELETE', @deletedBy, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty
                FROM erp_bookstore_content WHERE content_id = @contentId AND ISNULL(state, 'Y') <> 'D';

                UPDATE erp_bookstore_content SET state = 'D' WHERE content_id = @contentId;
            END
        """);
    }

    /**
     * 마스터 도서 복구 프로시저 생성
     */
    private void createRestoreBookProcedure() {
        jdbcTemplate.execute("""
            CREATE PROCEDURE sp_restore_book
                @delId INT
            AS
            BEGIN
                SET NOCOUNT ON;

                DECLARE @contentId INT = (SELECT content_id FROM erp_bookstore_content_del WHERE log_id = @delId);
                IF @contentId IS NULL RETURN;

                -- 논리삭제(state='D')된 도서는 삭제 직전 상태(사용중/절판)로만 되돌린다
                IF EXISTS (SELECT 1 FROM erp_bookstore_content WHERE content_id = @contentId AND state = 'D')
                BEGIN
                    UPDATE erp_bookstore_content
                    SET state = (SELECT CASE WHEN ISNULL(d.state, 'Y') = 'D' THEN 'Y' ELSE ISNULL(d.state, 'Y') END
                                 FROM erp_bookstore_content_del d WHERE d.log_id = @delId)
                    WHERE content_id = @contentId;
                    RETURN;
                END

                -- 아래는 2026-11-18 이전 실삭제된 도서 복구 경로
                IF EXISTS (SELECT 1 FROM erp_bookstore_content WHERE content_id = @contentId) RETURN;

                SET IDENTITY_INSERT erp_bookstore_content ON;

                INSERT INTO erp_bookstore_content (content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
                SELECT content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty
                FROM erp_bookstore_content_del WHERE log_id = @delId;

                SET IDENTITY_INSERT erp_bookstore_content OFF;

                INSERT INTO erp_bookstore_content_detail (content_id, gubun, name)
                SELECT content_id, gubun, name
                FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY gubun ORDER BY log_id DESC) AS rn
                      FROM erp_bookstore_content_detail_del
                      WHERE content_id = @contentId AND log_type = 'DELETE') t
                WHERE rn = 1;

                INSERT INTO erp_bookstore_item (bcode, content_id, center_code, book_title, author, publisher, image_url, qty, loaned_qty, lost_qty)
                SELECT bcode, content_id, center_code, book_title, author, publisher, image_url,
                       ISNULL(qty, 0), ISNULL(loaned_qty, 0), ISNULL(lost_qty, 0)
                FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY bcode, center_code ORDER BY log_id DESC) AS rn
                      FROM erp_bookstore_item_del
                      WHERE content_id = @contentId AND log_type = 'DELETE') t
                WHERE rn = 1
                  AND NOT EXISTS (SELECT 1 FROM erp_bookstore_item i WHERE i.bcode = t.bcode AND i.center_code = t.center_code);

                INSERT INTO erp_bookstore_itempool (content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state)
                SELECT content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state
                FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY qlevel, qnum ORDER BY log_id DESC) AS rn
                      FROM erp_bookstore_itempool_del
                      WHERE content_id = @contentId AND log_type = 'DELETE') t
                WHERE rn = 1;
            END
        """);
    }
}
