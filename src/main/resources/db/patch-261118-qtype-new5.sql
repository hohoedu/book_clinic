-- ════════════════════════════════════════════════════════════════════
-- 운영 patch — 문제 영역 5개 추가 (2026-11-18)
--   10 추론 / 11 공감 / 12 분석 / 13 비판 / 14 통합
-- 학년별 출제 영역은 관리자 도서 데이터 화면(book-data.js qtypeCodesForActiveLevel)이 정한다.
-- 유형 설명(qtype_desc) 문구는 임시안 — 확정 문구가 오면 UPDATE로 교체.
-- 여러 번 실행해도 안전. GO 없이 문장마다 ; — DBeaver Alt+X.
-- ════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '10')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '10', '추론');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '10')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('10', N'이야기 속 단서를 바탕으로 직접 드러나지 않은 내용이나 앞으로 일어날 일을 짐작하는 능력이에요. 단서를 모아 숨은 뜻을 찾아보세요!', 1, 'patch-261118');

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '11')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '11', '공감');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '11')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('11', N'인물의 처지와 마음을 내 일처럼 느끼고 이해하는 능력이에요. 내가 그 인물이라면 어땠을지 생각해 보세요!', 1, 'patch-261118');

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '12')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '12', '분석');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '12')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('12', N'글의 짜임과 내용을 나누어 살피며 중요한 요소들의 관계를 파악하는 능력이에요. 글을 차근차근 나누어 살펴보세요!', 1, 'patch-261118');

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '13')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '13', '비판');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '13')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('13', N'글의 내용과 인물의 생각이 타당한지 따져 보고 나의 생각을 세우는 능력이에요. 정말 그럴까? 질문하며 읽어 보세요!', 1, 'patch-261118');

IF NOT EXISTS (SELECT 1 FROM erp_bookstore_code WHERE gubun = 'T' AND code = '14')
    INSERT INTO erp_bookstore_code (gubun, code, codeNm) VALUES ('T', '14', '통합');
IF NOT EXISTS (SELECT 1 FROM erp_bookstore_qtype_desc WHERE qtype = '14')
    INSERT INTO erp_bookstore_qtype_desc (qtype, qtype_desc, use_yn, updated_by) VALUES ('14', N'글의 여러 내용과 나의 경험, 배경지식을 연결해 새로운 의미를 만드는 능력이에요. 흩어진 내용을 하나로 엮어 생각해 보세요!', 1, 'patch-261118');
