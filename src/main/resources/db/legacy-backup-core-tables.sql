-- ════════════════════════════════════════════════════════════════════
-- 레거시 도서 테이블 백업(이름변경) — 2026-08-06
--
-- [무엇을 하나] erp_bookstore_code / erp_bookstore_content / erp_bookstore_item /
-- erp_bookstore_itempool 4개 테이블이 운영 DB에 이미 있는데, 지금 코드가 기대하는
-- 구조와 완전히 다른 옛날 설계다(예: item에 item_id/qty 카운터가 없음, itempool에
-- qlevel이 없음). 이 스크립트는 그 4개 테이블을 "_legacy_20260806" 접미사를 붙여
-- 이름만 바꿔서 그대로 보존한다. 데이터는 1도 건드리지 않는다.
--
-- [왜 이름만 바꾸나 — 안전한 이유]
--  - DROP이 아니라 RENAME이라 데이터가 전혀 지워지지 않는다.
--  - sp_rename은 즉시 되돌릴 수 있다. 문제가 생기면 아래 "되돌리기" 섹션을 실행하면
--    원래 이름으로 복구된다.
--  - 이름을 비워두면 원래 이름(erp_bookstore_content 등)이 다시 비어서, 뒤이어
--    ddl-core.sql을 실행하면 최신 구조로 새 테이블이 깨끗하게 만들어진다.
--
-- [PK 제약조건도 같이 바꾸는 이유] 테이블 이름만 바꾸면 그 테이블의 PK 제약조건
-- 이름(예: PK_erp_bookstore_code)은 그대로 남는다. 새 ddl-core.sql이 같은 이름으로
-- 새 PK를 만들려고 하면 "이미 사용 중인 이름"이라 실패한다. 그래서 테이블을
-- 바꾸기 전에 그 테이블에 딸린 PK/UNIQUE 제약조건 이름도 먼저 찾아서 같이 바꾼다.
--
-- [실행 후 확인] 스크립트 끝에 있는 SELECT로 바뀐 이름들을 확인할 수 있다.
-- 확인되면 이어서 ddl-core.sql 을 실행하면 된다.
-- ════════════════════════════════════════════════════════════════════

DECLARE @suffix VARCHAR(30) = '_legacy_20260806';
DECLARE @tableName SYSNAME, @constraintName SYSNAME, @sql NVARCHAR(500);

DECLARE @targets TABLE (name SYSNAME);
INSERT INTO @targets VALUES
    ('erp_bookstore_code'), ('erp_bookstore_content'),
    ('erp_bookstore_item'), ('erp_bookstore_itempool');

-- 1) 대상 테이블에 딸린 PK/UNIQUE 제약조건 이름을 먼저 바꾼다 (이름 충돌 방지)
DECLARE con_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT OBJECT_NAME(kc.parent_object_id), kc.name
    FROM sys.key_constraints kc
    JOIN @targets t ON t.name = OBJECT_NAME(kc.parent_object_id)
    WHERE kc.type IN ('PK', 'UQ');

OPEN con_cursor;
FETCH NEXT FROM con_cursor INTO @tableName, @constraintName;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = 'EXEC sp_rename ''' + @constraintName + ''', ''' + @constraintName + @suffix + ''', ''OBJECT''';
    PRINT @sql;
    EXEC sp_executesql @sql;
    FETCH NEXT FROM con_cursor INTO @tableName, @constraintName;
END
CLOSE con_cursor;
DEALLOCATE con_cursor;

-- 2) 테이블 자체 이름을 바꾼다
DECLARE tbl_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @targets;
OPEN tbl_cursor;
FETCH NEXT FROM tbl_cursor INTO @tableName;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID(@tableName, 'U') IS NOT NULL
    BEGIN
        SET @sql = 'EXEC sp_rename ''' + @tableName + ''', ''' + @tableName + @suffix + '''';
        PRINT @sql;
        EXEC sp_executesql @sql;
    END
    FETCH NEXT FROM tbl_cursor INTO @tableName;
END
CLOSE tbl_cursor;
DEALLOCATE tbl_cursor;

-- 3) 결과 확인 — 4개 테이블 모두 xxx_legacy_20260806로 보이면 성공
SELECT name AS 백업된_테이블명
FROM sys.tables
WHERE name LIKE '%_legacy_20260806';

-- ════════════════════════════════════════════════════════════════════
-- [되돌리기] 문제가 생겨서 원래 이름으로 되돌리고 싶으면 아래 4줄을 실행한다
-- (PK 제약조건 이름은 굳이 원복하지 않아도 동작에는 지장 없다).
-- ════════════════════════════════════════════════════════════════════
-- EXEC sp_rename 'erp_bookstore_code_legacy_20260806', 'erp_bookstore_code';
-- EXEC sp_rename 'erp_bookstore_content_legacy_20260806', 'erp_bookstore_content';
-- EXEC sp_rename 'erp_bookstore_item_legacy_20260806', 'erp_bookstore_item';
-- EXEC sp_rename 'erp_bookstore_itempool_legacy_20260806', 'erp_bookstore_itempool';
