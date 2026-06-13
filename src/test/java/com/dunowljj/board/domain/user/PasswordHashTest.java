package com.dunowljj.board.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashTest {

    @Test
    @DisplayName("유효한 해시는 그대로 보관한다")
    void holds_valid_hash() {
        PasswordHash hash = new PasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        assertThat(hash.value()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
    }

    @Test
    @DisplayName("null/blank 해시는 IllegalStateException — 내부 불변식이라 BusinessException 아님(→ 5xx, ADR-0005 §5.1)")
    void blank_hash_throws_illegal_state_not_business_exception() {
        assertThatThrownBy(() -> new PasswordHash(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PasswordHash("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PasswordHash("   ")).isInstanceOf(IllegalStateException.class);
    }
}
