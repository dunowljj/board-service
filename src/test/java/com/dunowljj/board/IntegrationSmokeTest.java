package com.dunowljj.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `integrationTest` Gradle task 가 `@Tag("integration")` 테스트를 실제로
 * 발견·실행함을 검증하는 placeholder. ADR-0006 §10 의 분리 실행 정책 검증용.
 */
@Tag("integration")
class IntegrationSmokeTest {

    @Test
    @DisplayName("integrationTest task가 integration 태그 테스트를 실행한다")
    void integration_test_task_runs_this_test() {
        assertThat(true).isTrue();
    }
}
