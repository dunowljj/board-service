package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.result.UserResult;
import com.dunowljj.board.application.port.out.LoadUserPort;
import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.common.error.UserNotFoundException;
import com.dunowljj.board.domain.user.UserFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock LoadUserPort loadUserPort;

    UserQueryService sut;

    @BeforeEach
    void setUp() {
        sut = new UserQueryService(loadUserPort);
    }

    @Test
    @DisplayName("id 로 조회 성공 시 UserResult 반환")
    void getById_returns_user_result() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 12, 0);
        when(loadUserPort.findById(7L))
                .thenReturn(Optional.of(new AuditedUser(UserFixtures.aReconstitutedUser(7L), now, now)));

        UserResult result = sut.getById(7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.nickname()).isEqualTo("alice");
    }

    @Test
    @DisplayName("id 로 조회 실패 시 UserNotFoundException")
    void getById_throws_when_not_found() {
        when(loadUserPort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
