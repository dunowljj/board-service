package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.application.port.in.result.UserResult;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResponse from(UserResult result) {
        return new UserResponse(
                result.id(),
                result.email(),
                result.nickname(),
                result.createdAt(),
                result.updatedAt());
    }
}
