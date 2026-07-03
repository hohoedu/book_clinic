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
 * - sp_delete_book: 마스터 도서 삭제 (item_center → itempool → item → content 순서로 del 테이블 이관)
 * - sp_restore_book: 마스터 도서 복구 (content_del → content 복원 시 IDENTITY_INSERT 사용)
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
     * FK 의존 순서: item_center 삭제 → itempool 이관 → item 이관 → content 이관
     */
    private void createDeleteBookProcedure() {
        jdbcTemplate.execute("""
            CREATE PROCEDURE sp_delete_book
                @contentId INT,
                @deletedBy VARCHAR(100)
            AS
            BEGIN
                SET NOCOUNT ON;

                INSERT INTO erp_bookstore_itempool_del (deleted_by, content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state)
                SELECT @deletedBy, content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state
                FROM erp_bookstore_itempool WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_itempool WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_item_center
                WHERE bcode IN (SELECT bcode FROM erp_bookstore_item WHERE content_id = @contentId);

                INSERT INTO erp_bookstore_item_del (deleted_by, bcode, content_id, book_title, author, publisher, image_url)
                SELECT @deletedBy, bcode, content_id, book_title, author, publisher, image_url
                FROM erp_bookstore_item WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_item WHERE content_id = @contentId;

                INSERT INTO erp_bookstore_content_del (deleted_by, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
                SELECT @deletedBy, content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty
                FROM erp_bookstore_content WHERE content_id = @contentId;

                DELETE FROM erp_bookstore_content WHERE content_id = @contentId;
            END
        """);
    }

    /**
     * 마스터 도서 복구 프로시저 생성
     * content_id 원본 유지를 위해 IDENTITY_INSERT ON/OFF 사용
     */
    private void createRestoreBookProcedure() {
        jdbcTemplate.execute("""
            CREATE PROCEDURE sp_restore_book
                @delId INT
            AS
            BEGIN
                SET NOCOUNT ON;

                SET IDENTITY_INSERT erp_bookstore_content ON;

                INSERT INTO erp_bookstore_content (content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty)
                SELECT content_id, original_title, author, genre, content_type, schoolyear, summary, keywords, state, publisher, image_url, reading_time, difficulty
                FROM erp_bookstore_content_del WHERE del_id = @delId;

                SET IDENTITY_INSERT erp_bookstore_content OFF;

                INSERT INTO erp_bookstore_item (bcode, content_id, book_title, author, publisher, image_url)
                SELECT bcode, content_id, book_title, author, publisher, image_url
                FROM erp_bookstore_item_del
                WHERE content_id = (SELECT content_id FROM erp_bookstore_content_del WHERE del_id = @delId);

                INSERT INTO erp_bookstore_itempool (content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state)
                SELECT content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state
                FROM erp_bookstore_itempool_del
                WHERE content_id = (SELECT content_id FROM erp_bookstore_content_del WHERE del_id = @delId);

                DELETE FROM erp_bookstore_itempool_del
                WHERE content_id = (SELECT content_id FROM erp_bookstore_content_del WHERE del_id = @delId);

                DELETE FROM erp_bookstore_item_del
                WHERE content_id = (SELECT content_id FROM erp_bookstore_content_del WHERE del_id = @delId);

                DELETE FROM erp_bookstore_content_del WHERE del_id = @delId;
            END
        """);
    }
}
