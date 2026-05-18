package com.dunowljj.board.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트 전용 가변 {@link Clock}. {@link #setTo(LocalDateTime)} / {@link #advance(Duration)}
 * 로 시점을 명시적으로 조작. 단일 thread 가정 — 동시성 안전 미보장.
 * {@code instant} 필드는 {@code volatile} 로 memory visibility 만 보장한다.
 *
 * <p>ADR-0008 §8.1 의 시점 조절 가능 Clock 패턴. {@link TestAuditConfig} 가
 * {@code @Primary} 빈으로 등록해 audit listener 의 시간 출처 (DateTimeProvider) 를
 * 결정적으로 만든다.
 */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public static MutableClock startingAt(LocalDateTime localNow) {
        ZoneId zone = ZoneId.systemDefault();
        return new MutableClock(localNow.atZone(zone).toInstant(), zone);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setTo(LocalDateTime localNow) {
        this.instant = localNow.atZone(zone).toInstant();
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
