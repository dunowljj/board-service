package com.dunowljj.board.domain.post;

import java.time.LocalDateTime;

public final class PostFixtures {

    private PostFixtures() {}

    public static Post aValidPost() {
        return Post.create("title", "body", "author");
    }

    public static Post aReconstitutedPost(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return Post.reconstitute(id, "title", "body", "author", now, now);
    }

    public static Post aReconstitutedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return Post.reconstitute(id, "title", "body", "author", createdAt, updatedAt);
    }
}
