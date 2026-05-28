package com.dunowljj.board.application.port.in.result;

import com.dunowljj.board.domain.user.User;

import java.time.LocalDateTime;

/**
 * Use case 반환 타입. domain User + audit metadata 합성. 표시용 닉네임은
 * {@link com.dunowljj.board.domain.user.Nickname#display()} 그대로 노출.
 */
public record UserResult(
        Long id,
        String email,
        String nickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResult of(User user, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new UserResult(
                user.getId(),
                user.getEmail().value(),
                user.getNickname().display(),
                createdAt,
                updatedAt);
    }
}
