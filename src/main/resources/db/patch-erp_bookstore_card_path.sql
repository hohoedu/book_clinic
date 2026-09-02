-- ===================== 이미 운영 중인 DB에 1회 수동 실행 (DBeaver 등) =====================
-- 도서별 수집 카드 이미지 테이블 신설 (2026-09-02).
-- 신규 구축 DB는 schema.sql이 같은 정의로 자동 생성하므로 이 파일이 필요 없다.
--
-- 왜 content에 컬럼을 추가하지 않았나:
--   표지(content.image_url)와 카드는 서로 다른 이미지다. 도서 마스터는 외부에서 들어오는 데이터
--   성격이고 카드는 클리닉 고유의 리워드 개념이라, 컬럼을 늘리는 대신 테이블을 분리했다.
--   행이 없는 책 = 카드 이미지가 아직 없는 책 → 화면은 정적 리소스(/images/student_result/card.png)로 폴백한다.
--
-- 카드 "지급"은 erp_bookstore_student_card가 담당한다. 이 테이블은 "그 카드가 어떻게 생겼는지"만 갖는다.
-- 리셋 제외 대상이라 schema.sql의 DROP 블록에는 들어가지 않는다(사람이 등록한 값 보존).

IF OBJECT_ID('erp_bookstore_card_path', 'U') IS NULL
CREATE TABLE erp_bookstore_card_path (
    content_id    INT          NOT NULL PRIMARY KEY,  -- erp_bookstore_content.content_id (책 1권당 카드 1장)
    card_url      VARCHAR(500) NOT NULL,  -- 카드 이미지 URL (ImageStorageService가 반환한 가비아 호스팅 주소)
    registered_by VARCHAR(100),           -- 등록한 사용자 이름
    registered_at DATETIME2    DEFAULT DATEADD(HOUR, 9, GETUTCDATE()),  -- 등록일시(KST)
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);
