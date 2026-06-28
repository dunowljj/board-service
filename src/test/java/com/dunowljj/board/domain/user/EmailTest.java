package com.dunowljj.board.domain.user;

import com.dunowljj.board.common.error.InvalidUserContentException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    @DisplayName("trim + lower-case canonical 로 저장한다")
    void normalizes_to_lower_case_with_trim() {
        Email email = new Email("  Alice@Example.COM  ");
        assertThat(email.value()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("null 이면 InvalidUserContentException 을 던진다")
    void rejects_null() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("빈 문자열이면 InvalidUserContentException 을 던진다")
    void rejects_blank() {
        assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("형식이 잘못되면 InvalidUserContentException 을 던진다")
    void rejects_invalid_format() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("equals 와 hashCode 는 정규화된 value 기준으로 동작한다")
    void equals_and_hashCode_contract() {
        EqualsVerifier.forClass(Email.class)
                .withOnlyTheseFields("value")
                .verify();
    }

    @Test
    @DisplayName("정규화로 대소문자만 다른 입력은 동일 value 가 되고, 그 결과 equals 가 같다고 본다")
    void case_variants_normalize_to_same_value() {
        Email a = new Email("Alice@Example.com");
        Email b = new Email("alice@example.com");
        // 원인: normalize 가 둘을 같은 canonical value 로 접는다 (equals 가 ignoreCase 인 게 아님 —
        // equals 는 평범한 value 비교, 위 equals_and_hashCode_contract 가 그 점을 보장).
        assertThat(a.value()).isEqualTo(b.value());
        // 결과: 같은 value 이므로 평범한 equals 가 동일로 판정.
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Email.isValid 정책 메서드의 형식 판정 계약 (true/false 케이스)")
    void isValid_policy_method_contract() {
        assertThat(Email.isValid("alice@example.com")).isTrue();
        assertThat(Email.isValid("  Alice@Example.COM  ")).isTrue(); // trim + lower 후 통과
        assertThat(Email.isValid("not-an-email")).isFalse();
        assertThat(Email.isValid(null)).isFalse();
        assertThat(Email.isValid("   ")).isFalse(); // blank → 정규화 후 empty → false
    }
}
