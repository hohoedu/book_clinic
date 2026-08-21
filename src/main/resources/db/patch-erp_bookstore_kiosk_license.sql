-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — erp_bookstore_kiosk_license / erp_bookstore_kiosk_device 신규 생성 (2026-08-21)
--
-- schema.sql에는 있지만 운영 DB(dbhohoedu_stst)에는 반영이 안 돼서
-- "Invalid object name 'erp_bookstore_kiosk_license'" 에러로 발견 (2026-08-21, KioskService.issue).
-- schema.sql 원본(erp_bookstore_kiosk_license/erp_bookstore_kiosk_device 블록)을 그대로 옮겼다.
-- 전부 IF OBJECT_ID/COL_LENGTH IS NULL 가드가 걸려 있어 여러 번 실행해도 안전하다.
-- ════════════════════════════════════════════════════════════════════

IF OBJECT_ID('erp_bookstore_kiosk_license', 'U') IS NULL
CREATE TABLE erp_bookstore_kiosk_license (
    license_id     INT IDENTITY(1,1) PRIMARY KEY,
    center_code    VARCHAR(20)   NULL,
    key_hash       VARCHAR(64)   NOT NULL,
    label          VARCHAR(50)   NOT NULL,
    issued_at      DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    issued_by      VARCHAR(100),
    device_limit   INT           NOT NULL DEFAULT 1,
    revoked_at     DATETIME2,
    revoked_by     VARCHAR(100),
    CONSTRAINT UQ_kiosk_license_hash UNIQUE (key_hash),
    FOREIGN KEY (center_code) REFERENCES erp_center(center_code)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_kiosk_license_center' AND object_id = OBJECT_ID('erp_bookstore_kiosk_license'))
    CREATE INDEX IX_kiosk_license_center
        ON erp_bookstore_kiosk_license (center_code)
        WHERE revoked_at IS NULL;
GO

IF COL_LENGTH('erp_bookstore_kiosk_license', 'device_limit') IS NULL
    ALTER TABLE erp_bookstore_kiosk_license ADD device_limit INT NOT NULL DEFAULT 1;
GO

IF OBJECT_ID('erp_bookstore_kiosk_device', 'U') IS NULL
CREATE TABLE erp_bookstore_kiosk_device (
    device_id      INT IDENTITY(1,1) PRIMARY KEY,
    license_id     INT           NOT NULL,
    token_hash     VARCHAR(64)   NOT NULL,
    user_agent     VARCHAR(255),
    registered_at  DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    last_used_at   DATETIME2,
    revoked_at     DATETIME2,
    revoked_by     VARCHAR(100),
    CONSTRAINT UQ_kiosk_device_hash UNIQUE (token_hash),
    FOREIGN KEY (license_id) REFERENCES erp_bookstore_kiosk_license (license_id)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_kiosk_device_license' AND object_id = OBJECT_ID('erp_bookstore_kiosk_device'))
    CREATE INDEX IX_kiosk_device_license
        ON erp_bookstore_kiosk_device (license_id)
        WHERE revoked_at IS NULL;
GO

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_NAME = 'erp_bookstore_kiosk_license'
             AND COLUMN_NAME = 'center_code' AND IS_NULLABLE = 'NO')
    ALTER TABLE erp_bookstore_kiosk_license ALTER COLUMN center_code VARCHAR(20) NULL;
GO
