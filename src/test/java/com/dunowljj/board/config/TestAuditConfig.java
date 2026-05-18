package com.dunowljj.board.config;

import com.dunowljj.board.domain.post.PostFixtures;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@code @DataJpaTest} slice 에서 production {@code Clock} 빈을 override 하기 위한
 * 테스트 전용 config. {@code @Primary MutableClock} 이 production
 * {@code Clock.systemDefaultZone()} 빈을 override 해 audit listener 의 시간 출처를
 * 결정적으로 만든다 (ADR-0008 §8).
 *
 * <p>반환 타입을 {@link MutableClock} 로 명시 — 호출자가 {@code @Autowired MutableClock}
 * 로 받아 {@code setTo}/{@code advance} 직접 사용.
 */
@TestConfiguration
public class TestAuditConfig {

    @Bean
    @Primary
    public MutableClock auditClock() {
        return MutableClock.startingAt(PostFixtures.FIXED_NOW);
    }
}
