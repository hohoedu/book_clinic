-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 뱃지 4종 → 5종 복원 (2026-09-14)
--
-- 2026-09-02에 "참 잘했어요!"(기본 불합격)와 "독서친구"(기본 합격)를 "독서완료" 1개로 합쳤던 것을
-- 되돌린다. 기본 문제 결과를 다시 불합격/합격/만점 3단계로 나누고, 심화는 그대로 2단계 유지한다.
-- 이번에 아이콘 아트도 새로 받아 이름이 예전과 다르다(문구는 아이콘에 그려진 것과 반드시 일치해야 함).
--
--   구 id → 신 id                              신 이름
--    1 독서완료(합격/불합격 공통) ┬→ 1 (불합격만)   완독
--                                └→ 2 (합격만)     정독 완료
--    2 독서왕                     → 3              정독왕
--    3 심화완료                   → 4              문해력 챌린저
--    4 심화왕                     → 5              문해력 챔피언
--
-- 구 1번은 합격/불합격을 합친 뱃지라, 어느 쪽이었는지는 erp_bookstore_recommend_log.grade로 되짚는다
-- (grade가 FRIEND/KING이면 합격 → 신 2번, 그 외(RETRY/NULL)면 불합격 → 신 1번 그대로 둔다).
--
-- 아이콘은 /images/icons/badge_1~5.png. 코드 쪽 대응: ClinicService BADGE_* 상수,
-- ClinicMapper의 badge_id IN (...) 범위. data.sql·data-core-prod.sql·schema.sql·ddl-core.sql은
-- 이미 5종으로 갱신돼 있으므로, 이 스크립트는 "구 4종이 이미 들어 있는 운영 DB"에서만 1회 실행한다.
-- ════════════════════════════════════════════════════════════════════

SET XACT_ABORT ON;
BEGIN TRAN;

-- 0) 이미 이관된 DB면 아무것도 하지 않는다 (badge_id=5가 이미 있으면 신 체계)
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_badge WHERE badge_id = 5)
BEGIN

    -- 1) 이번에 새로 생기는 마스터 2/5번을 먼저 넣는다 — 아래 학생 이력 재번호가
    --    FK(erp_bookstore_student_badge.badge_id → erp_bookstore_badge.badge_id) 위반 없이 들어가려면
    --    목적지 번호가 마스터에 먼저 있어야 한다.
    IF NOT EXISTS (SELECT 1 FROM erp_bookstore_badge WHERE badge_id = 2)
        INSERT INTO erp_bookstore_badge (badge_id, badge_name, badge_desc, category, threshold, param)
        VALUES (2, N'정독 완료', N'책을 읽고 문제를 합격선 이상 해결', 'BASIC_PASS', 1, NULL);

    IF NOT EXISTS (SELECT 1 FROM erp_bookstore_badge WHERE badge_id = 5)
        INSERT INTO erp_bookstore_badge (badge_id, badge_name, badge_desc, category, threshold, param)
        VALUES (5, N'문해력 챔피언', N'어휘력과 문해력의 실력 증가', 'ADV_PERFECT', 1, NULL);

    -- 2) 학생 획득 이력 재번호 — 내림차순으로 처리해야 직전 단계가 목적지 번호를 비워준다.
    --    (4→5로 4번이 비고, 3→4로 3번이 비고, 그 뒤 2→3으로 2번이 완전히 빈다.)
    UPDATE erp_bookstore_student_badge SET badge_id = 5 WHERE badge_id = 4;
    UPDATE erp_bookstore_student_badge SET badge_id = 4 WHERE badge_id = 3;
    UPDATE erp_bookstore_student_badge SET badge_id = 3 WHERE badge_id = 2;

    -- 3) 구 1번(독서완료, 합격/불합격 공통)을 결과에 따라 1(불합격 그대로)/2(합격)로 가른다.
    --    이 시점엔 2번 슬롯이 비어 있으므로(위 2번에서 3번으로 옮겨감) PK 충돌 걱정 없이 옮길 수 있다.
    UPDATE sb
       SET sb.badge_id = 2
      FROM erp_bookstore_student_badge sb
      JOIN erp_bookstore_recommend_log rl
        ON rl.student_id = sb.student_id AND rl.content_id = sb.content_id
     WHERE sb.badge_id = 1
       AND rl.grade IN ('FRIEND', 'KING');

    -- 4) 마스터 이름/설명/카테고리 갱신 (badge_name은 아이콘 이미지 문구와 반드시 일치)
    UPDATE erp_bookstore_badge
       SET badge_name = N'완독', badge_desc = N'책을 끝까지 읽고 문제풀이를 완료', category = 'BASIC_FAIL'
     WHERE badge_id = 1;
    UPDATE erp_bookstore_badge
       SET badge_name = N'정독왕', badge_desc = N'책의 내용을 정확하게 이해', category = 'BASIC_PERFECT'
     WHERE badge_id = 3;
    UPDATE erp_bookstore_badge
       SET badge_name = N'문해력 챌린저', badge_desc = N'한 단계 깊은 사고 활동에 도전', category = 'ADV_PASS'
     WHERE badge_id = 4;

END

COMMIT;

-- 확인용
-- SELECT * FROM erp_bookstore_badge ORDER BY badge_id;
-- SELECT badge_id, COUNT(*) FROM erp_bookstore_student_badge GROUP BY badge_id ORDER BY badge_id;
