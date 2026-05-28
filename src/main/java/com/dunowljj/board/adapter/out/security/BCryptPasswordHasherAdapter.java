package com.dunowljj.board.adapter.out.security;

import com.dunowljj.board.application.port.out.PasswordHasherPort;
import com.dunowljj.board.domain.user.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Spring Security {@link PasswordEncoder} (BCrypt strength 10) 위임. application 은
 * {@link PasswordHasherPort} 만 알고 Spring 의존을 모름 (ADR-0011 §5).
 */
@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasherAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash.value());
    }
}
