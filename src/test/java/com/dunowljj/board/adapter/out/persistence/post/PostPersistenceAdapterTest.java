package com.dunowljj.board.adapter.out.persistence.post;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.domain.post.Post;
import com.dunowljj.board.domain.post.PostFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.dunowljj.board.domain.post.PostFixtures.FIXED_NOW;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostPersistenceAdapter.class)
class PostPersistenceAdapterTest {

    @Autowired
    PostPersistenceAdapter adapter;

    @Autowired
    PostJpaRepository repository;

    @PersistenceContext
    EntityManager em;

    @Test
    @DisplayName("새 게시글을 저장하면 id 가 부여되고 createdAt/updatedAt 이 보존되어 다시 조회된다")
    void save_assigns_id_and_findById_returns_same_post() {
        Post saved = adapter.save(Post.create(FIXED_NOW, "t", "b", "a"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getBody()).isEqualTo("b");
        assertThat(saved.getAuthor()).isEqualTo("a");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);

        // 1차 캐시 우회: findById 가 실제 DB SELECT 를 거치도록 강제 (round-trip 검증)
        em.flush();
        em.clear();
        Optional<Post> found = adapter.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getTitle()).isEqualTo("t");
        assertThat(found.get().getBody()).isEqualTo("b");
        assertThat(found.get().getAuthor()).isEqualTo("a");
        assertThat(found.get().getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(found.get().getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("기존 id 를 가진 도메인을 저장하면 동일 id 로 업데이트되어 row 수가 유지된다")
    void save_updates_in_place_when_id_present() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        PostJpaEntity persisted = repository.save(
                new PostJpaEntity(null, "old", "ob", "oa", t1, t1));
        Long id = persisted.getId();
        long countBefore = repository.count();

        LocalDateTime t2 = LocalDateTime.of(2026, 1, 2, 0, 0);
        Post toUpdate = Post.reconstitute(id, "new", "nb", "oa", t1, t2);
        Post saved = adapter.save(toUpdate);

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getTitle()).isEqualTo("new");
        assertThat(saved.getBody()).isEqualTo("nb");
        assertThat(saved.getUpdatedAt()).isEqualTo(t2);
        assertThat(repository.count()).isEqualTo(countBefore);

        // 1차 캐시 우회: merge 가 발행한 UPDATE 가 실제로 flush 되었는지 DB SELECT 로 검증.
        em.flush();
        em.clear();
        Optional<Post> reloaded = adapter.findById(id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getTitle()).isEqualTo("new");
        assertThat(reloaded.get().getBody()).isEqualTo("nb");
        assertThat(reloaded.get().getCreatedAt()).isEqualTo(t1);
        assertThat(reloaded.get().getUpdatedAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("존재하지 않는 id 로 조회하면 빈 Optional 을 돌려준다")
    void findById_returns_empty_when_not_found() {
        Optional<Post> found = adapter.findById(9_999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findPage 는 createdAt 내림차순으로 첫 페이지 항목과 totalElements 를 돌려준다")
    void findPage_returns_first_page_ordered_by_createdAt_desc() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime mid = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 1, 3, 0, 0);
        repository.save(new PostJpaEntity(null, "old", "b", "a", older, older));
        repository.save(new PostJpaEntity(null, "mid", "b", "a", mid, mid));
        repository.save(new PostJpaEntity(null, "new", "b", "a", newer, newer));

        PostPage page = adapter.findPage(0, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(Post::getTitle).containsExactly("new", "mid");
    }

    @Test
    @DisplayName("findPage 두 번째 페이지는 남은 항목과 동일한 totalElements 를 돌려준다")
    void findPage_returns_second_page_with_remaining_items() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime mid = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 1, 3, 0, 0);
        repository.save(new PostJpaEntity(null, "old", "b", "a", older, older));
        repository.save(new PostJpaEntity(null, "mid", "b", "a", mid, mid));
        repository.save(new PostJpaEntity(null, "new", "b", "a", newer, newer));

        PostPage page = adapter.findPage(1, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items()).extracting(Post::getTitle).containsExactly("old");
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
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        PostJpaEntity persisted = repository.save(
                new PostJpaEntity(null, "t", "b", "a", now, now));
        Long id = persisted.getId();

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
