package com.dunowljj.board.application.port.out.result;

import com.dunowljj.board.domain.post.Post;

import java.time.LocalDateTime;

/**
 * Outbound port 가 반환하는 *도메인 + audit metadata + author 표시명* 합성 타입 (ADR-0008 §4, PLAN-0011 §7).
 * persistence adapter 가 users join projection 으로 {@code authorNickname} 을 채워 N+1 회피.
 */
public record AuditedPost(
        Post post,
        String authorNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
