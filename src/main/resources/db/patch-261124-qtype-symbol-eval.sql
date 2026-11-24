-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 문제 영역 2개 추가 (2026-11-24)
--   15 상징 / 16 평가  (10~14 추론·공감·분석·비판·통합은 patch-261118-qtype-new5.sql)
-- 학년별 출제 영역 배치는 관리자 도서 데이터 화면(book-data.js BASIC_QTYPES_BY_GRADE)이 정한다.
-- 유형 설명(qtype_desc) 문구는 임시안 — 확정 문구가 오면 UPDATE로 교체.
-- 여러 번 실행해도 안전. GO 없이 문장마다 ; — DBeaver Alt+X.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '15')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '15', '상징');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '15')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('15', N'이야기 속 사물이나 장면이 겉뜻 너머에 담고 있는 숨은 의미를 찾아내는 능력이에요. 무엇을 나타내는지 곰곰이 생각해 보세요!', 1, 'patch-261124');

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '16')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '16', '평가');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '16')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('16', N'인물의 행동과 글의 내용을 기준에 따라 따져 보고 알맞은지 판단하는 능력이에요. 나라면 어떻게 평가할지 생각해 보세요!', 1, 'patch-261124');
