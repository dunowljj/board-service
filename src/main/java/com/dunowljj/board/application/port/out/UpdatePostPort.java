package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.domain.post.Post;

/**
 * 기존 영속 갱신 outbound port. {@code post.getId() != null} 보장. 구현은
 * load-mutate-save 패턴 + flush 보장 invariant — 반환 audit timestamp 를 읽기 전
 * flush 가 끝나 있어야 한다 (ADR-0008 §4.1).
 *
 * <p>메서드명 {@code update(Post)} — {@link CreatePostPort} 와의 시그니처 충돌
 * 회피 (단일 adapter 가 두 port 동시 구현).
 */
public interface UpdatePostPort {

    AuditedPost update(Post post);
}
