package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    @Test
    @DisplayName("게시글을 생성하면 입력한 필드 값이 그대로 채워진다")
    void create_sets_provided_fields() {
        Post post = Post.create("t", "b", "a");

        assertThat(post.getId()).isNull();
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthor()).isEqualTo("a");
    }

    @Test
    @DisplayName("작성자가 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_author_is_null() {
        assertThatThrownBy(() -> Post.create("t", "b", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("작성자가 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_author_is_blank() {
        assertThatThrownBy(() -> Post.create("t", "b", "   "))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.create(null, "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.create("   ", "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("본문이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.create("t", null, "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성하면 입력한 식별자와 필드가 그대로 유지된다")
    void reconstitute_preserves_inputs() {
        Post post = Post.reconstitute(7L, "t", "b", "a");

        assertThat(post.getId()).isEqualTo(7L);
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthor()).isEqualTo("a");
    }

    @Test
    @DisplayName("재구성 시 식별자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_id_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(null, "t", "b", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_blank() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "  "))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, null, "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "  ", "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 본문이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", null, "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신하면 새 제목/본문이 반영된다")
    void updateContent_replaces_content() {
        Post post = PostFixtures.aValidPost();

        post.updateContent("t2", "b2");

        assertThat(post.getTitle()).isEqualTo("t2");
        assertThat(post.getBody()).isEqualTo("b2");
    }

    @Test
    @DisplayName("내용을 갱신할 때 제목이 null 이면 갱신할 수 없다")
    void updateContent_throws_when_title_is_null() {
        Post post = PostFixtures.aValidPost();

        assertThatThrownBy(() -> post.updateContent(null, "b2"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신할 때 제목이 공백이면 갱신할 수 없다")
    void updateContent_throws_when_title_is_blank() {
        Post post = PostFixtures.aValidPost();

        assertThatThrownBy(() -> post.updateContent("  ", "b2"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신할 때 본문이 null 이면 갱신할 수 없다")
    void updateContent_throws_when_body_is_null() {
        Post post = PostFixtures.aValidPost();

        assertThatThrownBy(() -> post.updateContent("t2", null))
                .isInstanceOf(InvalidPostContentException.class);
    }
}
