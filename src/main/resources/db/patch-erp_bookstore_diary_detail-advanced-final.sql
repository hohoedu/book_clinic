-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 심화(qlevel='02') 재도전/틀린문제 다시풀기 (2026-08-31)
--
-- 심화 문제도 기본 문제처럼 만점이 아니면 "재도전"과 "틀린 문제 다시 풀기" 기회를 준다.
--   advanced_correct_cnt        = "처음 점수" — 최초 제출값 고정 (기존)
--   advanced_final_correct_cnt  = "최종 점수" — 재도전(mode=RETRY)에서 더 잘하면 max로 갱신 (신설)
--                                 첫 제출 시 advanced_correct_cnt와 동일
-- 뱃지(심화완료 4 / 심화왕 5)는 재도전으로 "올라가기만" 한다(4→5, 불합격→4/5). 내려가지 않음.
-- "틀린 문제만 다시 풀기"(mode=WRONG_ONLY)는 점수·뱃지 어떤 것도 바꾸지 않는다.
--
-- 이미 테이블이 만들어진 DB(로컬/운영)에서 한 번 실행한다.
-- schema.sql / ddl-core.sql은 CREATE TABLE 시점에 컬럼이 포함되도록 함께 수정돼 있어
-- 새로 만드는 DB에는 이 스크립트가 필요 없다.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('erp_bookstore_diary_detail') AND name = 'advanced_final_correct_cnt'
)
    ALTER TABLE erp_bookstore_diary_detail ADD advanced_final_correct_cnt INT;

-- 기존 심화 기록 보정 — 최종 점수를 처음 점수와 동일하게 채운다
EXEC('UPDATE erp_bookstore_diary_detail
      SET advanced_final_correct_cnt = advanced_correct_cnt
      WHERE advanced_final_correct_cnt IS NULL AND advanced_correct_cnt IS NOT NULL');
