-- ============================================================================
-- 260921 문제 유형 설명 — erp_bookstore_qtype_desc 신설 + gubun='T' 코드 정정
--
-- [무엇을 하나]
--  1) 학생 문제풀이 화면(student-question.js)에서 유형 이름 아래 보여주던 안내 문구를
--     프론트 하드코딩에서 DB로 옮긴다. 문구가 70자 안팎이라 erp_bookstore_code.codeNm
--     VARCHAR(20)에 들어가지 않고, 설명이 필요한 구분은 'T' 하나뿐이라 erp_bookstore_code에
--     컬럼을 붙이면 나머지 구분(C/G/S/L) 행이 전부 NULL이 된다 — 그래서 별도 테이블로 분리했다.
--  2) gubun='T' 08/09의 코드명 표기를 화면 디자인과 맞춘다.
--     운영 DB에는 08='심화 어휘', 09='심화 문법'로 들어 있는데 화면 디자인 표기는
--     '어휘심화'/'문법심화'다. 이 codeNm은 지금까지 화면에서 쓰이지 않았고(관리자 도서
--     데이터 화면의 유형 목록은 book-data.js에 하드코딩돼 있다), 서버가 qtypeNm으로
--     내려주기 시작하면서 처음 학생 화면에 노출되므로 지금 표기를 맞춰 둔다.
--     → 08 '심화 어휘' → '어휘심화', 09 '심화 문법' → '문법심화'
--     (ddl-core.sql로 새로 구축한 DB에는 08이 '문법'이고 09가 없을 수 있어 INSERT도 함께 둔다)
--
-- [안전한가] itempool에 저장된 qtype 값 자체는 건드리지 않는다. 기본(qlevel='01') 문항은
-- 01~07만 쓰고 08/09는 심화 문항만 쓰므로, 코드명을 고쳐도 기존 문항의 의미는 달라지지 않는다.
-- 실행 전 아래 확인 쿼리로 기본 문항에 08/09가 섞여 있지 않은지 한 번 보는 것을 권한다.
--
--   SELECT qlevel, qtype, COUNT(*) FROM erp_bookstore_itempool
--    WHERE qtype IN ('08','09') GROUP BY qlevel, qtype;
--
-- 여러 번 실행해도 결과가 같다(멱등).
-- ============================================================================

-- ── 1. 유형 설명 테이블 ──────────────────────────────────────────────
IF OBJECT_ID('erp_bookstore_qtype_desc', 'U') IS NULL
CREATE TABLE erp_bookstore_qtype_desc (
    qtype         VARCHAR(2)    NOT NULL PRIMARY KEY,  -- 문제 유형 코드 (erp_bookstore_code gubun='T')
    qtype_desc    VARCHAR(200)  NOT NULL,              -- 유형 안내 문구 (화면 qtypeDesc)
    use_yn        BIT           NOT NULL DEFAULT 1,    -- 사용여부
    created_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_at    DATETIME2     DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),
    updated_by    VARCHAR(50)                          -- 수정한 사람
);
GO

-- ── 2. gubun='T' 08/09 코드명 표기 정정 ──────────────────────────────
-- 08: '심화 어휘'(구축에 따라 '문법') → '어휘심화'
UPDATE erp_bookstore_code
   SET codeNm = N'어휘심화'
 WHERE gubun = 'T' AND code = '08' AND codeNm <> N'어휘심화';

-- 09: '심화 문법' → '문법심화'
UPDATE erp_bookstore_code
   SET codeNm = N'문법심화'
 WHERE gubun = 'T' AND code = '09' AND codeNm <> N'문법심화';

-- 09 행 자체가 없는 DB(ddl-core.sql 초기 구축본)를 위한 보강
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '09')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '09', N'문법심화');
GO

-- ── 3. 유형 설명 문구 ────────────────────────────────────────────────
-- 이미 있는 코드는 문구만 갱신하고, 없으면 새로 넣는다
MERGE erp_bookstore_qtype_desc AS target
USING (VALUES
    ('01', N'등장인물과 사건, 행동 등 중요한 내용을 정확하게 파악하는 능력이에요. 이야기 속 중요한 내용을 잘 떠올려 보세요!'),
    ('02', N'다양한 표현의 뜻과 쓰임을 알고, 그 속에 담긴 의미를 이해하는 능력이에요. 표현에 집중해 문제를 풀어 보세요!'),
    ('03', N'이야기의 앞뒤 내용을 연결하여 원인과 결과, 사건의 관계를 파악하는 능력이에요. 문제의 단서를 연결해서 생각해 보세요!'),
    ('04', N'인물의 행동과 선택을 살펴보고, 이야기의 주제와 의미를 생각하는 능력이에요. 왜 그런지, 무엇을 말하고 있는지 생각해 보세요!'),
    ('05', N'인물의 말과 행동을 통해 마음과 감정을 이해하는 능력이에요. 인물의 입장이 되어 마음을 헤아려 보세요.'),
    ('06', N'이야기에 나온 낱말의 뜻과 쓰임을 문맥에 맞게 이해하는 능력이에요. 앞뒤 내용을 살펴 낱말의 뜻을 생각해 보세요!'),
    ('07', N'책에서 출발해 관련된 배경지식과 새로운 정보로 지식을 넓혀 가는 능력이에요. 새로운 지식을 발견해 배움의 폭을 넓혀요!'),
    ('08', N'핵심 어휘를 한자의 뜻과 함께 풀어보며 문맥 속 의미를 이해하는 능력이에요. 풍부한 어휘력으로 이야기를 더 깊이 이해해 보세요!'),
    ('09', N'책 속 낱말과 문장을 이해하고, 국어문법과 다양한 표현을 알맞게 활용하는 능력이에요. 문제를 풀며 국어 실력을 키워 보세요!')
) AS source (qtype, qtype_desc)
   ON target.qtype = source.qtype
WHEN MATCHED AND target.qtype_desc <> source.qtype_desc THEN
    UPDATE SET qtype_desc = source.qtype_desc,
               updated_at = DATEADD(HOUR, 9, GETUTCDATE()),
               updated_by = 'patch-260921'
WHEN NOT MATCHED THEN
    INSERT (qtype, qtype_desc, use_yn, updated_by)
    VALUES (source.qtype, source.qtype_desc, 1, 'patch-260921');
GO

-- ── 확인 ────────────────────────────────────────────────────────────
-- SELECT c.code, c.codeNm, d.qtype_desc
--   FROM erp_bookstore_code c
--   LEFT JOIN erp_bookstore_qtype_desc d ON d.qtype = c.code AND d.use_yn = 1
--  WHERE c.gubun = 'T'
--  ORDER BY c.code;
