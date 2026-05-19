package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostContentTest {

    @Test
    @DisplayName("제목과 본문이 모두 유효하면 PostContent 를 생성할 수 있다")
    void constructor_accepts_valid_title_and_body() {
        PostContent content = new PostContent("title", "body");

        assertThat(content.getTitle()).isEqualTo("title");
        assertThat(content.getBody()).isEqualTo("body");
    }

    @Test
    @DisplayName("본문은 빈 문자열을 허용한다")
    void constructor_allows_empty_body() {
        PostContent content = new PostContent("title", "");

        assertThat(content.getBody()).isEmpty();
    }

    @Test
    @DisplayName("제목이 null 이면 PostContent 를 생성할 수 없다")
    void constructor_throws_when_title_is_null() {
        assertThatThrownBy(() -> new PostContent(null, "body"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 공백이면 PostContent 를 생성할 수 없다")
    void constructor_throws_when_title_is_blank() {
        assertThatThrownBy(() -> new PostContent("   ", "body"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("본문이 null 이면 PostContent 를 생성할 수 없다")
    void constructor_throws_when_body_is_null() {
        assertThatThrownBy(() -> new PostContent("title", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목과 본문이 같으면 같은 PostContent 로 본다")
    void equals_returns_true_when_title_and_body_are_same() {
        PostContent a = new PostContent("title", "body");
        PostContent b = new PostContent("title", "body");

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("equals 와 hashCode 는 title·body 기준 계약을 만족한다")
    void equals_and_hashCode_contract() {
        EqualsVerifier.forClass(PostContent.class)
                .withOnlyTheseFields("title", "body")
                .verify();
    }
}
