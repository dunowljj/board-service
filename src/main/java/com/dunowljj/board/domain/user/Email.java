package com.dunowljj.board.domain.user;

import com.dunowljj.board.common.error.InvalidUserContentException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 로그인 식별자. trim + lower-case canonical 저장 (ADR-0011 §1).
 * ASCII email format 전제 — IDN / SMTPUTF8 은 별도 ADR.
 */
public final class Email {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_LENGTH = 254;

    private final String value;

    public Email(String input) {
        if (input == null) {
            throw new InvalidUserContentException("email");
        }
        String canonical = input.trim().toLowerCase(Locale.ROOT);
        if (canonical.isEmpty() || canonical.length() > MAX_LENGTH) {
            throw new InvalidUserContentException("email");
        }
        if (!EMAIL_PATTERN.matcher(canonical).matches()) {
            throw new InvalidUserContentException("email");
        }
        this.value = canonical;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
