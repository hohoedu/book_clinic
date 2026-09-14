-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 이용권 차감 시점을 "입실"에서 "예약"으로 (2026-09-14)
--
-- 정책 변경
--   1) 예약을 잡는 순간 이용권 1회가 차감된다(erp_bookstore_pass_use 1행 + remain_count -1).
--   2) 그 예약이 취소되면 차감을 되돌린다(canceled_at 기록 + remain_count +1).
--   3) 학생/학부모의 취소·변경은 회차 시작 24시간 전까지만 가능하다(응용 계층).
--   4) 센터 직원은 웹에서 당일에도 취소·변경할 수 있다(응용 계층).
--
-- 그래서 차감 이력이 "어느 예약으로 깠는지"와 "되돌려졌는지"를 알아야 한다.
-- 행을 지우지 않고 canceled_at으로 남기는 이유는 잔여 횟수 분쟁의 근거를 남기기 위해서다.
-- 대신 모든 집계는 canceled_at IS NULL만 센다 — "살아있는 행 수 = total_count − remain_count"
-- 불변식이 유지돼야 환불 계산(PaymentService.usedCount ↔ refund_rule.max_count)이 맞는다.
--
-- 기존 행(입실 차감분)은 reservation_id NULL로 남는다. 복구 조회가 reservation_id로만
-- 걸리므로 옛 행이 섞여 있어도 무해하고, 환불 집계에도 그대로 잡힌다(실제로 쓴 횟수라서).
--
-- schema.sql / ddl-payment.sql 은 이 패치와 함께 최신 구조로 수정했다.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass_use') AND name = 'reservation_id')
    ALTER TABLE erp_bookstore_pass_use ADD reservation_id INT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('erp_bookstore_pass_use') AND name = 'canceled_at')
    ALTER TABLE erp_bookstore_pass_use ADD canceled_at DATETIME2 NULL;
GO

-- 예약 취소 시 복구 대상(그 예약으로 깐 살아있는 차감)을 찾는 경로
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pass_use_reservation' AND object_id = OBJECT_ID('erp_bookstore_pass_use'))
    CREATE INDEX IX_pass_use_reservation ON erp_bookstore_pass_use (reservation_id)
        WHERE reservation_id IS NOT NULL;
GO
