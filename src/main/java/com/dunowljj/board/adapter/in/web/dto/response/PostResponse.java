package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.application.port.in.result.AuditedPostResult;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String title,
        String body,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(AuditedPostResult result) {
        return new PostResponse(
                result.id(),
                result.title(),
                result.body(),
                result.authorId(),
                result.authorNickname(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
