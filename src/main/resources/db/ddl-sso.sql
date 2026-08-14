-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 배포용 DDL — 올패스 ↔ 호호책방 SSO 토큰 브릿지 (2026-08-14)
--
-- [왜 이 파일이 따로 있나] 다른 도메인 DDL과 동일한 이유 — 운영에서는 schema.sql이
-- 실행되지 않아(spring.sql.init 미설정, ddl-auto: validate) 이 파일을 직접 실행해야 한다.
-- 모든 구문이 IF OBJECT_ID(...) IS NULL로 감싸여 있어 여러 번 실행해도 안전하다.
--
-- [공유 테이블] sso_ticket은 all_pass와 book_clinic이 함께 쓰는 dbhohoedu_stst DB에
-- 만들어지며, 두 앱이 각자 자기 리포지토리의 마이그레이션 관례에 맞춰 이 DDL을
-- 문서화한다(all_pass 쪽은 sql/2026-08-14_sso_ticket.sql, 내용은 동일). 실제 CREATE는
-- 한 번만 실행하면 된다 — 멱등 가드 덕분에 두 번 실행해도 안전하다.
--
-- [용도] 올패스 직원이 자기 계정 그대로 호호책방 관리자 화면으로(또는 반대로) 이동할 때,
-- 서로 다른 도메인이라 세션 쿠키를 공유할 수 없어 원타임 서명 토큰(JWT, RS256)으로
-- 다리를 놓는다. 이 테이블은 그 토큰의 jti(1회성 식별자)를 저장해 재사용(replay)을
-- 막는 용도다 — 토큰 자체는 서명으로 위변조를 막고, 이 테이블은 "이미 한 번 쓴 토큰인지"를
-- 원자적 UPDATE 한 번으로 판별한다.
-- ════════════════════════════════════════════════════════════════════

IF OBJECT_ID('sso_ticket', 'U') IS NULL
BEGIN
    CREATE TABLE sso_ticket (
        jti          VARCHAR(36)   NOT NULL PRIMARY KEY,        -- 토큰의 jti(UUID), 발급 시 서버가 생성
        issuer       VARCHAR(20)   NOT NULL,                    -- 'all-pass' | 'book-clinic' (발급 측)
        user_id      VARCHAR(100)  NOT NULL,                    -- erp_user.user_id (두 앱 공통 로그인 아이디)
        redirect_url VARCHAR(500)  NULL,                        -- 검증 성공 후 이동할 목적지 경로
        used_yn      BIT           NOT NULL DEFAULT 0,          -- 소비 여부 (원자적 UPDATE로만 0→1 전이)
        issued_at    DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
        expires_at   DATETIME2     NOT NULL                     -- issued_at + 30초 (발급 측 애플리케이션이 계산해 INSERT)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_sso_ticket_expires_at' AND object_id = OBJECT_ID('sso_ticket'))
BEGIN
    CREATE INDEX ix_sso_ticket_expires_at ON sso_ticket (expires_at);
END
GO
