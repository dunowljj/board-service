package com.dunowljj.board.application.port.out.result;

import com.dunowljj.board.domain.user.User;

import java.time.LocalDateTime;

/**
 * Outbound port 가 반환하는 User + audit metadata 합성 (ADR-0008).
 */
public record AuditedUser(User user, LocalDateTime createdAt, LocalDateTime updatedAt) {}
