package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.LoginUserUseCase;
import com.dunowljj.board.application.port.in.RegisterUserUseCase;
import com.dunowljj.board.application.port.in.result.UserResult;
import com.dunowljj.board.application.port.out.ExistsUserPort;
import com.dunowljj.board.application.port.out.LoadUserPort;
import com.dunowljj.board.application.port.out.PasswordHasherPort;
import com.dunowljj.board.application.port.out.SaveUserPort;
import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.common.error.AuthenticationFailedException;
import com.dunowljj.board.common.error.DuplicateEmailException;
import com.dunowljj.board.common.error.DuplicateNicknameException;
import com.dunowljj.board.common.error.InvalidUserContentException;
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.Nickname;
import com.dunowljj.board.domain.user.PasswordHash;
import com.dunowljj.board.domain.user.User;
import com.dunowljj.board.domain.user.UserFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock SaveUserPort saveUserPort;
    @Mock LoadUserPort loadUserPort;
    @Mock ExistsUserPort existsUserPort;
    @Mock PasswordHasherPort passwordHasher;

    UserCommandService sut;

    @BeforeEach
    void setUp() {
        sut = new UserCommandService(saveUserPort, loadUserPort, existsUserPort, passwordHasher);
    }

    @Test
    @DisplayName("회원가입 성공 시 password 해시 후 저장하고 UserResult 반환")
    void register_hashes_password_and_saves() {
        when(existsUserPort.existsByEmail(any())).thenReturn(false);
        when(existsUserPort.existsByNicknameCanonical(any())).thenReturn(false);
        when(passwordHasher.hash("secret123"))
                .thenReturn(new PasswordHash("$2a$10$hashedXXX"));
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0);
        when(saveUserPort.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return new AuditedUser(User.reconstitute(1L, user.getEmail(),
                    user.getNickname(), user.getPasswordHash()), now, now);
        });

        UserResult result = sut.register(new RegisterUserUseCase.RegisterUserCommand(
                "alice@example.com", "alice", "secret123"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.nickname()).isEqualTo("alice");
    }

    @Test
    @DisplayName("회원가입 시 email 중복이면 DuplicateEmailException")
    void register_throws_when_email_exists() {
        when(existsUserPort.existsByEmail(any())).thenReturn(true);

        assertThatThrownBy(() -> sut.register(new RegisterUserUseCase.RegisterUserCommand(
                "alice@example.com", "alice", "secret123")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("회원가입 시 nickname canonical 중복이면 DuplicateNicknameException")
    void register_throws_when_nickname_exists() {
        when(existsUserPort.existsByEmail(any())).thenReturn(false);
        when(existsUserPort.existsByNicknameCanonical(any())).thenReturn(true);

        assertThatThrownBy(() -> sut.register(new RegisterUserUseCase.RegisterUserCommand(
                "alice@example.com", "Alice", "secret123")))
                .isInstanceOf(DuplicateNicknameException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 InvalidUserContentException")
    void register_throws_when_password_too_short() {
        assertThatThrownBy(() -> sut.register(new RegisterUserUseCase.RegisterUserCommand(
                "alice@example.com", "alice", "short")))
                .isInstanceOf(InvalidUserContentException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("로그인 성공 시 actorUserId 반환")
    void login_returns_actor_user_id_on_success() {
        User user = UserFixtures.aReconstitutedUser(7L);
        when(loadUserPort.findByEmail(any()))
                .thenReturn(Optional.of(new AuditedUser(user, LocalDateTime.now(), LocalDateTime.now())));
        when(passwordHasher.matches(ArgumentMatchers.eq("secret123"), any())).thenReturn(true);

        Long actorUserId = sut.login(new LoginUserUseCase.LoginCommand("alice@example.com", "secret123"));

        assertThat(actorUserId).isEqualTo(7L);
    }

    @Test
    @DisplayName("로그인 시 사용자가 없으면 AuthenticationFailedException")
    void login_throws_when_user_not_found() {
        when(loadUserPort.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.login(new LoginUserUseCase.LoginCommand("alice@example.com", "secret123")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("로그인 시 password 불일치면 AuthenticationFailedException")
    void login_throws_when_password_mismatch() {
        User user = UserFixtures.aReconstitutedUser(7L);
        when(loadUserPort.findByEmail(any()))
                .thenReturn(Optional.of(new AuditedUser(user, LocalDateTime.now(), LocalDateTime.now())));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> sut.login(new LoginUserUseCase.LoginCommand("alice@example.com", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("로그인 시 email format 자체가 잘못되면 AuthenticationFailedException (사용자 존재 여부 노출 X)")
    void login_throws_authentication_failed_when_email_format_invalid() {
        assertThatThrownBy(() -> sut.login(new LoginUserUseCase.LoginCommand("not-an-email", "secret123")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("로그인 시 password 가 null 이면 hasher 호출 없이 AuthenticationFailedException (BCrypt null IllegalArgument → 500/enumeration 차단)")
    void login_throws_authentication_failed_when_password_null() {
        assertThatThrownBy(() -> sut.login(new LoginUserUseCase.LoginCommand("alice@example.com", null)))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(passwordHasher, never()).matches(any(), any());
    }
}
