/*
  [패치] 스페셜 카드 card_type 'RARE' → 'SPECIAL' (2026-10-01)

  화면 명칭을 "스페셜 카드"로 통일하면서 DB 값도 맞춘다. 같은 커밋의 코드(ClinicMapper/MonitorMapper)는
  'SPECIAL'만 읽고 쓰므로, 배포와 함께(가급적 배포 직전) 실행해야 한다. 이 패치 전에 새 코드가 뜨면
  기존 RARE 카드가 컬렉션/결과 화면에서 일반 카드처럼 보이고, 반대로 패치 후 옛 코드가 돌면 새 지급분이
  RARE로 다시 쌓인다(재실행하면 다시 SPECIAL로 맞춰진다).

  - 'RARE' 조건이 걸린 필터드 유니크 인덱스(UX_..._rare)를 지우고 'SPECIAL' 기준(UX_..._special)으로 다시 만든다.
  - 재실행 안전: 인덱스는 존재 여부를 보고, UPDATE는 남은 RARE만 바꾼다.
  - card_type은 VARCHAR(10)이라 'SPECIAL'(7자)이 그대로 들어간다.
*/
BEGIN TRANSACTION;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_rare' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    DROP INDEX UX_erp_bookstore_student_card_rare ON erp_bookstore_student_card;

UPDATE erp_bookstore_student_card
SET card_type = 'SPECIAL'
WHERE card_type = 'RARE';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_erp_bookstore_student_card_special' AND object_id = OBJECT_ID('erp_bookstore_student_card'))
    CREATE UNIQUE INDEX UX_erp_bookstore_student_card_special
        ON erp_bookstore_student_card (student_id, trigger_count)
        WHERE card_type = 'SPECIAL';

COMMIT;

-- 확인: RARE 0건이어야 한다
SELECT card_type, COUNT(*) AS cnt FROM erp_bookstore_student_card GROUP BY card_type;
