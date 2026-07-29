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
 * - sp_delete_book: 마스터 도서 삭제 (itempool → item(사본, 2026-07-13부터 center_code 포함) → content_detail(분류별 상세) → content 순서로 del 테이블에 DELETE 로그 기록)
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

    /** 기존 저장 프로시저 삭제 */
    private void dropProcedures() {
        jdbcTemplate.execute("IF OBJECT_ID('sp_delete_book', 'P') IS NOT NULL DROP PROCEDURE sp_delete_book");
        jdbcTemplate.execute("IF OBJECT_ID('sp_restore_book', 'P') IS NOT NULL DROP PROCEDURE sp_restore_book");
    }

    /**
     * 마스터 도서 삭제 프로시저 생성
     * FK 의존 순서: priority 삭제 → itempool 이관 → item(사본) 이관 → content 이관
     * (erp_bookstore_priority가 content_id를 FK로 참조하므로 순위표에 올라간 도서는 먼저 정리해야 함 —
     *  priority는 설계상 복구 기능이 없으므로 DELETE 로그만 남기고 지운다)
     * 2026-07-13: item_center가 item에 통합되어 사본(item) 행 자체에 center_code/status가 있으므로
     * item_center를 먼저 정리하는 단계가 사라졌다.
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

                INSERT INTO erp_bookstore_itempool_del (log_type, logged_by, content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state)
                SELECT 'DELETE', @deletedBy, content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state
                FROM erp_bookstore_itempool WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_itempool WHERE content_id = @contentId;

                INSERT INTO erp_bookstore_item_del (log_type, logged_by, item_id, bcode, content_id, book_title, author, publisher, image_url, center_code, qty, loaned_qty, lost_qty)
                SELECT 'DELETE', @deletedBy, item_id, bcode, content_id, book_title, author, publisher, image_url, center_code, qty, loaned_qty, lost_qty
                FROM erp_bookstore_item WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_item WHERE content_id = @contentId;

                INSERT INTO erp_bookstore_content_detail_del (log_type, logged_by, content_id, gubun, name)
                SELECT 'DELETE', @deletedBy, content_id, gubun, name
                FROM erp_bookstore_content_detail WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_content_detail WHERE content_id = @contentId;

                INSERT INTO erp_bookstore_content_del (log_type, logged_by, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
                SELECT 'DELETE', @deletedBy, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty
                FROM erp_bookstore_content WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_content WHERE content_id = @contentId;
            END
        """);
    }

    /**
     * 마스터 도서 복구 프로시저 생성
     * content_id 원본 유지를 위해 IDENTITY_INSERT ON/OFF 사용
     * 로그는 지우지 않고 원본 테이블로 복사만 한다 — 로그가 누적되므로 자식 테이블은 키별 최신 DELETE 로그만 복원
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
