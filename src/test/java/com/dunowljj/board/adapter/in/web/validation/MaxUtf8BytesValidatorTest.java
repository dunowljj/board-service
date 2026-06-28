package com.dunowljj.board.adapter.in.web.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MaxUtf8BytesValidator} 단위 테스트. 핵심은 *char 가 아니라 byte 기준* 상한이라는 점 —
 * 멀티바이트 입력이 char 수로는 작아도 byte 한계(password BCrypt 72)를 넘을 수 있다(ADR-0005 §5.1).
 * {@code initialize(constraint)} 가 읽는 max 는 애너테이션이 붙은 필드에서 reflection 으로 주입.
 */
class MaxUtf8BytesValidatorTest {

    @MaxUtf8Bytes(72)
    private String max72;

    private MaxUtf8BytesValidator validator;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        MaxUtf8Bytes constraint = getClass().getDeclaredField("max72").getAnnotation(MaxUtf8Bytes.class);
        validator = new MaxUtf8BytesValidator();
        validator.initialize(constraint);
    }

    @Test
    @DisplayName("상한 이하 ASCII 는 통과 (경계값 72 byte 포함)")
    void within_and_at_limit_passes() {
        assertThat(validator.isValid("a".repeat(71), null)).isTrue();
        assertThat(validator.isValid("a".repeat(72), null)).isTrue(); // 정확히 경계
    }

    @Test
    @DisplayName("상한 초과 ASCII 는 위반 (73 byte)")
    void over_limit_ascii_fails() {
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    @DisplayName("멀티바이트는 char 수가 아니라 byte 수로 판정한다 (한글 1자 = 3 byte)")
    void counts_bytes_not_chars() {
        // "가" = UTF-8 3 byte. 24자 = 72 byte(통과), 25자 = 75 byte(위반) — char 수(25)는 한참 작음.
        assertThat("가".getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(3);
        assertThat(validator.isValid("가".repeat(24), null)).isTrue();
        assertThat(validator.isValid("가".repeat(25), null)).isFalse();
    }

    @Test
    @DisplayName("null 은 통과(true) — 존재 검사는 @NotBlank 책임 (의도된 null-pass)")
    void null_passes_deferring_to_notblank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
