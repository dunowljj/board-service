package com.dunowljj.board.domain.post;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class PostFixtures {

    private static final Clock FIXED_CLOCK = fixedClockAt(LocalDateTime.of(2026, 5, 17, 10, 0));
    public static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);

    private PostFixtures() {}

    public static Clock fixedClock() {
        return FIXED_CLOCK;
    }

    /**
     * Build a {@link Clock} fixed such that {@code LocalDateTime.now(clock)} returns
     * the given {@code localNow} regardless of the host's default time zone. Anchors
     * the Instant via {@code ZoneId.systemDefault()} to stay consistent with the
     * production bean's {@code Clock.systemDefaultZone()} (ADR-0007 §1).
     */
    public static Clock fixedClockAt(LocalDateTime localNow) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(localNow.atZone(zone).toInstant(), zone);
    }

    public static Post aValidPost() {
        return Post.create(FIXED_NOW, "title", "body", "author");
    }

    public static Post aReconstitutedPost(Long id) {
        return Post.reconstitute(id, "title", "body", "author", FIXED_NOW, FIXED_NOW);
    }

    public static Post aReconstitutedPost(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return Post.reconstitute(id, "title", "body", "author", createdAt, updatedAt);
    }
}
