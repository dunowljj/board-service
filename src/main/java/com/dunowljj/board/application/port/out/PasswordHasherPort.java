package com.dunowljj.board.application.port.out;

import com.dunowljj.board.domain.user.PasswordHash;

/**
 * 평문 password 의 해시 / 검증 outbound capability. Spring Security {@code PasswordEncoder}
 * 를 application service 에 직접 주입하지 않기 위한 port (ADR-0011 §5, PLAN-0011 Risk #5).
 */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash passwordHash);
}
