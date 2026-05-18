package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.domain.post.Post;

public class PostMapper {

    private PostMapper() {}

    /**
     * entity → AuditedPost. 도메인 Post 와 audit metadata 를 합성. 도메인은
     * audit 을 모르므로 mapper 가 별도 합성 (ADR-0008 §4).
     */
    public static AuditedPost toAuditedPost(PostJpaEntity entity) {
        Post post = Post.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getAuthor()
        );
        return new AuditedPost(post, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /**
     * 신규 entity 생성 전용. audit timestamp 는 listener 가 채울 자리.
     * Update 경로에서는 사용 금지 (ADR-0008 §4.1) — load-mutate-save 패턴 사용.
     */
    public static PostJpaEntity toEntity(Post post) {
        return new PostJpaEntity(
                post.getId(),
                post.getTitle(),
                post.getBody(),
                post.getAuthor()
        );
    }
}
