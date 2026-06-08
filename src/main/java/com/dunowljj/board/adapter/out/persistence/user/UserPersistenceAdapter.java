package com.dunowljj.board.adapter.out.persistence.user;

import com.dunowljj.board.application.port.out.ExistsUserPort;
import com.dunowljj.board.application.port.out.LoadUserPort;
import com.dunowljj.board.application.port.out.SaveUserPort;
import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * User outbound port 통합 어댑터.
 *
 * <p>조회는 {@link UserJpaRepository} 의 파생 query 메서드로, 저장은 {@link UserStore#saveUnique}
 * 로 위임한다. race 로 인한 unique 위반→도메인 예외 변환은 UserStore 가 담당해 어댑터에는
 * 비즈니스 흐름만 남는다 (PLAN-0011 Risk #5). application 으로 {@code org.springframework.dao.*}
 * 의존 전파 금지.
 */
@Component
public class UserPersistenceAdapter implements SaveUserPort, LoadUserPort, ExistsUserPort {

    private final UserJpaRepository repository;
    private final UserStore userStore;

    public UserPersistenceAdapter(UserJpaRepository repository, UserStore userStore) {
        this.repository = repository;
        this.userStore = userStore;
    }

    @Override
    public AuditedUser save(User user) {
        UserJpaEntity saved = userStore.saveUnique(UserMapper.toEntity(user));
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
}
