package com.dunowljj.board.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 시스템의 시간 출처. {@link Clock} 빈 1 개가 진실원 — JPA Auditing 의
 * {@link DateTimeProvider} 와 (미래) 비즈니스 timestamp 캡처 모두 공유 (ADR-0008 §6).
 *
 * <p>{@code Clock.systemDefaultZone()} 채택 (ADR-0007 §1) — UTC 전환은 별도 ADR.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
