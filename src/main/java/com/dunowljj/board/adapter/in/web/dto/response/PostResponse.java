package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.application.port.in.result.AuditedPostResult;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String title,
        String body,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(AuditedPostResult result) {
        return new PostResponse(
                result.id(),
                result.title(),
                result.body(),
                result.author(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
