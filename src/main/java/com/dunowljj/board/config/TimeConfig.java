package com.dunowljj.board.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time-related infrastructure beans. Wires the single {@link Clock} the
 * application layer consumes to capture {@code now} values for the domain
 * (ADR-0007 §1). Uses {@link Clock#systemDefaultZone()} to preserve current
 * {@code LocalDateTime} semantics — UTC migration is a separate ADR.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
