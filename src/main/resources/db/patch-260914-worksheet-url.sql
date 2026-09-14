-- ===================== 이미 운영 중인 DB에 1회 수동 실행 (DBeaver 등) =====================
-- 도서별 워크시트(출력용) 이미지 컬럼 추가 (2026-09-14).
-- 신규 구축 DB는 schema.sql이 같은 정의로 자동 생성하므로 이 파일이 필요 없다.
--
-- 왜 새 테이블이 아니라 card_path에 컬럼을 붙였나:
--   erp_bookstore_card_path는 이미 content_id가 PK인 "책 1권당 1행" 테이블이다. 워크시트도
--   책 1권당 1장이라 키 구조가 완전히 같아서, 테이블을 하나 더 만들면 같은 조인을 두 번 하게 된다.
--   이 테이블의 의미는 이제 "수집 카드 경로"가 아니라 "도서 부가 이미지 경로"다.
--
-- card_url을 NULL 허용으로 바꾸는 이유:
--   워크시트만 등록하고 카드는 아직 없는 책이 생긴다. NOT NULL이면 그런 행을 만들 수 없다.
--   "카드 이미지 없음"의 표현이 '행 없음'에서 'card_url IS NULL'로 넓어질 뿐, 화면 폴백
--   (/images/student_result/card.png) 동작은 그대로다 — 조회 쿼리가 전부 ISNULL/COALESCE 처리다.

IF COL_LENGTH('erp_bookstore_card_path', 'worksheet_url') IS NULL
    ALTER TABLE erp_bookstore_card_path
        ADD worksheet_url VARCHAR(500) NULL;  -- 워크시트(출력용) 이미지 URL, 가비아 호스팅 주소
GO

-- card_url NOT NULL → NULL 허용
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('erp_bookstore_card_path')
             AND name = 'card_url' AND is_nullable = 0)
    ALTER TABLE erp_bookstore_card_path
        ALTER COLUMN card_url VARCHAR(500) NULL;
GO
