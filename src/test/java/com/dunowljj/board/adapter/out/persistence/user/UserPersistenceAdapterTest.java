package com.dunowljj.board.adapter.out.persistence.user;

import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.common.error.DuplicateEmailException;
import com.dunowljj.board.common.error.DuplicateNicknameException;
import com.dunowljj.board.config.MutableClock;
import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.dunowljj.board.config.TestAuditConfig;
import com.dunowljj.board.config.TimeConfig;
import com.dunowljj.board.domain.post.PostFixtures;
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.Nickname;
import com.dunowljj.board.domain.user.PasswordHash;
import com.dunowljj.board.domain.user.User;
import com.dunowljj.board.domain.user.UserFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({UserPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class, PostgresTestcontainersConfig.class})
class UserPersistenceAdapterTest {

    @Autowired
    UserPersistenceAdapter adapter;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setTo(PostFixtures.FIXED_NOW);
    }

    @Test
    @DisplayName("save 후 findByEmail 로 같은 사용자를 조회한다")
    void save_then_findByEmail_round_trip() {
        AuditedUser saved = adapter.save(UserFixtures.aValidUser());

        assertThat(saved.user().getId()).isNotNull();
        assertThat(saved.user().getEmail().value()).isEqualTo("alice@example.com");

        Optional<AuditedUser> found = adapter.findByEmail(new Email("alice@example.com"));
        assertThat(found).isPresent();
        assertThat(found.get().user().getId()).isEqualTo(saved.user().getId());
    }

    @Test
    @DisplayName("findByEmail 은 canonical (lower-case) 기준으로 매칭한다")
    void findByEmail_matches_canonical() {
        adapter.save(UserFixtures.aValidUser());

        Optional<AuditedUser> found = adapter.findByEmail(new Email("Alice@Example.COM"));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("existsByEmail / existsByNicknameCanonical 검증")
    void exists_checks() {
        adapter.save(UserFixtures.aValidUser());

        assertThat(adapter.existsByEmail(new Email("alice@example.com"))).isTrue();
        assertThat(adapter.existsByEmail(new Email("other@example.com"))).isFalse();
        assertThat(adapter.existsByNicknameCanonical("alice")).isTrue();
        assertThat(adapter.existsByNicknameCanonical("bob")).isFalse();
    }

    @Test
    @DisplayName("동일 email 로 save 하면 DuplicateEmailException (DB unique constraint race fallback)")
    void save_throws_DuplicateEmailException_on_db_unique_violation() {
        adapter.save(UserFixtures.aValidUser());

        User duplicate = User.register(
                new Email("alice@example.com"),
                new Nickname("different"),
                new PasswordHash("$2a$10$other"));

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("동일 nicknameCanonical 로 save 하면 DuplicateNicknameException (Alice vs alice 차단)")
    void save_throws_DuplicateNicknameException_on_canonical_collision() {
        adapter.save(UserFixtures.aValidUser());  // nickname = "alice"

        User duplicate = User.register(
                new Email("other@example.com"),
                new Nickname("Alice"),  // canonical = "alice"
                new PasswordHash("$2a$10$other"));

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DuplicateNicknameException.class);
    }
}
