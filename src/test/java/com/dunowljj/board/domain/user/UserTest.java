package com.dunowljj.board.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("register 는 id 없이 사용자 객체를 만든다")
    void register_creates_user_without_id() {
        User user = UserFixtures.aValidUser();

        assertThat(user.getId()).isNull();
        assertThat(user.getEmail().value()).isEqualTo("alice@example.com");
        assertThat(user.getNickname().display()).isEqualTo("alice");
        assertThat(user.getPasswordHash().value()).startsWith("$2a$10$");
    }

    @Test
    @DisplayName("reconstitute 는 id 와 함께 영속 복원한다")
    void reconstitute_preserves_id() {
        User user = UserFixtures.aReconstitutedUser(42L);

        assertThat(user.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("reconstitute 시 id 가 null 이면 IllegalArgumentException")
    void reconstitute_rejects_null_id() {
        assertThatThrownBy(() -> User.reconstitute(null,
                new Email("alice@example.com"),
                new Nickname("alice"),
                new PasswordHash("$2a$10$abc")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
