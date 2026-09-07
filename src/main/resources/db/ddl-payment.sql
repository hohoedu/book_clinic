-- ════════════════════════════════════════════════════════════════════
-- 운영 DB 배포용 DDL — 이용권/결제 도메인 (2026-08-04)
--
-- [왜 이 파일이 따로 있나]
-- application-prod.yml에는 spring.sql.init 설정이 없어서 운영에서는 schema.sql이
-- 실행되지 않는다(ddl-auto도 validate다). 운영 테이블은 사람이 직접 만들어야 한다.
-- 그런데 schema.sql 상단에는 erp_student/erp_user를 DROP하는 구문이 있고, 운영 DB는
-- all_pass(서당)와 공유한다. 그 파일을 통째로 운영에서 실행하면 서당 데이터까지 날아간다.
-- 그래서 DROP이 한 줄도 없는 이 파일을 따로 둔다.
--
-- [사용법] 운영 DB에 이 파일만 실행한다. 모든 구문이 IF OBJECT_ID(...) IS NULL /
-- IF NOT EXISTS로 감싸여 있어 여러 번 실행해도 안전하고, 기존 데이터를 건드리지 않는다.
--
-- [독서클리닉 도메인은 별도 파일] 추천/문제풀이/일지/뱃지/실물도서재고 등은
-- src/main/resources/db/ddl-core.sql에 같은 방식으로 분리해 뒀다. 운영에 처음
-- 배포할 때는 두 파일을 모두 실행해야 하며, 서로 참조 관계가 없어 순서는 무관하다.
--
-- [원본] src/main/resources/db/schema.sql의 이용권/결제 섹션과 같은 내용이다.
-- 스키마를 고칠 일이 있으면 두 파일을 함께 고쳐야 한다.
-- ════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════
-- 이용권 / 결제 / 환불 (2026-08-04 재설계, KG이니시스)
--
-- [상품] 월 N회 횟수권. 출석(입실) 1회당 1회 차감되고 소진되면 끝난다.
-- 자동 재결제(빌키)는 없고 할부도 받지 않는다(이니시스 quotabase로 일시불 고정).
-- 그래서 빌키·할부 관련 컬럼이 없다.
--
-- [핵심 구조 — 이용권과 결제의 분리] 프로그램비를 걷는 방법이 학생에 따라 다르다.
--   · 책방만 이용   → 모바일 앱에서 학부모가 PG(이니시스)로 직접 결제
--   · 서당 병행     → 교재비에 프로그램비를 얹어 전월 20일 일괄 청구 (별도 프로젝트 all_pass 소관)
-- 두 경우 모두 "이 학생이 이번 달 몇 회 쓸 수 있는가"는 똑같이 필요하지만, 서당 학생은
-- 이 시스템에 결제 행 자체가 생기지 않는다. 그래서 횟수를 payment에 두면 서당 학생을
-- 표현할 수 없다. 이용권(erp_bookstore_pass)을 먼저 두고, PG 결제는 그 이용권이 생긴
-- 사유 중 하나로 매단다. 무상 부여·프로모션이 나중에 생겨도 이용권 쪽은 그대로다.
--
-- 이용권이 "어디서 온 횟수인지"를 source + ref_no 한 쌍으로 직접 들고 있는다.
--   source='PG'      ref_no = erp_bookstore_payment.order_no  (이 DB 안)
--   source='SEODANG' ref_no = all_pass의 청구 식별자(bill_id)  (다른 시스템)
--   source='FREE'    ref_no = 부여 근거(품의번호 등) 또는 NULL
-- 결제 쪽에서 이용권을 가리키지 않고 이용권이 결제를 가리키게 한 이유는, 서당 청구 행이
-- 이 DB에 없어서 반대 방향으로는 FK든 조인이든 경로가 두 갈래로 갈리기 때문이다.
-- 대신 ref_no에는 FK를 걸 수 없다(서당분은 외부 키다). PG분은 order_no가 UNIQUE라
-- 조인 자체는 안전하고, 짝이 안 맞는 건은 정산 대조 배치로 잡는다.
--
-- [PG 결제 흐름] Flutter가 이니시스 모바일 SDK로 인증(카드 선택/본인확인)까지만 하고, 그 결과를
-- Spring으로 넘긴다. 승인 API 호출은 반드시 서버가 한다 — 앱이 보낸 "결제 성공"만 믿으면
-- 위변조된 금액으로 결제 완료 처리가 되기 때문이다.
--   1) 서버: order_no 발급 + 금액은 product에서 읽어 status=READY로 선(先)기록
--   2) 앱  : 이니시스 인증 → authToken/authUrl을 서버로 전달
--   3) 서버: 승인 요청 → 응답 금액이 1)의 amount와 같은지 검증 → 다르면 즉시 망취소 후 FAILED
--   4) 서버: status=PAID + pass 행 발급(remain_count 충전), 원문 로그 적재 후 결과 반환
--
-- [환불] 앱에서 신청하면 규정(refund_rule)이 자동 적용돼 PG 취소까지 간다. 신청과 실행이
-- 1:1이라 신청 테이블을 따로 두지 않는다. 서당 청구분은 이 시스템에서 환불하지 않는다
-- (돈을 all_pass가 받았으므로 환불도 그쪽이다). 그래서 환불 도메인은 PG 결제 전용이다.
--   ※ "직원 승인 후 환불" 같은 심사 단계가 생기면 그때는 신청 테이블을 분리해야 한다.
--     반려·재신청이 생기면서 신청 1건 : 취소 N건이 되기 때문이다.
--
-- [컬럼을 늘리지 않는 이유] 이니시스 응답 필드를 전부 컬럼으로 펼치지 않는다. 화면에 쓰지
-- 않는 값은 erp_bookstore_payment_log의 원문(res_body)에 이미 다 들어 있어서, 컬럼으로
-- 또 꺼내면 같은 데이터를 두 군데 관리하게 된다.
--
-- [스냅샷] pass/payment는 product를 FK로 물지만 상품명·금액·제공횟수를 값으로도 복사해 둔다.
-- 본사가 가격을 올리면 과거 결제내역 금액까지 같이 올라가면 안 되기 때문이다.
--
-- [erp_student.sub_book/sub_hoho] 그 BIT는 이 테이블들에서 파생되는 캐시다. 화면들이 이미
-- BIT를 보고 있어서 남기되, 진짜 정답은 항상 유효한 pass의 remain_count 합계다.
--
-- [DROP 제외] 실제 돈이 오간 기록이고 상품·규정은 본사가 화면에서 관리하는 마스터라
-- 매 기동 리셋하면 안 된다. 파일 상단 DROP 목록에 넣지 말고 IF OBJECT_ID(...) IS NULL로만
-- 생성한다(erp_center와 같은 취급). 인덱스도 같은 이유로 sys.indexes 존재 확인을 감싼다.
--
-- [FK] student_id / center_code는 다른 테이블과 같은 관례로 값으로만 연결한다.
-- 결제 도메인 안(product↔pass↔use↔payment↔cancel)에서만 FK를 거는데,
-- 이 관계의 정합성이 곧 금액 정합성이라서다.
-- ════════════════════════════════════════════════════════

-- 상품 마스터 — 서비스별 횟수권. 본사가 관리자 화면에서 가격/횟수를 고친다.
-- 서당 일괄청구분도 같은 상품을 쓴다. 청구 방법이 다를 뿐 제공하는 이용권은 같기 때문이다.
-- 가격 변경 이력은 두지 않는다. 과거 결제는 payment의 스냅샷이 지키고 있어서,
-- 여기에 이력까지 쌓으면 같은 사실이 두 군데가 된다.
IF OBJECT_ID('erp_bookstore_product', 'U') IS NULL
CREATE TABLE erp_bookstore_product (
    product_id   INT          IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    product_code VARCHAR(20)  NOT NULL UNIQUE,  -- 상품 코드 (예: BOOK_M8). 화면/설정에서 상품을 지목하는 키
    product_name VARCHAR(50)  NOT NULL,         -- 상품명 (예: 도서 클리닉 월 8회권)
    service_code VARCHAR(10)  NOT NULL,         -- BOOK(도서) / HOHO(호호). erp_student.sub_book/sub_hoho에 대응
    total_count  SMALLINT     NOT NULL,         -- 제공 횟수. pass.remain_count의 시작값이 된다
    price        INT          NOT NULL,         -- 판매가(원). 서당 일괄청구도 이 금액을 교재비에 얹는다
    is_active    BIT          NOT NULL DEFAULT 1,  -- 판매중 여부. 단종 상품도 과거 결제가 참조하므로 삭제하지 않고 내린다
    created_at   DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 환불 규정 — "결제 후 며칠 이내 / 몇 회 이하로 썼으면 몇 % 환불"을 본사가 관리한다.
-- 적용은 priority 오름차순으로 훑어 조건에 처음 맞는 한 건만 쓴다(여러 규정이 겹쳐도 결과가 하나로 정해지게).
-- 어디에도 안 걸리면 환불 불가로 본다. PG 결제분에만 적용된다.
--
-- [개정] 기존 행을 고치면 그 규정으로 환불해 준 과거 건의 근거가 사라진다.
-- 규정이 바뀌면 기존 행은 is_active=0으로 내리고 새 rule_code로 새 행을 넣는다.
IF OBJECT_ID('erp_bookstore_refund_rule', 'U') IS NULL
CREATE TABLE erp_bookstore_refund_rule (
    rule_id     INT          IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    rule_code   VARCHAR(20)  NOT NULL UNIQUE,  -- 규정 코드. payment_cancel에 스냅샷으로 남는 값
    rule_name   VARCHAR(50),                   -- 화면 표시용 설명 (예: 7일 이내 미사용 전액환불)
    max_days    SMALLINT     NOT NULL,         -- 결제일로부터 이 일수 이내일 것
    max_count   SMALLINT     NOT NULL,         -- 사용 횟수가 이 값 이하일 것
    refund_rate SMALLINT     NOT NULL,         -- 환불율(%). 결제금액 기준이며 원 단위 절사는 서버가 처리한다
    priority    SMALLINT     NOT NULL,         -- 적용 순서. 작을수록 먼저 검사한다
    is_active   BIT          NOT NULL DEFAULT 1,  -- 현행 규정 여부. 개정된 규정도 과거 근거라 삭제하지 않는다
    created_at  DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

-- 이용권 — "이 학생이 몇 회 남았나"의 단일 정답. 책방 학생(PG 결제)과 서당 학생(일괄청구)이
-- 같은 모양으로 들어오고, source로만 갈린다. 출석 차감은 이 테이블만 보면 되므로
-- 차감 로직에 결제 방식 분기가 들어가지 않는다.
IF OBJECT_ID('erp_bookstore_pass', 'U') IS NULL
CREATE TABLE erp_bookstore_pass (
    pass_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    student_id   VARCHAR(100)  NOT NULL,       -- erp_student.student_id (FK 없이 값으로만 연결)
    center_code  VARCHAR(50),                  -- 소속 센터 (정산/조회 필터용 중복 저장)
    product_id   INT           NOT NULL,       -- erp_bookstore_product.product_id
    service_code VARCHAR(10)   NOT NULL,       -- 상품의 service_code 스냅샷. "이 학생 도서 잔여횟수"가 가장 잦은
                                               -- 조회라 매번 product를 조인하지 않으려고 값으로도 복사해 둔다
    source       VARCHAR(10)   NOT NULL,       -- PG(앱 카드결제) / SEODANG(서당 일괄청구) / FREE(본사 무상부여)
    ref_no       VARCHAR(40),                  -- 이 횟수가 발생한 근거의 식별자. source에 따라 가리키는 곳이 다르다.
                                               -- PG=erp_bookstore_payment.order_no / SEODANG=all_pass 청구 bill_id.
                                               -- 외부 시스템 키가 섞여 있어 FK를 걸 수 없다
    billing_ym   CHAR(6),                      -- 이 이용권이 청구된 년월(YYYYMM). 서당 일괄청구는 전월 20일에
                                               -- 다음 달치를 걷으므로 "언제 청구된 몫인지"가 결제일과 다르다.
                                               -- all_pass 청구 내역과 대조하는 키라서 PG 건에도 같은 규칙으로 채운다
    valid_from   DATE          NOT NULL,       -- 이 이용권이 적용되는 주기의 시작일.
                                               -- 자동결제(PG)분은 결제일 그 날이고(2026-09-07 전환),
                                               -- 서당 일괄청구분은 그 달의 1일이다 — 두 체계가 공존하므로
                                               -- 주기를 billing_ym에서 역산하지 않고 이 컬럼을 단일 진실로 삼는다
    valid_until  DATE          NOT NULL,       -- 주기의 마지막 날(자동결제분은 한 달 뒤 전일, 서당분은 말일).
                                               -- 오늘이 이 범위 밖이면 remain_count가 남아 있어도 못 쓴다
                                               -- — 이월 없이 주기 종료와 함께 소멸한다는 정책
    total_count  SMALLINT      NOT NULL,       -- 지급된 총 횟수 (product.total_count 스냅샷)
    remain_count SMALLINT      NOT NULL,       -- 잔여 횟수. 출석마다 1씩 깐다.
                                               -- pass_use 건수와 total_count - remain_count가 항상 같아야 한다.
                                               -- 매번 세지 않으려고 캐시로 둔 것이라 차감 트랜잭션에서 같이 갱신한다.
                                               -- 환불되면 잔여를 회수해 0으로 만든다
    granted_at   DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 지급일시(KST)
    revoked_at   DATETIME2,                    -- 회수일시(KST). 환불·청구취소로 무효가 된 이용권.
                                               -- NULL이 아니면 remain_count가 남아 있어도 쓸 수 없다
    FOREIGN KEY (product_id) REFERENCES erp_bookstore_product(product_id)
);

-- 마이그레이션: valid_from/valid_until 도입 이전에 이미 만들어져 있는 운영 DB용.
-- 기존 행은 billing_ym(없으면 granted_at의 월)에서 그 달의 1일/말일을 역산해 채운다.
-- BEGIN/END로 묶지 않고 독립된 문장으로 나눈다 — 이 프로젝트의 SQL 스크립트 실행기는
-- 세미콜론 단위로 문장을 쪼개서 실행하는데, BEGIN/END 블록 안에 세미콜론이 여러 개 있으면
-- 블록 중간에서 잘려 "Incorrect syntax" 오류가 난다(2026-08-06 확인).
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_from')
    ALTER TABLE erp_bookstore_pass ADD valid_from DATE NULL, valid_until DATE NULL;

UPDATE erp_bookstore_pass
SET valid_from  = DATEFROMPARTS(LEFT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 4),
                                 RIGHT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 2), 1),
    valid_until = EOMONTH(DATEFROMPARTS(LEFT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 4),
                                         RIGHT(ISNULL(billing_ym, FORMAT(granted_at, 'yyyyMM')), 2), 1))
WHERE valid_from IS NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_from' AND is_nullable = 1)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_from DATE NOT NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass') AND name = 'valid_until' AND is_nullable = 1)
    ALTER TABLE erp_bookstore_pass ALTER COLUMN valid_until DATE NOT NULL;

-- 출석할 때마다 타는 경로 — 살아있는 이용권만 보면 되므로 필터드 인덱스로 좁힌다.
-- 여러 달치가 겹칠 수 있게 되면서(월 단위 유효기간 도입) 소진 순서 기준이 granted_at(먼저 산 것)에서
-- valid_until(먼저 만료되는 것)로 바뀌었다 — 인덱스도 실제 조회/정렬 조건에 맞춰 갈아탄다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_student' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    DROP INDEX IX_pass_student ON erp_bookstore_pass;
CREATE INDEX IX_pass_student ON erp_bookstore_pass (student_id, service_code, valid_until)
    WHERE revoked_at IS NULL;

-- 결제/청구 건에서 이용권을 되짚는 경로 (환불 시 회수할 이용권 찾기, 정산 대조)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_ref' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    CREATE INDEX IX_pass_ref ON erp_bookstore_pass (source, ref_no);

-- 서당 일괄청구분을 월별로 대조할 때 쓴다 (이번 달 청구한 인원 vs 실제 지급된 이용권)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_billing' AND object_id = OBJECT_ID('erp_bookstore_pass'))
    CREATE INDEX IX_pass_billing ON erp_bookstore_pass (billing_ym, source, center_code);

-- 횟수 차감 이력 — 출석(입실) 1회당 1행. 환불 규정의 "몇 회 이하로 썼는가"를 이 테이블로 센다.
--
-- [clinic_session을 세지 않는 이유] 출석 자체는 erp_bookstore_clinic_session에 남지만
-- 그 테이블은 매 기동 DROP 대상이다. 사용 횟수를 거기서 세면 리셋 한 번에 환불 금액이 틀어진다.
-- 돈에 영향을 주는 카운트는 리셋되지 않는 곳에 따로 남겨야 한다. session_id는 추적용으로만
-- 값으로 들고, 리셋되면 사라지는 값이라 FK는 걸지 않는다.
--
-- [하루 1회] 같은 날 재입실해도 차감은 1회다. 재로그인·되돌아온 학생에게 두 번 까이면 안 되므로
-- UNIQUE로 막는다. 서비스가 둘 이상으로 늘면 이 키에 service_code를 넣어야 한다.
IF OBJECT_ID('erp_bookstore_pass_use', 'U') IS NULL
CREATE TABLE erp_bookstore_pass_use (
    use_id      INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    pass_id     INT           NOT NULL,       -- erp_bookstore_pass.pass_id (어느 이용권을 깠는지)
    student_id  VARCHAR(100)  NOT NULL,       -- erp_student.student_id (조회 편의용 중복 저장)
    session_id  INT,                          -- erp_bookstore_clinic_session.session_id (값으로만 연결, 추적용)
    used_date   DATE          NOT NULL,       -- 차감일 (하루 1회 판정 기준)
    created_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UQ_pass_use_daily UNIQUE (student_id, used_date),
    FOREIGN KEY (pass_id) REFERENCES erp_bookstore_pass(pass_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_use_pass' AND object_id = OBJECT_ID('erp_bookstore_pass_use'))
    CREATE INDEX IX_pass_use_pass ON erp_bookstore_pass_use (pass_id);

-- PG 결제 — 책방만 이용하는 학생의 앱 카드결제. 서당 학생은 여기 행이 생기지 않는다.
-- 행은 결제 "시작" 시점에 status=READY로 먼저 생긴다(승인 실패/이탈 건도 남아야 정산 대조가 된다).
-- 승인 성공 시 이용권이 발급되며, 그 연결은 pass.ref_no = 이 행의 order_no로 맺는다.
IF OBJECT_ID('erp_bookstore_payment', 'U') IS NULL
CREATE TABLE erp_bookstore_payment (
    payment_id    INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    order_no      VARCHAR(40)   NOT NULL UNIQUE,  -- 가맹점 주문번호(이니시스 oid). 서버 생성, 이니시스 제한이 40자
    group_order_no VARCHAR(40),                   -- 형제 묶음결제 시 공통 그룹 주문번호. 단일결제는 항상 NULL.
                                                  -- PG에는 그룹 주문번호 1개로 결제 1건만 승인 요청하고, 이 컬럼으로
                                                  -- 같은 그룹에 속한 학생별 payment 행들을 되짚는다.
    tid           VARCHAR(40),                    -- 이니시스 거래번호. 환불 API에 넘기는 키.
                                                  -- 승인 전에는 NULL이라 NOT NULL 불가.
                                                  -- 중복 승인 차단은 아래 필터드 UNIQUE 인덱스가 담당한다
                                                  -- (컬럼에 UNIQUE를 걸면 SQL Server는 NULL을 한 행만 허용해서
                                                  --  두 번째 READY 행부터 INSERT가 깨진다)
    student_id    VARCHAR(100)  NOT NULL,         -- erp_student.student_id (FK 없이 값으로만 연결)
    center_code   VARCHAR(50),                    -- 결제한 학생의 소속 센터 (정산/조회 필터용 중복 저장)
    product_id    INT           NOT NULL,         -- erp_bookstore_product.product_id
    product_name  VARCHAR(50),                    -- 결제 시점 상품명 스냅샷
    -- 상품의 service_code 스냅샷(2026-08-07) — 아래 UX_payment_active_billing 필터드 유니크
    -- 인덱스에 쓴다. 필터드 유니크 인덱스는 조인을 못 걸어서 값으로 복사해 둬야 한다.
    service_code  VARCHAR(10),
    billing_ym    CHAR(6),                        -- 이 결제가 몇 월치 이용권인지(YYYYMM). prepare()/prepareGroup()
                                                  -- 시점에 PassService.nextBillingYm()으로 정해서 넣고, 승인 확정
                                                  -- 때 이 값을 그대로 이용권에 옮긴다(그 사이 재계산하면 화면에
                                                  -- 보여준 달과 실제 발급된 달이 어긋날 수 있다. 2026-08-06)
    amount        INT           NOT NULL,         -- 결제 금액(원) = 결제 시점 가격 스냅샷.
                                                  -- 승인 응답 금액과 이 값을 대조해 위변조를 검증한다.
                                                  -- 검증을 통과해야만 PAID가 되므로 "승인금액" 컬럼을 따로 두지 않는다
    refund_amount INT           NOT NULL DEFAULT 0,  -- 누적 환불 금액. payment_cancel의 status='DONE' 합계와 항상 같아야 한다.
                                                     -- 잔액은 amount - refund_amount로 나온다
    status        VARCHAR(10)   NOT NULL DEFAULT 'READY',
        -- READY(결제창 띄움) / PAID(승인완료) / CLOSED(승인 시도 전 이탈 — X버튼/뒤로가기/방치)
        -- / FAILED(승인실패·망취소) / CANCELED(전액취소)
        -- 부분취소는 별도 상태값을 두지 않는다. status='PAID' + refund_amount > 0 이면 부분취소이고,
        -- 상태값으로 또 들고 있으면 refund_amount와 어긋날 수 있는 지점이 하나 더 늘기 때문이다
    pay_method    VARCHAR(10),                    -- 이니시스 payMethod. 현재는 Card만 받지만 값은 응답대로 저장한다
    card_name     VARCHAR(30),                    -- 카드사명 (결제내역 화면 표시용)
    card_no       VARCHAR(20),                    -- 마스킹된 카드번호. 원본 번호는 절대 저장하지 않는다
    appl_no       VARCHAR(30),                    -- 카드 승인번호 (카드사 문의 시 필요)
    result_code   VARCHAR(10),                    -- 이니시스 resultCode ('0000'=성공). 실패 건을 걸러내는 용도.
                                                  -- 실패 사유 원문은 payment_log.res_body를 본다
    requested_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 결제 시작일시(KST)
    paid_at       DATETIME2,                      -- 승인 완료일시(KST). 환불 규정의 "며칠 이내"가 이 값 기준이다
    -- 운영자 수동 확인 필요 플래그(2026-08-07) — schema.sql과 동일. 이미 배포된 운영 DB는
    -- 이 CREATE TABLE이 안 타므로 patch-erp_bookstore_payment-needs_review.sql로 별도 추가한다.
    needs_review  BIT           NOT NULL DEFAULT 0,
    review_reason VARCHAR(200),
    reviewed_at   DATETIME2,
    -- 동시 환불 요청 경합 방지 선점 컬럼(2026-08-07) — schema.sql과 동일. 이미 배포된 운영 DB는
    -- patch-erp_bookstore_payment-refund_lock.sql로 별도 추가한다.
    refund_requested_at DATETIME2,
    -- CLOSED 사후 재확인 완료 표시(2026-08-07) — schema.sql과 동일. 이미 배포된 운영 DB는
    -- patch-erp_bookstore_payment-closed_recheck.sql로 별도 추가한다.
    closed_recheck_at DATETIME2,
    FOREIGN KEY (product_id) REFERENCES erp_bookstore_product(product_id)
);

-- 같은 학생·서비스·청구월에 진행 중(READY)이거나 완료(PAID)된 결제가 동시에 2개 이상 있지
-- 못하게 막는다(2026-08-07) — schema.sql과 동일. 이미 배포된 운영 DB는
-- patch-erp_bookstore_payment-active_billing_unique.sql로 별도 추가한다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_active_billing' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE UNIQUE INDEX UX_payment_active_billing ON erp_bookstore_payment (student_id, service_code, billing_ym)
        WHERE status IN ('READY', 'PAID');

-- (2026-08-06 폐지) tid 유니크 인덱스는 형제 묶음결제와 양립할 수 없어 제거했다 — PG 승인은
-- 그룹당 1건만 나는데, 그 tid를 학생 수만큼의 payment 행에 그대로 복사해 남기므로 같은 tid를
-- 가진 행이 여러 개 있는 게 정상이다. 이중 승인 방어는 markPaid의 WHERE status='READY'
-- 조건이 이미 담당한다(재시도하면 0행 갱신되어 자연히 막힌다) — tid 유니크는 원래도 보조 방어선일
-- 뿐이었다. 이미 만들어진 운영 DB에는 인덱스가 남아 있을 수 있어 제거 마이그레이션을 둔다.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_payment_tid' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    DROP INDEX UX_payment_tid ON erp_bookstore_payment;

-- 결제내역 조회는 "이 학생의 결제 목록을 최신순"이 대부분이라 정렬까지 인덱스에 태운다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_student' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_student ON erp_bookstore_payment (student_id, requested_at DESC);

-- 센터별 기간 정산용
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_center_paid' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_center_paid ON erp_bookstore_payment (center_code, paid_at);

-- 승인까지 못 간 READY 방치분을 배치로 정리하기 위한 인덱스
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_status' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_status ON erp_bookstore_payment (status, requested_at);

-- 형제 묶음결제 승인 시 group_order_no로 그룹 내 학생별 payment 행을 되짚기 위한 인덱스
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_group_order' AND object_id = OBJECT_ID('erp_bookstore_payment'))
    CREATE INDEX IX_payment_group_order ON erp_bookstore_payment (group_order_no) WHERE group_order_no IS NOT NULL;

-- 마이그레이션: erp_bookstore_payment가 group_order_no 도입 이전에 이미 만들어져 있는 운영 DB용.
-- 위 CREATE TABLE은 테이블이 아예 없을 때만 실행되므로, 기존 테이블에는 컬럼을 별도로 추가해야 한다.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'group_order_no')
    ALTER TABLE erp_bookstore_payment ADD group_order_no VARCHAR(40);

-- 마이그레이션: billing_ym(월 단위 유효기간) 도입 이전에 이미 만들어져 있는 운영 DB용.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_payment') AND name = 'billing_ym')
    ALTER TABLE erp_bookstore_payment ADD billing_ym CHAR(6);

-- 학생 형제(가족) 묶음 — 결제창에서 형제를 함께 보여주고 합산 결제할 때 쓴다.
-- 자동 매칭 로직 없음: 형제 등록은 사람이 직접 INSERT한다(전화번호 자동 그룹핑 같은 것 없음).
-- student_id는 erp_bookstore_payment/erp_bookstore_pass와 동일한 이유로 FK를 걸지 않는다
-- (erp_student.student_id에 UNIQUE 제약이 없어 FK 대상이 될 수 없다).
IF OBJECT_ID('erp_student_sibling', 'U') IS NULL
CREATE TABLE erp_student_sibling (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    sibling_key  VARCHAR(40)  NOT NULL,   -- 형제 그룹 키. 자동생성 없음 — 등록할 때 사람이 정한다
                                          -- (예: 대표 학생의 student_id를 그대로 키로 쓴다)
    student_id   VARCHAR(100) NOT NULL,   -- erp_student.student_id (FK 없이 값으로만 연결)
    created_at   DATETIME2    NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    CONSTRAINT UX_sibling_key_student UNIQUE (sibling_key, student_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_sibling_student' AND object_id = OBJECT_ID('erp_student_sibling'))
    CREATE INDEX IX_sibling_student ON erp_student_sibling (student_id);

-- 환불(취소) 내역 — 부분환불과 재시도가 있어 결제 1건에 N행이다. PG 결제분 전용이다.
-- 이번 취소가 부분인지 전액인지는 cancel_amount와 payment.amount - payment.refund_amount 비교로 나오므로
-- is_partial 같은 플래그를 두지 않는다.
IF OBJECT_ID('erp_bookstore_payment_cancel', 'U') IS NULL
CREATE TABLE erp_bookstore_payment_cancel (
    cancel_id     INT           IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK
    payment_id    INT           NOT NULL,         -- erp_bookstore_payment.payment_id (원거래 tid는 여기서 조인해 얻는다)
    cancel_amount INT           NOT NULL,         -- 이번에 취소한 금액 (규정 적용 결과)
    cancel_tid    VARCHAR(40),                    -- 이니시스가 돌려주는 취소 거래번호. 부분환불은 건별로 따로 나와서
                                                  -- 정산 대조 때 원거래 tid만으로는 어느 건인지 못 가린다
    reason        VARCHAR(100),                   -- 취소 사유 (이니시스 cancelmsg로 전달, 필수 파라미터라 비워둘 수 없음)
    requested_by  VARCHAR(50),                    -- 처리자 erp_user.user_code. 앱에서 학생/보호자가 신청했으면 'APP'
    -- 아래 3개는 환불 규정을 적용한 "그 시점의 근거" 스냅샷이다. 나중에 다시 계산하면 사용 횟수가
    -- 늘어 있고 규정도 개정돼 있어서 같은 답이 안 나온다. 환불 분쟁은 "왜 이 금액인가"를 증명하는
    -- 일이라 결과 금액만 남기면 방어가 안 된다.
    rule_code     VARCHAR(20),                    -- 적용한 erp_bookstore_refund_rule.rule_code.
                                                  -- 규정이 개정돼도 과거 건은 이 코드로 근거를 찾는다
    used_days     SMALLINT,                       -- paid_at부터 취소 요청일까지 경과일수
    used_count    SMALLINT,                       -- 그 시점까지의 pass_use 건수
    status        VARCHAR(10)   NOT NULL DEFAULT 'REQ',  -- REQ(요청) / DONE(취소완료) / FAIL(취소실패)
    result_code   VARCHAR(10),                    -- 이니시스 resultCode
    result_msg    VARCHAR(200),                   -- 취소 실패 사유. 앱/관리자 화면에 바로 보여줘야 해서
                                                  -- payment와 달리 컬럼으로 둔다
    requested_at  DATETIME2     NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 취소 요청일시(KST)
    canceled_at   DATETIME2,                      -- 취소 완료일시(KST). status=DONE일 때만 채워짐
    FOREIGN KEY (payment_id) REFERENCES erp_bookstore_payment(payment_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_cancel_payment' AND object_id = OBJECT_ID('erp_bookstore_payment_cancel'))
    CREATE INDEX IX_payment_cancel_payment ON erp_bookstore_payment_cancel (payment_id);

-- PG 통신 원문 로그 — 승인/취소 요청·응답 본문을 그대로 남긴다.
-- 결제 분쟁은 "우리가 뭘 보냈고 이니시스가 뭘 답했나"를 증명하는 싸움이라 파싱된 컬럼만으로는 부족하다.
-- 위 테이블들에서 컬럼을 덜어낼 수 있는 것도 원문이 여기 남기 때문이다.
-- 카드번호·인증정보가 섞여 들어오므로 저장 전에 마스킹한 뒤 넣는다.
IF OBJECT_ID('erp_bookstore_payment_log', 'U') IS NULL
CREATE TABLE erp_bookstore_payment_log (
    log_id      BIGINT         IDENTITY(1,1) PRIMARY KEY,  -- 내부 PK (결제 1건당 여러 행이라 BIGINT)
    order_no    VARCHAR(40)    NOT NULL,        -- 결제 행이 아직 없는 시점의 로그도 추적되도록 order_no로 묶는다.
                                                -- payment_id는 order_no로 조인하면 나오므로 중복해서 들지 않는다
    tid         VARCHAR(40),
    log_type    VARCHAR(20)    NOT NULL,        -- APPROVE(승인) / NET_CANCEL(망취소) / CANCEL(환불) / INQUIRY(거래조회)
                                                -- 금액 불일치로 망취소를 때렸는데 그마저 실패하면 카드사에는 승인이 남는다.
                                                -- log_type='NET_CANCEL' + 실패 건은 따로 모니터링해서 수동 취소해야 한다
    http_status SMALLINT,                       -- HTTP 응답 코드. 타임아웃 등 응답 자체가 없으면 NULL
    result_code VARCHAR(10),                    -- 이니시스 resultCode (실패 로그만 빠르게 걸러내려고 별도 컬럼)
    req_body    NVARCHAR(MAX),                  -- 요청 본문(마스킹 후)
    res_body    NVARCHAR(MAX),                  -- 응답 본문(마스킹 후)
    created_at  DATETIME2      NOT NULL DEFAULT DATEADD(HOUR, 9, GETUTCDATE())
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payment_log_order' AND object_id = OBJECT_ID('erp_bookstore_payment_log'))
    CREATE INDEX IX_payment_log_order ON erp_bookstore_payment_log (order_no, created_at);

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
