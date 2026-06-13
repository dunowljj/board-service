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
        if (!isValid(input)) {
            throw new InvalidUserContentException("email");
        }
        this.value = normalize(input);
    }

    /**
     * 형식·길이 정책의 단일 출처 (ADR-0005 §5.1). 경계 커스텀 validator(`@ValidEmail`)가 이 메서드를
     * 재사용해 normalize 순서까지 동일하게 유지 — 규칙 divergence 방지.
     */
    public static boolean isValid(String input) {
        if (input == null) {
            return false;
        }
        String canonical = normalize(input);
        return !canonical.isEmpty()
                && canonical.length() <= MAX_LENGTH
                && EMAIL_PATTERN.matcher(canonical).matches();
    }

    /** trim + locale-독립 소문자화 (canonical). isValid 통과 후 호출 → non-null 전제. */
    private static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT);
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
