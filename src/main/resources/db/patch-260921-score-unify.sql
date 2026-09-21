-- ============================================================================
-- 260921 "처음 점수 / 최종 점수" 통합 — 지난 점수를 화면에서 없앤다
--
-- 2026-08-28에 재도전을 대비해 점수를 correct_count(처음)와 final_correct_count(최종)로
-- 나눠 보관하기 시작했고, 모니터링·독서일지·앱이 "3/12 → 9/12"처럼 둘을 같이 보여줬다.
-- 2026-09-21 정책 변경으로 재제출(재도전 / "틀린 문제 다시 풀기" 1회차) 결과가 곧 그 학생의
-- 점수가 됐다 — 지난 점수는 어디에도 노출되면 안 된다.
--
-- 앞으로의 제출은 ClinicMapper.updateRetryResult / MonitorMapper.upsertDiaryDetail 가 두 컬럼을
-- 같은 값으로 맞춰 쓴다. 컬럼을 지우지 않고 같은 값으로 유지하는 이유는 "처음 != 최종일 때만
-- 화살표"로 그리는 화면(monitor-live.js scoreHtml, diary.js quizHtml)이 그대로 단일 표시가 되고,
-- 앱이 이미 읽고 있는 필드(firstBasicCorrect)도 깨지지 않기 때문이다.
--
-- 이 패치는 그 규칙을 **기존 데이터에 소급 적용**한다. 실행하지 않으면 이 패치 이전에 재도전한
-- 기록만 예전처럼 화살표가 그려진다. 스키마 변경은 없고 값만 맞춘다.
-- 되돌릴 수 없다(지난 점수는 이 두 컬럼에만 있었다) — 운영 DB는 백업 후 실행할 것.
-- ============================================================================

-- ── 1. 기본 문제 — 처음 점수를 최종 점수에 맞춘다 ──────────────────────────────
-- final_correct_count가 NULL인 행(이 컬럼이 생기기 전 기록)은 건드리지 않는다.
UPDATE erp_bookstore_recommend_log
SET correct_count = final_correct_count
WHERE final_correct_count IS NOT NULL
  AND (correct_count IS NULL OR correct_count <> final_correct_count);

-- ── 2. 독서일지 스냅샷(기본) — recommend_log의 점수로 맞춘다 ──────────────────
UPDATE dd
SET dd.basic_correct_cnt = rl.final_correct_count
FROM erp_bookstore_diary_detail dd
JOIN erp_bookstore_recommend_log rl ON rl.recommend_id = dd.recommend_id
WHERE rl.final_correct_count IS NOT NULL
  AND (dd.basic_correct_cnt IS NULL OR dd.basic_correct_cnt <> rl.final_correct_count);

-- ── 3. 독서일지 스냅샷(심화) — 처음 점수를 최종 점수에 맞춘다 ──────────────────
UPDATE erp_bookstore_diary_detail
SET advanced_correct_cnt = advanced_final_correct_cnt
WHERE advanced_final_correct_cnt IS NOT NULL
  AND (advanced_correct_cnt IS NULL OR advanced_correct_cnt <> advanced_final_correct_cnt);

-- ── 확인용 — 세 쿼리 모두 0건이어야 한다 ──────────────────────────────────────
-- SELECT COUNT(*) FROM erp_bookstore_recommend_log
--  WHERE final_correct_count IS NOT NULL AND correct_count <> final_correct_count;
-- SELECT COUNT(*) FROM erp_bookstore_diary_detail dd
--   JOIN erp_bookstore_recommend_log rl ON rl.recommend_id = dd.recommend_id
--  WHERE rl.final_correct_count IS NOT NULL AND dd.basic_correct_cnt <> rl.final_correct_count;
-- SELECT COUNT(*) FROM erp_bookstore_diary_detail
--  WHERE advanced_final_correct_cnt IS NOT NULL AND advanced_correct_cnt <> advanced_final_correct_cnt;
