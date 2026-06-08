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
        Post post = Post.create("t", "b", 7L);

        assertThat(post.getId()).isNull();
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("작성자 id 가 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_authorId_is_null() {
        assertThatThrownBy(() -> Post.create("t", "b", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.create(null, "b", 1L))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.create("   ", "b", 1L))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("본문이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.create("t", null, 1L))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성하면 입력한 식별자와 필드가 그대로 유지된다")
    void reconstitute_preserves_inputs() {
        Post post = Post.reconstitute(7L, "t", "b", 9L);

        assertThat(post.getId()).isEqualTo(7L);
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthorId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("재구성 시 식별자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_id_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(null, "t", "b", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자 id 가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_authorId_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, null, "b", 1L))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "  ", "b", 1L))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 본문이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", null, 1L))
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
}
