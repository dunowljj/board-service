package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.domain.post.Post;

public class PostMapper {

    private PostMapper() {}

    public static Post toDomain(PostJpaEntity entity) {
        return Post.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getAuthor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static PostJpaEntity toEntity(Post post) {
        return new PostJpaEntity(
                post.getId(),
                post.getTitle(),
                post.getBody(),
                post.getAuthor(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
