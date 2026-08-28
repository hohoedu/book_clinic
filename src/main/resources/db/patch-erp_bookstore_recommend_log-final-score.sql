-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 문제풀이 결과 프로세스 재확정 + 모니터링 상태 6종 (2026-08-28)
--
-- 1) 첫 제출이면 재도전/통과/만점 무관하게 status='DONE'. 불합격이어도 다음 입실 시 다음 책.
--    재도전은 결과화면/완료화면 버튼에서만 이어서 하고 상태는 계속 DONE.
-- 2) 점수 3종 분리:
--      correct_count       = "처음 점수" — 최초 제출값에서 고정
--      final_correct_count = "최종 점수" — 재도전(mode=RETRY)마다 갱신되는 최신값
--      (심화 점수는 diary_detail.advanced_correct_cnt에 최초 1회만 저장 — 코드에서 가드)
--    "틀린 문제 다시 풀기"(mode=WRONG_ONLY)는 어떤 점수도 바꾸지 않는다.
-- 3) grade(KING/FRIEND/null) + 뱃지는 재도전으로 "올라가기만" 한다(null→FRIEND→KING).
--    독서친구→재도전 불합격처럼 내려가는 방향은 반영 안 함. 등급이 오르면 기본 뱃지(1~3)도 상위로 교체.
-- 4) 모니터링 "문제 푸는 중" 회차/심화 구분을 위해 clinic_session.quiz_qlevel 신설.
--
-- ※ GO 배치 구분자를 쓰지 않는다(JDBC 단일 배치 실행 대비) — 새 컬럼을 참조하는 UPDATE는
--   EXEC()로 감싸 컴파일을 실행 시점으로 미룬다.
-- schema.sql / ddl-core.sql 은 이 패치와 함께 최신 구조로 수정했다.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('erp_bookstore_recommend_log') AND name = 'final_correct_count'
)
    ALTER TABLE erp_bookstore_recommend_log ADD final_correct_count INT;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('erp_bookstore_clinic_session') AND name = 'quiz_qlevel'
)
    ALTER TABLE erp_bookstore_clinic_session ADD quiz_qlevel VARCHAR(2);

-- 기존 행 보정 — 이미 제출 기록이 있으면 최종 점수를 처음 점수와 동일하게 채운다
EXEC('UPDATE erp_bookstore_recommend_log
      SET final_correct_count = correct_count
      WHERE final_correct_count IS NULL AND correct_count IS NOT NULL');
