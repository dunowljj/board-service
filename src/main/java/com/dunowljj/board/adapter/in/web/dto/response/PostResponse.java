package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.domain.post.Post;
import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String title,
        String body,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getBody(),
                post.getAuthor(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
