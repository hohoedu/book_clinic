-- ════════════════════════════════════════════════════════════════════
-- 결제 도메인 초기화 (개발 DB 전용) — 2026-08-04
--
-- ⚠️ 운영 DB에서 실행하지 말 것. 결제·이용권 데이터가 전부 삭제된다.
--
-- [왜 필요한가] 결제 테이블은 전부 IF OBJECT_ID(...) IS NULL로만 생성된다(실제 돈이 오간
-- 기록이라 매 기동 리셋하면 안 되기 때문). 그래서 설계가 바뀌어도 이미 만들어진 테이블은
-- 그대로 남고 새 컬럼이 반영되지 않는다. 2026-07-31 버전 테이블이 남아 있으면
-- "Invalid column name 'product_id'" 같은 오류가 난다.
--
-- [사용법] 개발 DB에서 이 파일을 실행한 뒤 앱을 재기동하면 schema.sql이 새 정의로 다시 만든다.
-- 그 다음 data-payment.sql로 상품·환불규정을 넣는다.
--
-- [삭제 순서] FK 역순이다. 순서를 바꾸면 참조 제약에 걸려 실패한다.
-- ════════════════════════════════════════════════════════════════════

-- ── 먼저 확인할 것 ──────────────────────────────────────────────────
-- [2026-08-04 확인 완료] DB에 있던 erp_bookstore_product는 컬럼 구성이
-- product_id / product_code / product_name / price / period_days / use_yn /
-- created_at / updated_at 로, 기간제(period_days) 상품 마스터였다. 우리가 쓰는
-- 횟수권(total_count / service_code)과 맞지 않고 쓰지 않기로 해서 함께 삭제한다.
--
-- 나중에 all_pass가 같은 이름으로 다시 만들면 또 충돌한다. 그때는 이 파일이 아니라
-- 우리 테이블 이름을 erp_bookstore_pass_product 등으로 바꾸는 쪽이 맞다 —
-- 남의 테이블을 지우는 스크립트를 유지하면 언젠가 남의 데이터를 지우게 된다.

DROP TABLE IF EXISTS erp_bookstore_payment_cancel;
DROP TABLE IF EXISTS erp_bookstore_payment_log;
DROP TABLE IF EXISTS erp_bookstore_payment;
DROP TABLE IF EXISTS erp_bookstore_pass_use;
DROP TABLE IF EXISTS erp_bookstore_pass;
DROP TABLE IF EXISTS erp_bookstore_refund_rule;

-- 기존 erp_bookstore_product(period_days/use_yn 구조)는 쓰지 않기로 확인됨(2026-08-04). 함께 삭제한다.
DROP TABLE IF EXISTS erp_bookstore_product;
