package com.dunowljj.board.adapter.in.web.error;

import com.dunowljj.board.adapter.in.web.dto.request.CreatePostRequest;
import com.dunowljj.board.adapter.in.web.dto.request.RegisterRequest;
import com.dunowljj.board.domain.post.PostContent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;

import java.util.Set;

import static com.dunowljj.board.adapter.in.web.error.ValidationErrorCode.INVALID;
import static com.dunowljj.board.adapter.in.web.error.ValidationErrorCode.INVALID_FORMAT;
import static com.dunowljj.board.adapter.in.web.error.ValidationErrorCode.REQUIRED;
import static com.dunowljj.board.adapter.in.web.error.ValidationErrorCode.TOO_LONG;
import static com.dunowljj.board.adapter.in.web.error.ValidationErrorCode.TOO_SHORT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코드 파생 규칙의 **전수 매트릭스** (PLAN-0012-C, ADR-0005 §5.2).
 *
 * <p>E2E 는 "두 추출 경로에서 실제로 직렬화되어 나온다"만 보고, 조합은 여기서 본다 — 같은 사실을 여러
 * 층에서 못 박지 않기 위함.
 *
 * <p>실제 Hibernate Validator 가 만든 {@code ConstraintViolation} 을 그대로 넣는다(모킹하지 않음).
 * 위반의 descriptor 구조가 우리가 가정한 그대로인지까지 함께 고정된다.
 */
class ValidationErrorCodeTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("@NotBlank(title) 과 @NotNull(body) 은 모두 REQUIRED 로 수렴한다")
    void required_from_notblank_and_notnull() {
        assertThat(codeOf(new CreatePostRequest("", "body"), "title")).isEqualTo(REQUIRED);
        assertThat(codeOf(new CreatePostRequest("title", null), "body")).isEqualTo(REQUIRED);
    }

    @Test
    @DisplayName("@Size 상한 초과는 TOO_LONG, 하한 미달은 TOO_SHORT — 같은 애너테이션의 방향 판정")
    void size_direction_is_resolved_by_attributes() {
        String overLongTitle = "a".repeat(PostContent.MAX_TITLE_LENGTH + 1);
        assertThat(codeOf(new CreatePostRequest(overLongTitle, "body"), "title")).isEqualTo(TOO_LONG);

        // password @Size(min = 8) — 하한만 지정된 제약
        assertThat(codeOf(register("short"), "password")).isEqualTo(TOO_SHORT);
    }

    @Test
    @DisplayName("@MaxUtf8Bytes 는 TOO_LONG — char 수 초과와 같은 코드로 수렴(의도적 정보 손실)")
    void maxutf8bytes_is_too_long() {
        // 한글 1자 = UTF-8 3바이트 → 25자 = 75바이트 > 72
        assertThat(codeOf(register("가".repeat(25)), "password")).isEqualTo(TOO_LONG);
    }

    @Test
    @DisplayName("@ValidEmail / @ValidNickname 은 INVALID_FORMAT")
    void custom_format_constraints_are_invalid_format() {
        assertThat(codeOf(new RegisterRequest("not-an-email", "닉네임", "password123"), "email"))
                .isEqualTo(INVALID_FORMAT);
        assertThat(codeOf(new RegisterRequest("user@example.com", "!!!", "password123"), "nickname"))
                .isEqualTo(INVALID_FORMAT);
    }

    @Test
    @DisplayName("violation 이 없으면 제약 이름 기반 fallback 으로 파생한다")
    void falls_back_to_constraint_name_when_violation_missing() {
        assertThat(ValidationErrorCode.from(null, resolvable("NotBlank"))).isEqualTo(REQUIRED);
        assertThat(ValidationErrorCode.from(null, resolvable("Min"))).isEqualTo(ValidationErrorCode.OUT_OF_RANGE);
        assertThat(ValidationErrorCode.from(null, resolvable("MaxUtf8Bytes"))).isEqualTo(TOO_LONG);
    }

    @Test
    @DisplayName("이름 기반 fallback 에서 @Size 는 방향을 못 가르므로 보수적으로 INVALID")
    void size_falls_back_to_invalid_without_attributes() {
        assertThat(ValidationErrorCode.from(null, resolvable("Size"))).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("알 수 없는 제약과 빈 입력은 INVALID 로 degrade — 예외를 던지지 않는다")
    void unknown_constraint_degrades_to_invalid() {
        assertThat(ValidationErrorCode.from(null, resolvable("SomeFutureConstraint"))).isEqualTo(INVALID);
        assertThat(ValidationErrorCode.from(null, null)).isEqualTo(INVALID);
        assertThat(ValidationErrorCode.from(null, resolvable())).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("supports() — 실사용 제약은 모두 non-fallback 으로 매핑됨을 인정한다")
    void supports_covers_constraints_in_use() {
        assertThat(ValidationErrorCode.supports(jakarta.validation.constraints.NotBlank.class)).isTrue();
        assertThat(ValidationErrorCode.supports(jakarta.validation.constraints.Size.class)).isTrue();
        assertThat(ValidationErrorCode.supports(Deprecated.class)).isFalse();
    }

    private static RegisterRequest register(String password) {
        return new RegisterRequest("user@example.com", "닉네임", password);
    }

    /** 해당 필드의 위반 1건을 실제 validator 로 만들어 코드로 파생한다. */
    private static <T> ValidationErrorCode codeOf(T request, String field) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        ConstraintViolation<T> violation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no violation on field: " + field));
        return ValidationErrorCode.from(violation, null);
    }

    /** Spring 이 제약 이름을 codes 배열 *마지막* 원소로 싣는 규약을 흉내낸다. */
    private static MessageSourceResolvable resolvable(String... codes) {
        return new MessageSourceResolvable() {
            @Override
            public String[] getCodes() {
                return codes;
            }
        };
    }
}
