package com.hohoedu.book_clinic.demo;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 시연 데이터 기준점 저장/원복 (2026-10-01).
 *
 * 테이블 이름이 바뀌는 동적 SQL이라 MyBatis 매퍼 대신 JdbcTemplate을 쓴다. SQL에 끼워 넣는 값은
 * 전부 코드 상수(테이블명, DemoResetService.DEMO_STUDENT_IDS)뿐이라 외부 입력이 섞이지 않는다.
 * JdbcTemplate도 @Transactional 트랜잭션에 함께 묶인다(같은 커넥션).
 */
@Repository
@RequiredArgsConstructor
public class DemoResetRepository {

    static final String SNAP_PREFIX = "erp_bookstore_demo_snap_";

    /** 원복 대상 테이블 — 부모→자식 순서(복원 순서). 삭제는 이 역순으로 한다 */
    enum Target {
        CLINIC_SESSION("erp_bookstore_clinic_session", "student_id IN %s"),
        DIARY("erp_bookstore_diary",
                "student_id IN %1$s OR session_id IN (SELECT session_id FROM erp_bookstore_clinic_session WHERE student_id IN %1$s)"),
        ATTITUDE("erp_bookstore_attitude",
                "student_id IN %1$s OR diary_key IN (SELECT diary_key FROM erp_bookstore_diary WHERE student_id IN %1$s)"),
        RECOMMEND_LOG("erp_bookstore_recommend_log", "student_id IN %s"),
        DIARY_DETAIL("erp_bookstore_diary_detail",
                "diary_key IN (SELECT diary_key FROM erp_bookstore_diary WHERE student_id IN %1$s)"
                        + " OR recommend_id IN (SELECT recommend_id FROM erp_bookstore_recommend_log WHERE student_id IN %1$s)"),
        QUIZ_ANSWER_LOG("erp_bookstore_quiz_answer_log",
                "student_id IN %1$s OR recommend_id IN (SELECT recommend_id FROM erp_bookstore_recommend_log WHERE student_id IN %1$s)"),
        ITEM_LOAN("erp_bookstore_item_loan", "student_id IN %s"),
        PASS("erp_bookstore_pass", "student_id IN %s"),
        PASS_USE("erp_bookstore_pass_use",
                "student_id IN %1$s OR pass_id IN (SELECT pass_id FROM erp_bookstore_pass WHERE student_id IN %1$s)"),
        RESERVATION("erp_bookstore_reservation", "student_id IN %s"),
        RESERVATION_LOG("erp_bookstore_reservation_log",
                "reservation_id IN (SELECT reservation_id FROM erp_bookstore_reservation WHERE student_id IN %1$s)"),
        STUDENT_BADGE("erp_bookstore_student_badge", "student_id IN %s"),
        STUDENT_CARD("erp_bookstore_student_card", "student_id IN %s");

        final String table;
        final String filter;

        Target(String table, String filter) {
            this.table = table;
            this.filter = filter;
        }

        String snapTable() {
            return SNAP_PREFIX + table.substring("erp_bookstore_".length());
        }

        String where(String studentIn) {
            return String.format(filter, studentIn);
        }
    }

    private final JdbcTemplate jdbcTemplate;

    /** ('A','B',...) — 코드 상수인 학생 ID만 받는다 */
    static String studentIn(List<String> studentIds) {
        return studentIds.stream().map(id -> "'" + id.replace("'", "''") + "'")
                .collect(Collectors.joining(",", "(", ")"));
    }

    /** 기준점을 저장할 때의 시연 학생 목록 — 목록이 바뀐 뒤 옛 기준점으로 원복하는 사고를 막는다 */
    static final String SNAP_STUDENTS = SNAP_PREFIX + "students";

    public void saveSnapshotStudents(List<String> studentIds) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + SNAP_STUDENTS);
        jdbcTemplate.execute("CREATE TABLE " + SNAP_STUDENTS + " (student_id VARCHAR(100) NOT NULL PRIMARY KEY)");
        for (String id : studentIds) {
            jdbcTemplate.update("INSERT INTO " + SNAP_STUDENTS + " (student_id) VALUES (?)", id);
        }
    }

    /** 기준점 저장 당시 학생 목록 — 학생 목록 테이블이 없던 시절 기준점이면 null */
    public List<String> findSnapshotStudents() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END", Integer.class, SNAP_STUDENTS);
        if (exists == null || exists == 0) return null;
        return jdbcTemplate.queryForList("SELECT student_id FROM " + SNAP_STUDENTS, String.class);
    }

    public boolean hasSnapshot() {
        for (Target t : Target.values()) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END", Integer.class, t.snapTable());
            if (exists == null || exists == 0) return false;
        }
        return true;
    }

    /** 기준점 저장 — 스냅샷 테이블을 새로 만들고 지금 행을 복사한다. 저장한 행 수 */
    public int saveSnapshot(Target t, String studentIn) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + t.snapTable());
        return jdbcTemplate.update("SELECT * INTO " + t.snapTable() + " FROM " + t.table + " WHERE " + t.where(studentIn));
    }

    public int deleteCurrent(Target t, String studentIn) {
        return jdbcTemplate.update("DELETE FROM " + t.table + " WHERE " + t.where(studentIn));
    }

    /** 스냅샷 행을 원래 PK 그대로 되돌려 넣는다 — 다른 테이블이 들고 있는 ID 참조가 그대로 맞는다 */
    public int restoreSnapshot(Target t) {
        String columns = String.join(", ", jdbcTemplate.queryForList(
                "SELECT QUOTENAME(name) FROM sys.columns WHERE object_id = OBJECT_ID(?) AND is_computed = 0 ORDER BY column_id",
                String.class, t.snapTable()));
        String insert = "INSERT INTO " + t.table + " (" + columns + ") SELECT " + columns + " FROM " + t.snapTable();
        Integer hasIdentity = jdbcTemplate.queryForObject(
                "SELECT OBJECTPROPERTY(OBJECT_ID(?), 'TableHasIdentity')", Integer.class, t.table);
        if (hasIdentity == null || hasIdentity == 0) {
            return jdbcTemplate.update(insert);
        }
        jdbcTemplate.execute("SET IDENTITY_INSERT " + t.table + " ON");
        try {
            return jdbcTemplate.update(insert);
        } finally {
            jdbcTemplate.execute("SET IDENTITY_INSERT " + t.table + " OFF");
        }
    }

    /**
     * 실물 재고 차이만큼 되돌린다 — item은 공용 테이블이라 통째로 덮을 수 없다. 재고 증감은 항상
     * item_loan 상태 전환과 짝(대여 LOANED → loaned_qty+1, 반납 → -1, 분실 LOST → loaned-1·lost+1)이므로,
     * 시연 학생의 대여 상태별 건수를 "지금 vs 기준점"으로 비교한 차이를 빼면 기준점 수량으로 돌아간다.
     */
    public int restoreItemQty(String studentIn) {
        String snap = Target.ITEM_LOAN.snapTable();
        return jdbcTemplate.update(
                "UPDATE i SET"
                        + " loaned_qty = CASE WHEN i.loaned_qty - d.loaned_diff < 0 THEN 0 ELSE i.loaned_qty - d.loaned_diff END,"
                        + " lost_qty   = CASE WHEN i.lost_qty - d.lost_diff < 0 THEN 0 ELSE i.lost_qty - d.lost_diff END"
                        + " FROM erp_bookstore_item i"
                        + " JOIN ("
                        + "   SELECT item_id,"
                        + "          SUM(CASE WHEN status = 'LOANED' THEN s ELSE 0 END) AS loaned_diff,"
                        + "          SUM(CASE WHEN status = 'LOST' THEN s ELSE 0 END) AS lost_diff"
                        + "   FROM (SELECT item_id, status, 1 AS s FROM erp_bookstore_item_loan WHERE student_id IN " + studentIn
                        + "         UNION ALL SELECT item_id, status, -1 FROM " + snap + ") x"
                        + "   GROUP BY item_id"
                        + " ) d ON d.item_id = i.item_id"
                        + " WHERE d.loaned_diff <> 0 OR d.lost_diff <> 0");
    }

    /** 회차 정원 차이만큼 되돌린다 — 취소되지 않은 예약 건수를 "지금 vs 기준점"으로 비교 */
    public int restoreSlotSeats(String studentIn) {
        String snap = Target.RESERVATION.snapTable();
        return jdbcTemplate.update(
                "UPDATE si SET reserved_count = CASE WHEN si.reserved_count - d.diff < 0 THEN 0 ELSE si.reserved_count - d.diff END"
                        + " FROM erp_bookstore_slot_instance si"
                        + " JOIN ("
                        + "   SELECT slot_instance_id, SUM(s) AS diff"
                        + "   FROM (SELECT slot_instance_id, 1 AS s FROM erp_bookstore_reservation"
                        + "         WHERE student_id IN " + studentIn + " AND status <> 'CANCELED'"
                        + "         UNION ALL SELECT slot_instance_id, -1 FROM " + snap + " WHERE status <> 'CANCELED') x"
                        + "   GROUP BY slot_instance_id"
                        + " ) d ON d.slot_instance_id = si.slot_instance_id"
                        + " WHERE d.diff <> 0");
    }

    /** 시연 중 새로 생긴 예약 ID — Firestore 모니터링 카드 문서 키(clinic_monitor/{reservationId}) */
    public List<Long> findNewReservationIds(String studentIn) {
        return jdbcTemplate.queryForList(
                "SELECT reservation_id FROM erp_bookstore_reservation WHERE student_id IN " + studentIn
                        + " AND reservation_id NOT IN (SELECT reservation_id FROM " + Target.RESERVATION.snapTable() + ")",
                Long.class);
    }
}
