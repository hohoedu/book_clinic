-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 자동결제(정기결제) 전환 (2026-09-07)
--
-- 매달 학부모가 직접 결제하던 일시불 방식을, 카드 1회 등록 후 매월 자동 청구되는 구독으로
-- 바꾼다. 설계 배경과 결정 근거는 docs/자동결제_전환_설계_260907.md에 있다.
--
-- [운영 적용 순서] 이 파일을 실행하기 전에 진행 중(READY)인 결제가 없는 시간대를 고른다.
-- UX_payment_active_billing을 내리고 UX_payment_active_cycle로 갈아끼우는 사이에 새 결제가
-- 들어오면 그 순간만 중복 청구 방어선이 비어 있다.
--
-- [주의] cycle_from 백필이 끝나기 전에 UX_payment_active_cycle을 만들면, cycle_from이 NULL인
-- READY/PAID 행이 둘 이상일 때 인덱스 생성이 실패한다. 아래 순서를 지켜서 실행한다.
-- 인덱스 생성이 실패하면 billing_ym이 NULL인 과거 행이 남아 있다는 뜻이므로 다음으로 확인한다:
--   SELECT payment_id, order_no, student_id, status, billing_ym, cycle_from
--   FROM erp_bookstore_payment
--   WHERE cycle_from IS NULL AND status IN ('READY','PAID');
-- ════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════
-- 자동결제(정기결제) — 구독 / 구독 멤버 (2026-09-07, KG이니시스 빌키)
-- 설계 배경 전체는 docs/자동결제_전환_설계_260907.md
--
-- [왜 payment와 따로 두나] payment 행은 "이번 주기에 돈이 한 번 오갔다"는 사실이고,
-- 구독은 "매달 이 카드로 이 학생들을 계속 청구한다"는 지속 상태다. 청구가 실패해도 구독은
-- 살아 있어야 하고(재시도), 해지해도 과거 payment 행은 그대로 남아야 한다. 한 테이블에
-- 섞으면 "마지막 결제 행"이 곧 구독 상태가 되어, 실패한 달에 구독이 사라지는 꼴이 된다.
--
-- [단위가 학생이 아니라 카드다] 형제는 카드 1장에 합산해서 승인 1건으로 나간다(기존
-- 묶음결제와 같은 방식). 그래서 구독은 빌키(카드) 하나를 나타내고, 청구 대상 학생은
-- member 테이블로 매단다. 학생 단위로 잡으면 형제 수만큼 승인이 쪼개져서 결정이 뒤집힌다.
--
-- [빌키 취급] bill_key는 그 자체로 과금이 가능한 자격증명이다. 로그·API 응답·화면 어디에도
-- 내보내지 않는다(사용자에게는 마스킹 카드번호만 보여준다). 조회 쿼리에서도 필요한 곳
-- (배치 승인 요청)에서만 꺼낸다.
IF OBJECT_ID('erp_bookstore_subscription', 'U') IS NULL
CREATE TABLE erp_bookstore_subscription (
    subscription_id  INT           IDENTITY(1,1) PRIMARY KEY,
    reg_order_no     VARCHAR(40)   NOT NULL UNIQUE,  -- 카드등록 결제창에 넘긴 주문번호(oid).
                                                -- 결제창에서 돌아오는 요청에는 세션 쿠키가 딸려오지 않아
                                                -- (이니시스 도메인발 cross-site POST) "누가 등록 중이었는지"를
                                                -- 이 값으로만 되짚을 수 있다. payment.order_no와 같은 역할이다
    owner_student_id VARCHAR(100)  NOT NULL,    -- 카드를 등록한 학부모의 대표 자녀(앱 로그인 학생).
                                                -- erp_student와 FK 없이 값으로만 연결한다(payment와 같은 이유)
    center_code      VARCHAR(50),               -- 정산/조회 필터용 스냅샷
    bill_key         VARCHAR(100),              -- 이니시스 CARD_BillKey. 규격은 40byte지만 여유를 둔다.
                                                -- 카드등록 승인이 나기 전(PENDING)에는 NULL이다 —
                                                -- 결제가 READY로 먼저 기록되는 것과 같은 이유로, 등록창까지
                                                -- 갔다가 이탈한 건도 남아야 추적이 된다
    card_name        VARCHAR(30),               -- 카드사명 (화면 표시용)
    card_no          VARCHAR(20),               -- 마스킹된 카드번호. 원본은 절대 저장하지 않는다
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
        -- PENDING(카드등록창 띄움, 아직 빌키 없음) / ACTIVE(정상) / PAUSED(연속 실패로 자동 중지)
        -- / CANCELED(해지 또는 등록 미완료로 닫힘)
        -- PAUSED는 이미 발급된 이용권을 회수하지 않는다 — 결제가 끝난 주기는 정당하게 산 것이고
        -- 다음 주기가 열리지 않을 뿐이다. 회수는 환불 경로(PaymentService.refund)만 담당한다
    anchor_day       TINYINT,                   -- 매월 청구 일자(1~31) = 최초 결제일의 일자. PENDING이면 NULL.
                                                -- 그 달에 없는 일자면(31일 앵커의 2월) 청구는 말일로 당기되
                                                -- 이 값 자체는 보존한다 — 안 그러면 한 번 28일로 밀린 앵커가
                                                -- 영영 28일로 굳어 매달 조금씩 앞당겨진다
    next_billing_on  DATE,                      -- 다음 청구를 "시도할" 날짜(KST). 배치가 조건부 UPDATE로 선점하는
                                                -- 컬럼이라 이 값이 곧 중복 청구 방지의 1차 장치다. PENDING이면 NULL
    billing_cycle_from DATE,                    -- 다음 청구가 커버할 주기의 시작일. 정상적으로는 next_billing_on과
                                                -- 같지만, 청구가 실패해 재시도가 D+1로 밀리면 둘이 갈라진다.
                                                -- 이용권 유효기간의 기준은 언제나 이 값이다 — 시도일을 기준으로
                                                -- 삼으면 카드사 사정으로 실패할 때마다 학생의 이용 기간이
                                                -- 하루씩 잘려나간다
    last_paid_at     DATETIME2,                 -- 마지막 청구 성공 시각(KST)
    fail_count       SMALLINT      NOT NULL DEFAULT 0,   -- 연속 실패 횟수. 성공하면 0으로 되돌린다
    last_fail_reason VARCHAR(200),              -- 마지막 실패 사유(카드사 거절 메시지 등). 상담 응대용
    card_reg_at      DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 카드(빌키) 등록 시각
    canceled_at      DATETIME2,                 -- 해지 시각. NULL이면 해지 안 됨
    created_at       DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 배치가 매일 "오늘 청구할 구독"만 집어가는 경로. status가 앞이라야 ACTIVE 구간만 스캔한다
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_subscription_due' AND object_id = OBJECT_ID('erp_bookstore_subscription'))
    CREATE INDEX IX_subscription_due ON erp_bookstore_subscription (status, next_billing_on);

-- 앱에서 "내 구독" 조회
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_subscription_owner' AND object_id = OBJECT_ID('erp_bookstore_subscription'))
    CREATE INDEX IX_subscription_owner ON erp_bookstore_subscription (owner_student_id);

-- 구독에 묶인 청구 대상 학생. 형제는 여기 여러 행이 되고, 승인은 이 행들의 금액 합계로 1건만 나간다.
--
-- [상품을 구독이 아니라 멤버가 들고 있는 이유] 형제라도 학년/이용 횟수가 달라 서로 다른 상품을
-- 살 수 있다. 구독에 상품을 두면 그 경우를 표현할 수 없다.
--
-- [금액 스냅샷을 두지 않는다] 여기에 가격을 복사해 두면 본사가 가격을 올렸을 때 어느 쪽이
-- 진짜인지 모호해진다. 매 청구 시점에 product의 현재가를 읽어 payment.amount에 스냅샷으로
-- 남긴다 — "그때 얼마였나"의 답은 언제나 payment 행이다.
IF OBJECT_ID('erp_bookstore_subscription_member', 'U') IS NULL
CREATE TABLE erp_bookstore_subscription_member (
    member_id       INT          IDENTITY(1,1) PRIMARY KEY,
    subscription_id INT          NOT NULL,
    student_id      VARCHAR(100) NOT NULL,      -- 청구 대상 학생
    product_id      INT          NOT NULL,      -- erp_bookstore_product.product_id
    service_code    VARCHAR(10)  NOT NULL,      -- 상품의 service_code 스냅샷(아래 유니크 인덱스에 쓴다)
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
        -- PENDING(카드등록 진행 중) / ACTIVE(청구 대상) / REMOVED(구독에서 빠짐)
        -- 등록이 확정될 때 PENDING → ACTIVE로 올린다. 등록창에서 이탈한 건을 ACTIVE로 두면
        -- 아래 유니크 인덱스에 걸려 그 학생이 영영 재등록을 못 하게 된다
    joined_at       DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    removed_at      DATETIME2,
    FOREIGN KEY (subscription_id) REFERENCES erp_bookstore_subscription(subscription_id),
    FOREIGN KEY (product_id) REFERENCES erp_bookstore_product(product_id)
);

-- 한 학생이 같은 서비스로 두 구독에 동시에 들어가면 매달 이중 청구된다(예: 아빠 카드와 엄마
-- 카드로 각각 등록). 애플리케이션 체크만으로는 두 기기 동시 등록의 레이스를 못 막아서 DB에도 건다.
-- 걸리는 시점은 등록 확정(PENDING → ACTIVE)이라, 두 기기가 동시에 등록창을 띄우는 것 자체는
-- 허용되고 먼저 확정한 쪽만 살아남는다 — 첫 청구가 나가기 전에 갈리므로 돈이 두 번 빠지지 않는다.
-- 구독을 해지할 때는 멤버도 반드시 REMOVED로 내려야 이 인덱스가 새 구독 등록을 막지 않는다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_subscription_member_active' AND object_id = OBJECT_ID('erp_bookstore_subscription_member'))
    CREATE UNIQUE INDEX UX_subscription_member_active ON erp_bookstore_subscription_member (student_id, service_code)
        WHERE status = 'ACTIVE';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_subscription_member_sub' AND object_id = OBJECT_ID('erp_bookstore_subscription_member'))
    CREATE INDEX IX_subscription_member_sub ON erp_bookstore_subscription_member (subscription_id, status);

-- ─────────────────────────────────────────────────────────
-- erp_bookstore_payment — 자동결제 전환에 따른 컬럼 추가 (2026-09-07)
--
-- [왜 cycle_from/until이 필요한가] 이용권 주기가 달력 월에서 "결제일 기준 1개월"로 바뀌었다
-- (3/15 결제 → 3/15~4/14). billing_ym(CHAR(6))으로는 이 주기를 표현할 수 없다 — 같은 202603이라도
-- 3/1~3/31과 3/15~4/14는 다른 주기다. 그래서 주기를 결제 행이 직접 들고, billing_ym은 정산 대조용
-- 라벨로만 남긴다(자동결제분은 cycle_from이 속한 달을 적는다).
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'subscription_id')
    ALTER TABLE erp_bookstore_payment ADD subscription_id INT;   -- 자동결제로 생긴 건만 채워진다(과거 일시불/수기건은 NULL)
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'cycle_from')
    ALTER TABLE erp_bookstore_payment ADD cycle_from DATE;       -- 이 결제가 커버하는 주기 시작일
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'cycle_until')
    ALTER TABLE erp_bookstore_payment ADD cycle_until DATE;      -- 주기 종료일(다음 주기 시작 전일)

-- 과거 일시불 건 백필 — 그 시절의 주기는 곧 달력 월이므로 billing_ym에서 역산한다.
-- (백필해 두지 않으면 아래 필터드 유니크에서 NULL 행이 한 건만 허용되어 INSERT가 깨진다)
UPDATE erp_bookstore_payment
SET cycle_from  = DATEFROMPARTS(LEFT(billing_ym, 4), RIGHT(billing_ym, 2), 1),
    cycle_until = EOMONTH(DATEFROMPARTS(LEFT(billing_ym, 4), RIGHT(billing_ym, 2), 1))
WHERE cycle_from IS NULL AND billing_ym IS NOT NULL;

-- 중복 청구 최종 방어선 교체 — 같은 학생·서비스·주기에 진행중(READY)이거나 완료(PAID)된 결제가
-- 둘 이상 있지 못하게 한다. 앵커 주기에서는 billing_ym이 주기를 대표하지 못하므로
-- (앵커 1일이면 두 주기의 billing_ym이 겹칠 수 있다) 기준을 cycle_from으로 옮긴다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_active_billing' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    DROP INDEX UX_payment_active_billing ON erp_bookstore_payment;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_active_cycle' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE UNIQUE INDEX UX_payment_active_cycle ON erp_bookstore_payment (student_id, service_code, cycle_from)
        WHERE status IN ('READY', 'PAID') AND cycle_from IS NOT NULL;

-- 구독별 청구 이력 조회 (앱의 "다음 결제일/지난 결제" 화면, 실패 재시도 판단)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_subscription' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_subscription ON erp_bookstore_payment (subscription_id, cycle_from)
        WHERE subscription_id IS NOT NULL;
