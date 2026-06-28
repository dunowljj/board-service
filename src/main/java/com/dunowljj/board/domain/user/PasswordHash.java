package com.dunowljj.board.domain.user;

import java.util.Objects;

/**
 * 해시 결과만 보유. 평문 password 는 도메인에 흐르지 않는다 (ADR-0011 §1, §5).
 */
public final class PasswordHash {

    private final String value;

    public PasswordHash(String value) {
        // 내부 불변식 (ADR-0005 §5.1): PasswordHash 는 hasher 산출물 / DB 복원값만 받고 평문(사용자
        // 입력)을 접촉하지 않는다. BCrypt 는 non-blank 를 반환하므로 blank 면 hasher 오설정 / DB 데이터
        // 손상 같은 서버·데이터 조건이다(DB NOT NULL 은 null 만 막고 "" 는 못 막음). 따라서
        // BusinessException(4xx) 아님 → plain 예외 → web adapter 5xx fallback.
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("password hash must not be blank");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordHash that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
