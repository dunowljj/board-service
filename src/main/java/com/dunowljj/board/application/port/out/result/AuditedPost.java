package com.dunowljj.board.application.port.out.result;

import com.dunowljj.board.domain.post.Post;

import java.time.LocalDateTime;

/**
 * Outbound port 가 반환하는 *도메인 + audit metadata* 합성 타입.
 * 도메인은 audit 을 모르므로 영속 어댑터가 entity 의 timestamp 를
 * 별도로 합쳐 application 으로 돌려준다 (ADR-0008 §4).
 */
public record AuditedPost(Post post, LocalDateTime createdAt, LocalDateTime updatedAt) {}
