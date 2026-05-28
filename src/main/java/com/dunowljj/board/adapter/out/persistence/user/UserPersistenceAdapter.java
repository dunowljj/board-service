package com.dunowljj.board.adapter.out.persistence.user;

import com.dunowljj.board.application.port.out.ExistsUserPort;
import com.dunowljj.board.application.port.out.LoadUserPort;
import com.dunowljj.board.application.port.out.SaveUserPort;
import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.common.error.DuplicateEmailException;
import com.dunowljj.board.common.error.DuplicateNicknameException;
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * User outbound port 통합 어댑터.
 *
 * <p>{@code save} 시 DB unique constraint race fallback — application 의 사전 {@code exists} 검증과
 * DB INSERT 사이에 동시 가입이 일어나면 {@link DataIntegrityViolationException} 발생. constraint
 * name (`uk_users_email`, `uk_users_nickname_canonical`) 기준으로
 * {@link DuplicateEmailException} / {@link DuplicateNicknameException} 변환 (PLAN-0011 Risk #5).
 * application 으로 {@code org.springframework.dao.*} 의존 전파 금지.
 */
@Component
public class UserPersistenceAdapter implements SaveUserPort, LoadUserPort, ExistsUserPort {

    private static final String EMAIL_CONSTRAINT = "uk_users_email";
    private static final String NICKNAME_CONSTRAINT = "uk_users_nickname_canonical";

    private final UserJpaRepository repository;

    public UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditedUser save(User user) {
        UserJpaEntity entity = UserMapper.toEntity(user);
        UserJpaEntity saved;
        try {
            saved = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            String message = rootMessage(e);
            if (message.contains(EMAIL_CONSTRAINT)) {
                throw new DuplicateEmailException(user.getEmail().value());
            }
            if (message.contains(NICKNAME_CONSTRAINT)) {
                throw new DuplicateNicknameException(user.getNickname().display());
            }
            throw e;
        }
        return UserMapper.toAudited(saved);
    }

    @Override
    public Optional<AuditedUser> findById(Long id) {
        return repository.findById(id).map(UserMapper::toAudited);
    }

    @Override
    public Optional<AuditedUser> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(UserMapper::toAudited);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public boolean existsByNicknameCanonical(String canonical) {
        return repository.existsByNicknameCanonical(canonical);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        StringBuilder sb = new StringBuilder();
        while (cause != null) {
            if (cause.getMessage() != null) {
                sb.append(cause.getMessage()).append('\n');
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }
}
