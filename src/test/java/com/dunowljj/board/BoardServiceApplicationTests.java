package com.dunowljj.board;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * `@SpringBootTest` 는 full Spring context 를 띄우므로 ADR-0006 §10 에 따라
 * 통합테스트로 분류 — 빠른 `test` task 에서 제외하기 위해 `integration` 태그.
 */
@SpringBootTest
@Tag("integration")
class BoardServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
