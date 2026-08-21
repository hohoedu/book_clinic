-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_reservation.service_date 컬럼 + 유니크 인덱스 추가 (2026-08-21)
--
-- schema.sql은 2026-08-20에 이 컬럼과 UX_reservation_student_date 인덱스를 추가했는데,
-- 그 시점에 ddl-schedule.sql에는 반영이 안 돼서 patch-260821.sql(ddl-schedule.sql 기반)로
-- 만든 운영 테이블에는 이 컬럼이 빠져 있었다("Invalid column name 'service_date'" 에러로 발견,
-- 2026-08-21). ddl-schedule.sql은 이 패치와 함께 최신 구조로 같이 수정해서, 새로 만드는 DB는
-- 이 스크립트가 필요 없다.
--
-- 테이블이 방금 만들어져 비어 있을 걸 전제로 NOT NULL로 바로 추가한다(기존 행이 있으면
-- slot_instance.service_date 값으로 먼저 채운 뒤 NOT NULL을 걸어야 한다).
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_reservation') AND name = 'service_date')
    ALTER TABLE erp_bookstore_reservation ADD service_date DATE NOT NULL DEFAULT '1900-01-01';
GO

-- 기본값은 컬럼을 안전하게 추가하기 위한 임시값일 뿐이다. 실제 값은 slot_instance에서 채운다.
UPDATE r
SET r.service_date = si.service_date
FROM erp_bookstore_reservation r
JOIN erp_bookstore_slot_instance si ON si.slot_instance_id = r.slot_instance_id
WHERE r.service_date = '1900-01-01';
GO

-- 임시 DEFAULT 제약은 필요 없으니 정리한다 (제약 이름은 SQL Server가 자동 생성한 이름이라
-- sys.default_constraints에서 컬럼 기준으로 찾아 지운다).
DECLARE @dfName SYSNAME;
SELECT @dfName = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID('erp_bookstore_reservation') AND c.name = 'service_date';
IF @dfName IS NOT NULL
    EXEC('ALTER TABLE erp_bookstore_reservation DROP CONSTRAINT ' + @dfName);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_reservation_student_date' AND object_id = OBJECT_ID('erp_bookstore_reservation'))
    CREATE UNIQUE INDEX UX_reservation_student_date
        ON erp_bookstore_reservation (student_id, service_date)
        WHERE status IN ('RESERVED', 'ATTENDED', 'NOSHOW');
GO
