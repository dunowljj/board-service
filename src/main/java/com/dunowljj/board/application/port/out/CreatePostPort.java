package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.domain.post.Post;

/**
 * 신규 영속 outbound port. {@code post.getId() == null} 보장. CQRS 정신
 * (ADR-0003) 과 정합하게 create 와 update 를 별도 port 로 분리 — 호출자가
 * 의도를 port 선택으로 명시 (ADR-0008 §4).
 *
 * <p>메서드명 {@code create(Post)} — 단일 adapter 가 {@link UpdatePostPort} 와
 * 동시 구현해야 하므로 시그니처 충돌 회피 (같은 {@code save(Post)} 면 Java 컴파일 불가).
 */
public interface CreatePostPort {

    AuditedPost create(Post post);
}
