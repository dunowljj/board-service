package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.dunowljj.board.domain.post.PostFixtures.FIXED_NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    @Test
    @DisplayName("게시글을 생성하면 createdAt 과 updatedAt 이 인자로 받은 시각으로 채워진다")
    void create_sets_createdAt_and_updatedAt_to_provided_now() {
        Post post = Post.create(FIXED_NOW, "t", "b", "a");

        assertThat(post.getId()).isNull();
        assertThat(post.getTitle()).isEqualTo("t");
        assertThat(post.getBody()).isEqualTo("b");
        assertThat(post.getAuthor()).isEqualTo("a");
        assertThat(post.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(post.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("생성 시 now 가 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_now_is_null() {
        assertThatThrownBy(() -> Post.create(null, "t", "b", "a"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("작성자가 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_author_is_null() {
        assertThatThrownBy(() -> Post.create(FIXED_NOW, "t", "b", null))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("작성자가 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_author_is_blank() {
        assertThatThrownBy(() -> Post.create(FIXED_NOW, "t", "b", "   "))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.create(FIXED_NOW, null, "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("제목이 공백이면 게시글을 생성할 수 없다")
    void create_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.create(FIXED_NOW, "   ", "b", "a"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("본문이 null 이면 게시글을 생성할 수 없다")
    void create_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.create(FIXED_NOW, "t", null, "a"))
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
        assertThatThrownBy(() -> Post.reconstitute(null, "t", "b", "a", FIXED_NOW, FIXED_NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 createdAt 이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_createdAt_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "a", null, FIXED_NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 updatedAt 이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_updatedAt_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "a", FIXED_NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", null, FIXED_NOW, FIXED_NOW))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 작성자가 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_author_is_blank() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", "b", "  ", FIXED_NOW, FIXED_NOW))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, null, "b", "a", FIXED_NOW, FIXED_NOW))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 제목이 공백이면 재구성할 수 없다")
    void reconstitute_throws_when_title_is_blank() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "  ", "b", "a", FIXED_NOW, FIXED_NOW))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("재구성 시 본문이 null 이면 재구성할 수 없다")
    void reconstitute_throws_when_body_is_null() {
        assertThatThrownBy(() -> Post.reconstitute(1L, "t", null, "a", FIXED_NOW, FIXED_NOW))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신하면 새 제목/본문이 반영되고 updatedAt 이 인자로 받은 시각으로 진행한다")
    void updateContent_replaces_content_and_sets_updatedAt_to_provided_now() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime created = post.getCreatedAt();
        LocalDateTime later = FIXED_NOW.plusMinutes(1);

        post.updateContent(later, "t2", "b2");

        assertThat(post.getTitle()).isEqualTo("t2");
        assertThat(post.getBody()).isEqualTo("b2");
        assertThat(post.getCreatedAt()).isEqualTo(created);
        assertThat(post.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("내용 갱신 시 now 와 현재 updatedAt 이 같으면 허용된다 (경계)")
    void updateContent_accepts_when_now_equals_current_updatedAt() {
        Post post = PostFixtures.aValidPost();

        post.updateContent(FIXED_NOW, "t2", "b2");

        assertThat(post.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("내용 갱신 시 now 가 null 이면 갱신할 수 없다")
    void updateContent_throws_when_now_is_null() {
        Post post = PostFixtures.aValidPost();

        assertThatThrownBy(() -> post.updateContent(null, "t2", "b2"))
                .isInstanceOf(NullPointerException.class);
        // mutation 보호 — 검증 실패 시 상태 불변
        assertThat(post.getTitle()).isEqualTo("title");
        assertThat(post.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("내용 갱신 시 now 가 현재 updatedAt 보다 과거이면 갱신할 수 없다 (역행 금지)")
    void updateContent_throws_when_now_is_before_current_updatedAt() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime earlier = FIXED_NOW.minusMinutes(1);

        assertThatThrownBy(() -> post.updateContent(earlier, "t2", "b2"))
                .isInstanceOf(IllegalArgumentException.class);
        // mutation 보호 — 검증 실패 시 상태 불변
        assertThat(post.getTitle()).isEqualTo("title");
        assertThat(post.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("내용을 갱신할 때 제목이 null 이면 갱신할 수 없다")
    void updateContent_throws_when_title_is_null() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime later = FIXED_NOW.plusMinutes(1);

        assertThatThrownBy(() -> post.updateContent(later, null, "b2"))
                .isInstanceOf(InvalidPostContentException.class);
        // mutation 보호 — 검증 실패 시 상태 불변
        assertThat(post.getTitle()).isEqualTo("title");
        assertThat(post.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("내용을 갱신할 때 제목이 공백이면 갱신할 수 없다")
    void updateContent_throws_when_title_is_blank() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime later = FIXED_NOW.plusMinutes(1);

        assertThatThrownBy(() -> post.updateContent(later, "  ", "b2"))
                .isInstanceOf(InvalidPostContentException.class);
    }

    @Test
    @DisplayName("내용을 갱신할 때 본문이 null 이면 갱신할 수 없다")
    void updateContent_throws_when_body_is_null() {
        Post post = PostFixtures.aValidPost();
        LocalDateTime later = FIXED_NOW.plusMinutes(1);

        assertThatThrownBy(() -> post.updateContent(later, "t2", null))
                .isInstanceOf(InvalidPostContentException.class);
    }
}
