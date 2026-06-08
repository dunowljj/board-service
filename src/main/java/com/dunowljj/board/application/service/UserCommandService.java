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
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.Nickname;
import com.dunowljj.board.domain.user.PasswordHash;
import com.dunowljj.board.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCommandService implements RegisterUserUseCase, LoginUserUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    /**
     * BCrypt 의 실질 72 byte 한계 정합. UTF-8 byte 길이 기준 검증으로 한글 / 이모지 등이
     * 72 byte 를 넘으면 truncation 위험.
     */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final SaveUserPort saveUserPort;
    private final LoadUserPort loadUserPort;
    private final ExistsUserPort existsUserPort;
    private final PasswordHasherPort passwordHasher;

    @Override
    public UserResult register(RegisterUserCommand command) {
        Email email = new Email(command.email());
        Nickname nickname = new Nickname(command.nickname());
        validatePassword(command.password());

        if (existsUserPort.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (existsUserPort.existsByNicknameCanonical(nickname.canonical())) {
            throw new DuplicateNicknameException();
        }

        PasswordHash hash = passwordHasher.hash(command.password());
        User user = User.register(email, nickname, hash);

        AuditedUser saved = saveUserPort.save(user);
        return UserResult.of(saved.user(), saved.createdAt(), saved.updatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Long login(LoginCommand command) {
        Email email;
        try {
            email = new Email(command.email());
        } catch (RuntimeException invalidFormat) {
            throw new AuthenticationFailedException();
        }
        // null password 를 hasher 전에 인증 실패로 처리 — BCrypt 가 null raw 에 IllegalArgumentException
        // 을 던져 500/enumeration oracle 이 되는 경로 차단. 존재/비존재 email 응답을 401 로 통일.
        if (command.password() == null) {
            throw new AuthenticationFailedException();
        }
        AuditedUser audited = loadUserPort.findByEmail(email)
                .orElseThrow(AuthenticationFailedException::new);
        if (!passwordHasher.matches(command.password(), audited.user().getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        return audited.user().getId();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new com.dunowljj.board.common.error.InvalidUserContentException("password");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new com.dunowljj.board.common.error.InvalidUserContentException("password");
        }
    }
}
