package com.dunowljj.board.adapter.in.web.validation;

import com.dunowljj.board.domain.user.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ValidEmailValidator} 단위 테스트. 경계 validator 자체의 분기를 빠르게 고정한다
 * (E2E 통합 경로와 별개로). 핵심은 *null/blank → true(통과)* 가드 — 존재 검사는
 * {@code @NotBlank} 가 담당하고 본 validator 는 형식만 본다(ADR-0005 §5.1, 다중 에러
 * aggregation 을 위한 의도된 null-pass).
 */
class ValidEmailValidatorTest {

    private final ValidEmailValidator validator = new ValidEmailValidator();

    @Test
    @DisplayName("정상 형식 email 은 통과한다")
    void valid_format_passes() {
        assertThat(validator.isValid("alice@example.com", null)).isTrue();
    }

    @Test
    @DisplayName("형식이 잘못되면 false 를 반환한다")
    void invalid_format_fails() {
        assertThat(validator.isValid("not-an-email", null)).isFalse();
    }

    @Test
    @DisplayName("null 은 통과(true) — 존재 검사는 @NotBlank 책임 (의도된 null-pass)")
    void null_passes_deferring_to_notblank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("blank 는 통과(true) — Email.isValid 는 blank 를 false 로 보지만 validator 는 @NotBlank 에 위임해 통과시킨다(중복 errors 회피)")
    void blank_passes_even_though_policy_rejects_it() {
        // 이 비대칭이 토론의 핵심: 도메인 정책(Email.isValid)과 경계 validator 의 blank 처리는
        // 의도적으로 반대다. validator 가 blank 를 통과시켜야 빈 값에 @NotBlank 메시지만 1건 나온다.
        assertThat(Email.isValid("   ")).isFalse();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    @DisplayName("nonblank 입력에서 validator 결과가 Email.isValid 와 일치한다 — 규칙 단일 출처(공유) 회귀 가드")
    void agrees_with_policy_for_nonblank_inputs() {
        // null/blank 는 가드로 갈라지므로 nonblank 표본에 한해 위임 동치를 고정 — validator 가 규칙을
        // 자체 재구현해 정책 메서드와 divergence 하면 이 테스트가 깨진다.
        for (String s : new String[]{
                "alice@example.com", "Bob@Example.COM", "  trim@me.com  ",
                "not-an-email", "a@b", "no-at-symbol"}) {
            assertThat(validator.isValid(s, null))
                    .as("input=%s", s)
                    .isEqualTo(Email.isValid(s));
        }
    }
}
