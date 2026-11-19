-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 센터별 띠지 번호 (2026-11-18)
--
-- ① erp_bookstore_content.content_num = 본사 기본 띠지 번호 (운영 DB엔 이미 있음 — 없을 때만 추가)
-- ② erp_bookstore_center_book_no — 센터가 본사 번호와 다르게 쓰는 책만 저장
-- ③ 확인용 조회 — 번호 없는 도서 / 같은 학년 안에서 겹치는 본사 번호 (저장 시 중복 검사에 걸리므로 미리 정리)
--
-- 여러 번 실행해도 안전하다. GO 없이 문장마다 ; — DBeaver에서 Alt+X로 실행.
-- ════════════════════════════════════════════════════════════════════

-- ①
IF COL_LENGTH('erp_bookstore_content', 'content_num') IS NULL
    ALTER TABLE erp_bookstore_content ADD content_num INT NULL;

-- ②
IF OBJECT_ID('erp_bookstore_center_book_no', 'U') IS NULL
CREATE TABLE erp_bookstore_center_book_no (
    center_code VARCHAR(20) NOT NULL,
    content_id  INT         NOT NULL,
    book_no     INT         NOT NULL,
    PRIMARY KEY (center_code, content_id),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

-- ③-1 번호가 없는 사용중 도서
SELECT schoolyear, content_id, original_title
FROM erp_bookstore_content
WHERE content_num IS NULL AND ISNULL(state, 'Y') = 'Y'
ORDER BY schoolyear, content_id;

-- ③-2 같은 학년 안에서 겹치는 본사 번호 (사용중 도서 기준)
SELECT schoolyear, content_num, COUNT(*) AS cnt
FROM erp_bookstore_content
WHERE content_num IS NOT NULL AND ISNULL(state, 'Y') = 'Y'
GROUP BY schoolyear, content_num
HAVING COUNT(*) > 1
ORDER BY schoolyear, content_num;
