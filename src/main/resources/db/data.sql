-- 테스트 계정 비밀번호 해시 생성 방법:
-- 앱 실행 후 GET /test/hash?password=admin123&salt=test_salt 호출
-- 반환된 hash 값으로 아래 password_hash를 교체하세요

INSERT INTO erp_user (center_code, role_key, user_code, user_id, user_name, password_hash, salt, type, use_yn, is_han, is_book, is_clinic)
VALUES ('CENTER01', 'ADMIN', 'USR001', 'admin', '관리자', 'REPLACE_WITH_HASH', 'test_salt', 'ADMIN', 1, 1, 1, 1);
