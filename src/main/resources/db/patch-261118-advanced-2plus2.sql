-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 심화문제 4문제(어휘2 + 문법2)로 정리 (2026-11-18)
--
-- 책마다 심화(qlevel='02') 활성 문항 중
--   어휘(qtype 08, 예전 06) 앞 2개 → qnum 01, 02
--   문법(qtype 09)          앞 2개 → qnum 03, 04
-- 로 번호를 다시 매기고, 나머지는 비활성(state='N') + qnum 앞에 '1'을 붙여(05→105) 01~04 자리를 비운다.
-- 삭제는 하지 않는다. 바뀌는 행은 전부 itempool_del에 UPDATE 스냅샷으로 남는다.
-- 학생 풀이 기록(quiz_answer_log)의 qnum도 같은 규칙으로 옮겨서 "틀린 문제 확인"이 같은 문제를 가리키게 한다.
-- 유형이 비어 있거나 08/06/09가 아닌 심화 문항은 남길 대상이 아니라 비활성된다.
--
-- 여러 번 실행해도 안전(이미 정리된 책은 그대로). GO 없이 문장마다 ; — DBeaver Alt+X.
-- ════════════════════════════════════════════════════════════════════

-- ① 실행 전 확인 — 학년별 활성 심화 문항 수
SELECT c.schoolyear, COUNT(DISTINCT q.content_id) AS books, COUNT(*) AS questions
FROM erp_bookstore_itempool q
JOIN erp_bookstore_content c ON c.content_id = q.content_id
WHERE q.qlevel = '02' AND q.state = 'S'
GROUP BY c.schoolyear ORDER BY c.schoolyear;

-- ② 번호 매핑 작업 테이블
IF OBJECT_ID('tmp_adv_qnum_map', 'U') IS NOT NULL DROP TABLE tmp_adv_qnum_map;
CREATE TABLE tmp_adv_qnum_map (
    content_id INT         NOT NULL,
    old_qnum   VARCHAR(20) NOT NULL,
    new_qnum   VARCHAR(20) NOT NULL,
    keep_yn    BIT         NOT NULL
);

-- 활성 문항 전부 + (01~04 자리를 막고 있는) 비활성 문항이 대상
INSERT INTO tmp_adv_qnum_map (content_id, old_qnum, new_qnum, keep_yn)
SELECT content_id, qnum,
       CASE WHEN keep_yn = 1 THEN CASE grp WHEN 'V' THEN '0' + CAST(rn AS VARCHAR) ELSE '0' + CAST(rn + 2 AS VARCHAR) END
            ELSE '1' + qnum END,
       keep_yn
FROM (
    SELECT content_id, qnum, grp,
           ROW_NUMBER() OVER (PARTITION BY content_id, grp, CASE WHEN state = 'S' THEN 0 ELSE 1 END
                              ORDER BY TRY_CAST(qnum AS INT), qnum) AS rn,
           CASE WHEN state = 'S' AND grp IN ('V', 'G')
                 AND ROW_NUMBER() OVER (PARTITION BY content_id, grp, CASE WHEN state = 'S' THEN 0 ELSE 1 END
                                        ORDER BY TRY_CAST(qnum AS INT), qnum) <= 2
                THEN 1 ELSE 0 END AS keep_yn
    FROM (
        SELECT content_id, qnum, state,
               CASE WHEN qtype IN ('08', '06') THEN 'V' WHEN qtype = '09' THEN 'G' ELSE 'Z' END AS grp
        FROM erp_bookstore_itempool
        WHERE qlevel = '02' AND (state = 'S' OR qnum IN ('01', '02', '03', '04'))
    ) a
) b;

-- 그대로인 행(번호도 같고 계속 활성)은 작업 대상에서 뺀다
DELETE FROM tmp_adv_qnum_map WHERE keep_yn = 1 AND old_qnum = new_qnum;

-- ③ 미리보기 — 바뀔 행 (필요하면 여기서 멈추고 확인)
SELECT m.*, q.qtype, q.state
FROM tmp_adv_qnum_map m
JOIN erp_bookstore_itempool q ON q.content_id = m.content_id AND q.qlevel = '02' AND q.qnum = m.old_qnum
ORDER BY m.content_id, m.old_qnum;

-- ④ 변경 전 스냅샷
INSERT INTO erp_bookstore_itempool_del (log_type, logged_by, content_id, qlevel, qnum, q, qex, e1, e2, e3, e4, ans, qtype, qexgb, state)
SELECT 'UPDATE', 'patch-261118-adv', q.content_id, q.qlevel, q.qnum, q.q, q.qex, q.e1, q.e2, q.e3, q.e4, q.ans, q.qtype, q.qexgb, q.state
FROM erp_bookstore_itempool q
JOIN tmp_adv_qnum_map m ON m.content_id = q.content_id AND m.old_qnum = q.qnum
WHERE q.qlevel = '02';

-- ⑤ 번호 변경 — PK 충돌을 피하려고 임시 번호('T'+원번호)를 거쳐 두 번에 옮긴다
UPDATE q SET q.qnum = 'T' + q.qnum
FROM erp_bookstore_itempool q
JOIN tmp_adv_qnum_map m ON m.content_id = q.content_id AND m.old_qnum = q.qnum
WHERE q.qlevel = '02';

UPDATE q SET q.qnum = m.new_qnum,
             q.state = CASE WHEN m.keep_yn = 1 THEN q.state ELSE 'N' END
FROM erp_bookstore_itempool q
JOIN tmp_adv_qnum_map m ON m.content_id = q.content_id AND q.qnum = 'T' + m.old_qnum
WHERE q.qlevel = '02';

-- ⑥ 학생 풀이 기록 번호도 같이 옮긴다 (한 문장이라 변경 전 값 기준으로 매칭됨)
UPDATE l SET l.qnum = m.new_qnum
FROM erp_bookstore_quiz_answer_log l
JOIN tmp_adv_qnum_map m ON m.content_id = l.content_id AND m.old_qnum = l.qnum
WHERE l.qlevel = '02';

-- ⑦ 작업 테이블 정리
DROP TABLE tmp_adv_qnum_map;

-- ⑧ 실행 후 확인 — 책별 활성 심화 문항이 4개(01~04)가 아닌 책 (문항이 원래 모자랐던 책만 나와야 정상)
SELECT q.content_id, c.original_title, COUNT(*) AS active_cnt,
       STUFF((SELECT ',' + q2.qnum FROM erp_bookstore_itempool q2
              WHERE q2.content_id = q.content_id AND q2.qlevel = '02' AND q2.state = 'S'
              ORDER BY q2.qnum FOR XML PATH('')), 1, 1, '') AS qnums
FROM erp_bookstore_itempool q
JOIN erp_bookstore_content c ON c.content_id = q.content_id
WHERE q.qlevel = '02' AND q.state = 'S'
GROUP BY q.content_id, c.original_title
HAVING COUNT(*) <> 4
ORDER BY q.content_id;
