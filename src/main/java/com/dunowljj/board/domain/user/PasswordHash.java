package com.dunowljj.board.domain.user;

import com.dunowljj.board.common.error.InvalidUserContentException;

import java.util.Objects;

/**
 * 해시 결과만 보유. 평문 password 는 도메인에 흐르지 않는다 (ADR-0011 §1, §5).
 */
public final class PasswordHash {

    private final String value;

    public PasswordHash(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidUserContentException("passwordHash");
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
