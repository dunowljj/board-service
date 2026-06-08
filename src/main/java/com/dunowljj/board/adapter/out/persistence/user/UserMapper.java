package com.dunowljj.board.adapter.out.persistence.user;

import com.dunowljj.board.application.port.out.result.AuditedUser;
import com.dunowljj.board.domain.user.Email;
import com.dunowljj.board.domain.user.Nickname;
import com.dunowljj.board.domain.user.PasswordHash;
import com.dunowljj.board.domain.user.User;

final class UserMapper {

    private UserMapper() {}

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId(),
                user.getEmail().value(),
                user.getNickname().display(),
                user.getNickname().canonical(),
                user.getPasswordHash().value());
    }

    static AuditedUser toAudited(UserJpaEntity entity) {
        User user = User.reconstitute(
                entity.getId(),
                new Email(entity.getEmail()),
                Nickname.restore(entity.getNickname(), entity.getNicknameCanonical()),
                new PasswordHash(entity.getPasswordHash()));
        return new AuditedUser(user, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
