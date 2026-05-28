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
    @DisplayName("대소문자 차이만 있는 두 입력은 같은 Email 로 평가된다")
    void case_variants_are_equal() {
        Email a = new Email("Alice@Example.com");
        Email b = new Email("alice@example.com");
        assertThat(a).isEqualTo(b);
    }
}
