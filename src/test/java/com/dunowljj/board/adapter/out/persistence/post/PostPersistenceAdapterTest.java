package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.config.MutableClock;
import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.dunowljj.board.config.TestAuditConfig;
import com.dunowljj.board.config.TimeConfig;
import com.dunowljj.board.domain.post.Post;
import com.dunowljj.board.domain.post.PostFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.dunowljj.board.domain.post.PostFixtures.FIXED_NOW;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PostPersistenceAdapter.class, TimeConfig.class, TestAuditConfig.class, PostgresTestcontainersConfig.class})
class PostPersistenceAdapterTest {

    @Autowired
    PostPersistenceAdapter adapter;

    @Autowired
    MutableClock clock;

    /**
     * MutableClock 은 Spring context 의 단일 빈 인스턴스로 모든 테스트에 공유된다.
     * {@code @DataJpaTest} 의 DB rollback 은 *bean 상태* 를 되돌리지 않으므로,
     * 이전 테스트의 {@code advance}/{@code setTo} 가 다음 테스트로 누수되지 않게
     * 매 테스트 진입 시 초기값으로 명시 리셋 (ADR-0006 §5 의 결정성 원칙).
     */
    @BeforeEach
    void resetClock() {
        clock.setTo(PostFixtures.FIXED_NOW);
    }

    @Test
    @DisplayName("새 게시글을 저장하면 id 가 부여되고 audit listener 가 createdAt/updatedAt 을 FIXED_NOW 로 채운다")
    void create_assigns_id_and_audit_listener_fills_timestamps() {
        AuditedPost saved = adapter.create(Post.create("t", "b", "a"));

        assertThat(saved.post().getId()).isNotNull();
        assertThat(saved.post().getTitle()).isEqualTo("t");
        assertThat(saved.post().getBody()).isEqualTo("b");
        assertThat(saved.post().getAuthor()).isEqualTo("a");
        assertThat(saved.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);

        Optional<AuditedPost> found = adapter.findById(saved.post().getId());
        assertThat(found).isPresent();
        assertThat(found.get().post().getTitle()).isEqualTo("t");
        assertThat(found.get().createdAt()).isEqualTo(FIXED_NOW);
        assertThat(found.get().updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("기존 게시글을 갱신하면 동일 id 로 갱신되고 createdAt 은 보존되며 updatedAt 은 새 시점으로 진행한다")
    void update_keeps_createdAt_and_advances_updatedAt() {
        AuditedPost created = adapter.create(Post.create("old-title", "old-body", "a"));
        Long id = created.post().getId();
        LocalDateTime createdAt = created.createdAt();

        clock.advance(Duration.ofMinutes(1));
        LocalDateTime later = LocalDateTime.now(clock);

        AuditedPost updated = adapter.update(Post.reconstitute(id, "new-title", "new-body", "a"));

        assertThat(updated.post().getId()).isEqualTo(id);
        assertThat(updated.post().getTitle()).isEqualTo("new-title");
        assertThat(updated.post().getBody()).isEqualTo("new-body");
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("동일 내용 update 시 dirty 가 없어 updatedAt 이 변경되지 않는다 (no-op)")
    void update_with_same_content_keeps_updatedAt() {
        AuditedPost created = adapter.create(Post.create("t", "b", "a"));
        Long id = created.post().getId();
        LocalDateTime originalUpdatedAt = created.updatedAt();

        clock.advance(Duration.ofMinutes(1));

        AuditedPost updated = adapter.update(Post.reconstitute(id, "t", "b", "a"));

        assertThat(updated.updatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("존재하지 않는 id 로 조회하면 빈 Optional 을 돌려준다")
    void findById_returns_empty_when_not_found() {
        Optional<AuditedPost> found = adapter.findById(9_999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findPage 는 createdAt 내림차순 + id 내림차순 tie-breaker 로 첫 페이지를 돌려준다")
    void findPage_returns_first_page_ordered_by_createdAt_desc_id_desc() {
        clock.setTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        adapter.create(Post.create("old", "b", "a"));
        clock.setTo(LocalDateTime.of(2026, 1, 2, 0, 0));
        adapter.create(Post.create("mid", "b", "a"));
        clock.setTo(LocalDateTime.of(2026, 1, 3, 0, 0));
        adapter.create(Post.create("new", "b", "a"));

        PostPage page = adapter.findPage(0, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(audited -> audited.post().getTitle())
                .containsExactly("new", "mid");
    }

    @Test
    @DisplayName("findPage 두 번째 페이지는 남은 항목과 동일한 totalElements 를 돌려준다")
    void findPage_returns_second_page_with_remaining_items() {
        clock.setTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        adapter.create(Post.create("old", "b", "a"));
        clock.setTo(LocalDateTime.of(2026, 1, 2, 0, 0));
        adapter.create(Post.create("mid", "b", "a"));
        clock.setTo(LocalDateTime.of(2026, 1, 3, 0, 0));
        adapter.create(Post.create("new", "b", "a"));

        PostPage page = adapter.findPage(1, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items()).extracting(audited -> audited.post().getTitle())
                .containsExactly("old");
    }

    @Test
    @DisplayName("데이터가 없으면 findPage 는 빈 리스트와 totalElements 0 을 돌려준다")
    void findPage_returns_empty_when_no_data() {
        PostPage page = adapter.findPage(0, 10);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("존재하는 id 를 삭제하면 row-count 1 을 돌려준다")
    void deleteById_returns_one_when_row_exists() {
        AuditedPost created = adapter.create(Post.create("t", "b", "a"));
        Long id = created.post().getId();

        int affected = adapter.deleteById(id);

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 id 를 삭제하면 row-count 0 을 돌려준다")
    void deleteById_returns_zero_when_not_found() {
        int affected = adapter.deleteById(9_999L);

        assertThat(affected).isZero();
    }
}
