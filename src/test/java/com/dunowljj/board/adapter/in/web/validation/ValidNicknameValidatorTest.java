package com.dunowljj.board.adapter.in.web.validation;

import com.dunowljj.board.domain.user.Nickname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ValidNicknameValidator} 단위 테스트. {@link ValidEmailValidatorTest} 와 같은 계약 —
 * 형식·문자·길이만 보고 null/blank 는 {@code @NotBlank} 에 위임(의도된 null-pass, ADR-0005 §5.1).
 */
class ValidNicknameValidatorTest {

    private final ValidNicknameValidator validator = new ValidNicknameValidator();

    @Test
    @DisplayName("허용 문자·길이 닉네임은 통과한다 (한글 포함)")
    void valid_nickname_passes() {
        assertThat(validator.isValid("alice", null)).isTrue();
        assertThat(validator.isValid("관리자", null)).isTrue();
    }

    @Test
    @DisplayName("허용 안 된 문자 / 길이 위반이면 위반(false)")
    void invalid_nickname_fails() {
        assertThat(validator.isValid("alice bob", null)).isFalse();   // 공백
        assertThat(validator.isValid("alice@bob", null)).isFalse();   // 특수문자
        assertThat(validator.isValid("a", null)).isFalse();           // 너무 짧음
        assertThat(validator.isValid("a".repeat(21), null)).isFalse();// 너무 김
    }

    @Test
    @DisplayName("null 은 통과(true) — 존재 검사는 @NotBlank 책임 (의도된 null-pass)")
    void null_passes_deferring_to_notblank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("blank 는 통과(true) — Nickname.isValidDisplay 는 blank 를 false 로 보지만 validator 는 @NotBlank 에 위임")
    void blank_passes_even_though_policy_rejects_it() {
        assertThat(Nickname.isValidDisplay("   ")).isFalse();
        assertThat(validator.isValid("   ", null)).isTrue();
    }
}
