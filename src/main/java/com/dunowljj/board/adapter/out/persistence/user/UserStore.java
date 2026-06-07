package com.dunowljj.board.adapter.out.persistence.user;

import com.dunowljj.board.adapter.out.persistence.common.UniqueViolationGuard;
import com.dunowljj.board.common.error.DuplicateEmailException;
import com.dunowljj.board.common.error.DuplicateNicknameException;
import com.dunowljj.board.domain.user.User;
import org.springframework.stereotype.Component;

/**
 * {@link UserJpaRepository} 를 감싸 unique 위반을 도메인 예외로 번역하는 save helper.
 *
 * <p>조회는 repository 의 파생 query 메서드(findByEmail 등)로 충분하지만, save 는 race 로 인한
 * unique 위반을 도메인 예외로 변환하는 try/catch 가 필요하다. Spring Data 인터페이스에는 명령형
 * 분기를 둘 수 없어 그 한 조각만 이 helper 로 분리한다. 어댑터는 {@link #saveUnique} 한 줄로
 * 호출해 비즈니스 흐름만 남긴다 (PLAN-0011 Risk #5).
 */
@Component
class UserStore {

    private final UserJpaRepository repository;

    UserStore(UserJpaRepository repository) {
        this.repository = repository;
    }

    /** saveAndFlush 후 unique 위반 시 constraint 기준 도메인 예외로 변환해 던진다. */
    UserJpaEntity saveUnique(UserJpaEntity entity, User user) {
        return new UniqueViolationGuard()
                .on(UserJpaEntity.EMAIL_CONSTRAINT, () -> new DuplicateEmailException(user.getEmail().value()))
                .on(UserJpaEntity.NICKNAME_CONSTRAINT, () -> new DuplicateNicknameException(user.getNickname().display()))
                .execute(() -> repository.saveAndFlush(entity));
    }
}
