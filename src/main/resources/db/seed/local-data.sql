-- admin User seed. password = 'admin123' (BCrypt strength 10, PLAN-0011 §9).
-- canonical = NFKC(trim) + lowerCase. Korean syllables 의 NFKC 는 NFC 와 동일.
INSERT INTO users (email, nickname, nickname_canonical, password_hash, created_at, updated_at)
VALUES ('admin@example.com', '관리자', '관리자',
        '$2a$10$sxwPatlB5cXx6LziJnaO5.VG4R2mxZljrUQd3p3fr9BQPCLdUKglG',
        NOW(), NOW());

-- Post seed — author_id 는 admin user 의 id (email 기준 sub-query).
INSERT INTO posts (title, body, author_id, created_at, updated_at)
SELECT '환영합니다', '첫 번째 게시글입니다.', id, NOW(), NOW()
FROM users WHERE email = 'admin@example.com';

INSERT INTO posts (title, body, author_id, created_at, updated_at)
SELECT '두 번째 글', '내용 예시입니다.', id, NOW(), NOW()
FROM users WHERE email = 'admin@example.com';
