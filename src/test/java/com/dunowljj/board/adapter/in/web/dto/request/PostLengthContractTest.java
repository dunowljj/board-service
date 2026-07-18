package com.dunowljj.board.adapter.in.web.dto.request;

import com.dunowljj.board.common.error.InvalidPostContentException;
import com.dunowljj.board.domain.post.PostContent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 옵션 1(상수 공유 + VO 백스톱)의 로직-중복 방어 = 등가성 테스트 (PLAN-0012-B, ADR-0005 §5.1 "Post 적용").
 *
 * <p>경계 {@code @Size} 와 VO 는 길이 비교를 *독립 구현*(상수만 공유)하므로, 둘이 *같은 임계*에서
 * 함께 통과/거부하는지 고정한다. title·body 각각, Create·Update 두 DTO 각각에 대해 세 divergence 벡터
 * (경계값·trim·codePoint)를 자극 — 어느 한 필드가 *잘못된 상수*를 참조하거나 VO 가
 * {@code >=}/{@code codePointCount}/{@code trim} 으로 갈라지면 깨진다. (의도 semantics: Java {@code char}
 * 수({@code String.length()}) 기준, trim 안 함 — {@code @Size} 와 동일.)
 */
class PostLengthContractTest {

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
    @DisplayName("title 길이 — 경계 @Size(Create/Update)와 VO 가 같은 임계에서 통과/거부 일치")
    void title_boundary_and_vo_agree() {
        int max = PostContent.MAX_TITLE_LENGTH;
        assertTitleAgrees("a".repeat(max), true);               // 경계: MAX 통과
        assertTitleAgrees("a".repeat(max + 1), false);          // 경계: MAX+1 거부
        assertTitleAgrees(" " + "a".repeat(max) + " ", false);  // trim: raw MAX+2 (trim 하면 통과→불일치)
        assertTitleAgrees("😀".repeat(max), false);             // codePoint: char 2*MAX (codePoint면 통과→불일치)
    }

    @Test
    @DisplayName("body 길이 — 경계 @Size(Create/Update)와 VO 가 같은 임계에서 통과/거부 일치")
    void body_boundary_and_vo_agree() {
        int max = PostContent.MAX_BODY_LENGTH;
        assertBodyAgrees("b".repeat(max), true);
        assertBodyAgrees("b".repeat(max + 1), false);
        assertBodyAgrees(" " + "b".repeat(max) + " ", false);
        assertBodyAgrees("😀".repeat(max), false);
    }

    private void assertTitleAgrees(String title, boolean expectValid) {
        assertThat(noViolationOn(validator.validate(new CreatePostRequest(title, "b")), "title"))
                .as("CreatePostRequest title len=%d", title.length()).isEqualTo(expectValid);
        assertThat(noViolationOn(validator.validate(new UpdatePostRequest(title, "b")), "title"))
                .as("UpdatePostRequest title len=%d", title.length()).isEqualTo(expectValid);
        assertVoAgrees(() -> new PostContent(title, "b"), expectValid, "title len=" + title.length());
    }

    private void assertBodyAgrees(String body, boolean expectValid) {
        assertThat(noViolationOn(validator.validate(new CreatePostRequest("t", body)), "body"))
                .as("CreatePostRequest body len=%d", body.length()).isEqualTo(expectValid);
        assertThat(noViolationOn(validator.validate(new UpdatePostRequest("t", body)), "body"))
                .as("UpdatePostRequest body len=%d", body.length()).isEqualTo(expectValid);
        assertVoAgrees(() -> new PostContent("t", body), expectValid, "body len=" + body.length());
    }

    private static void assertVoAgrees(ThrowingCallable vo, boolean expectValid, String desc) {
        if (expectValid) {
            assertThatCode(vo).as("VO %s", desc).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(vo).as("VO %s", desc).isInstanceOf(InvalidPostContentException.class);
        }
    }

    private static boolean noViolationOn(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals(field));
    }
}
