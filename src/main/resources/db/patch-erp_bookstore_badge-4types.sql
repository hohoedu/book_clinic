-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 뱃지 5종 → 4종 재편 (2026-09-02)
--
-- 구 1번 "참 잘했어요!"(기본 문제 첫 시도 불합격)와 구 2번 "독서친구"를 "독서완료"로 합친다.
-- 기본 문제는 풀기만 하면 합격/불합격(재도전) 무관하게 독서완료 뱃지다.
--
--   구 id → 신 id
--    1 참 잘했어요! ┐
--                   ├→ 1 독서완료 (이름 변경 — 새 아이콘 문구와 일치)
--    2 독서친구     ┘
--    3 독서왕        → 2 독서왕
--    4 심화 완료     → 3 심화완료 (공백 제거 — 아이콘 문구와 일치)
--    5 심화왕        → 4 심화왕
--
-- 아이콘도 /images/icons/badge_1~4.png 로 줄어든다(badge_5.png는 더 이상 참조되지 않음).
-- 코드 쪽 대응: ClinicService BADGE_* 상수, ClinicMapper의 badge_id IN (...) 범위.
-- data.sql(로컬, 매 기동 리셋)과 data-core-prod.sql(신규 설치)은 이미 4종으로 갱신돼 있으므로,
-- 이 스크립트는 "구 5종이 이미 들어 있는 운영 DB"에서만 1회 실행한다.
-- ════════════════════════════════════════════════════════════════════

SET XACT_ABORT ON;
BEGIN TRAN;

-- 0) 이미 이관된 DB면 아무것도 하지 않는다 (badge_id=5가 없으면 신 체계)
IF EXISTS (SELECT 1 FROM erp_bookstore_badge WHERE badge_id = 5)
BEGIN

    -- 1) 방어 — 같은 (student_id, content_id)에 구 1번과 구 2번이 함께 있으면 2→1 이관 시 PK 충돌.
    --    정상 데이터에선 책당 기본 뱃지가 1개뿐이라 발생하지 않지만, 있으면 구 2번 행을 버린다
    --    (어차피 둘 다 신 1번 독서친구로 합쳐진다).
    DELETE sb
    FROM erp_bookstore_student_badge sb
    WHERE sb.badge_id = 2
      AND EXISTS (
          SELECT 1 FROM erp_bookstore_student_badge dup
          WHERE dup.student_id = sb.student_id
            AND dup.content_id = sb.content_id
            AND dup.badge_id = 1
      );

    -- 2) 학생 획득 이력 재번호 — 오름차순으로 처리해야 직전 단계가 목적지 번호를 비워준다.
    --    (2→1 로 2번이 비고, 3→2 로 3번이 비고 …). 목적지 id는 마스터에 이미 존재하므로 FK 위반 없음.
    UPDATE erp_bookstore_student_badge SET badge_id = 1 WHERE badge_id = 2;
    UPDATE erp_bookstore_student_badge SET badge_id = 2 WHERE badge_id = 3;
    UPDATE erp_bookstore_student_badge SET badge_id = 3 WHERE badge_id = 4;
    UPDATE erp_bookstore_student_badge SET badge_id = 4 WHERE badge_id = 5;

    -- 3) 마스터 이름/설명/카테고리 갱신
    --    badge_name은 아이콘 이미지에 그려진 문구와 반드시 같아야 한다(결과화면이 이미지+이름을 함께 표시).
    UPDATE erp_bookstore_badge
       SET badge_name = N'독서완료', badge_desc = N'책을 읽고 문제풀이를 완료',     category = 'BASIC_PASS'
     WHERE badge_id = 1;
    UPDATE erp_bookstore_badge
       SET badge_name = N'독서왕',   badge_desc = N'책의 내용을 정확하게 이해',     category = 'BASIC_PERFECT'
     WHERE badge_id = 2;
    UPDATE erp_bookstore_badge
       SET badge_name = N'심화완료', badge_desc = N'한 단계 깊은 사고 활동에 도전', category = 'ADV_PASS'
     WHERE badge_id = 3;
    UPDATE erp_bookstore_badge
       SET badge_name = N'심화왕',   badge_desc = N'어휘력과 문해력의 실력 증가',   category = 'ADV_PERFECT'
     WHERE badge_id = 4;

    -- 4) 남은 5번 마스터 제거 (2단계에서 참조가 모두 4번으로 옮겨진 뒤라 FK 안전)
    DELETE FROM erp_bookstore_badge WHERE badge_id = 5;

END

COMMIT;

-- 확인용
-- SELECT * FROM erp_bookstore_badge ORDER BY badge_id;
-- SELECT badge_id, COUNT(*) FROM erp_bookstore_student_badge GROUP BY badge_id ORDER BY badge_id;
