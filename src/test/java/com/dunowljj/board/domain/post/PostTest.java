package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    @Test
    @DisplayName("게시글을 생성하면 createdAt 과 updatedAt 이 동일하게 현재 시각으로 채워진다")
    void create_sets_createdAt_equal_to_updatedAt_within_now_range() {
        LocalDateTime before = LocalDateTime.now();
        Post post = Post.create("t", "b", "a");
        LocalDateTime after = LocalDateTime.now();

        assertThat(post.getId()).isNull();
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthor()).isEqualTo("a");
        assertThat(post.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(post.getUpdatedAt()).isEqualTo(post.getCreatedAt());
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
    @DisplayName("재구성하면 입력한 식별자와 시각이 그대로 유지된다")
    void reconstitute_preserves_inputs() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 1, 2, 0, 0);

        Post post = Post.reconstitute(7L, "t", "b", "a", created, updated);

        assertThat(post.getId()).isEqualTo(7L);
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthor()).isEqualTo("a");
        assertThat(post.getCreatedAt()).isEqualTo(created);
        assertThat(post.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("재구성 시 식별자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_id_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(null, "t", "b", "a", now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 createdAt 이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_createdAt_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "a", null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 updatedAt 이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_updatedAt_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "a", now, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", null, now, now))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_blank() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "  ", now, now))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, null, "b", "a", now, now))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_blank() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "  ", "b", "a", now, now))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 본문이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_body_is_null() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", null, "a", now, now))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신하면 새 제목/본문이 반영되고 updatedAt 은 createdAt 이상으로 진행한다")
    void updateContent_replaces_content_and_advances_updatedAt() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime created = post.getCreatedAt();

        post.updateContent("t2", "b2");

        assertThat(post.getTitle()).isEqualTo("t2");
        assertThat(post.getBody()).isEqualTo("b2");
        assertThat(post.getCreatedAt()).isEqualTo(created);
        assertThat(post.getUpdatedAt()).isAfterOrEqualTo(created);
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
