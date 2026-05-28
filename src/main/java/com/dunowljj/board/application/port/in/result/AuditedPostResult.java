package com.dunowljj.board.application.port.in.result;

import com.dunowljj.board.domain.post.Post;

import java.time.LocalDateTime;

/**
 * Use case 반환 타입. application service 가 outbound 결과 (AuditedPost) 를
 * 분해해 이 record 로 합성한다 — port.in.result 가 port.out.result 를
 * 직접 의존하지 않음 (ADR-0008 §5.1).
 *
 * <p>{@code authorNickname} 은 표시용 닉네임 (NFC). persistence adapter 의 join projection 으로 채워짐 (PLAN-0011 §9).
 */
public record AuditedPostResult(
        Long id,
        String title,
        String body,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuditedPostResult from(Post post, String authorNickname, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new AuditedPostResult(
                post.getId(), post.getTitle(), post.getBody(), post.getAuthorId(), authorNickname,
                createdAt, updatedAt
        );
    }
}
