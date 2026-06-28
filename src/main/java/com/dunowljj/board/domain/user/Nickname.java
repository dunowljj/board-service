package com.dunowljj.board.domain.user;

import com.dunowljj.board.common.error.InvalidUserContentException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 표시 식별자. display (trim + NFC) + canonical (trim + NFKC + lowerCase) 분리.
 * canonical 만 전역 unique (ADR-0011 §1).
 *
 * <p>허용 문자: alphanumeric + 한글 + `_` + `-`. 길이 2-20 자.
 */
public final class Nickname {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{IsHangul}A-Za-z0-9_-]+$");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;

    private final String display;
    private final String canonical;

    public Nickname(String input) {
        if (!isValidDisplay(input)) {
            throw new InvalidUserContentException("nickname");
        }
        String trimmed = input.trim();
        this.display = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        this.canonical = Normalizer.normalize(trimmed, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /**
     * 길이·허용 문자 정책의 단일 출처 (trim 후 기준, ADR-0005 §5.1). 경계 커스텀
     * validator(`@ValidNickname`)가 이 메서드를 재사용해 규칙 divergence 방지.
     */
    public static boolean isValidDisplay(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        return trimmed.length() >= MIN_LENGTH
                && trimmed.length() <= MAX_LENGTH
                && ALLOWED.matcher(trimmed).matches();
    }

    /** 영속 복원 자리 — 검증 없이 두 값 직접 주입. */
    public static Nickname restore(String display, String canonical) {
        return new Nickname(display, canonical);
    }

    private Nickname(String display, String canonical) {
        this.display = display;
        this.canonical = canonical;
    }

    public String display() {
        return display;
    }

    public String canonical() {
        return canonical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Nickname that)) return false;
        return Objects.equals(canonical, that.canonical);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonical);
    }
}
