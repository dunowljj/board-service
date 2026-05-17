package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.SavePostPort;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import com.dunowljj.board.domain.post.PostFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.dunowljj.board.domain.post.PostFixtures.FIXED_NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    @Mock LoadPostPort loadPostPort;
    @Mock SavePostPort savePostPort;
    @Mock DeletePostPort deletePostPort;

    PostCommandService sut;

    @BeforeEach
    void setUp() {
        sut = new PostCommandService(PostFixtures.fixedClock(), loadPostPort, savePostPort, deletePostPort);
    }

    @Test
    @DisplayName("게시글을 등록하면 입력값으로 채워진 Post 가 저장 경계로 전달된다")
    void create_passes_post_with_expected_state_to_save_port() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        when(savePostPort.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        sut.create(new CreatePostUseCase.CreatePostCommand("t", "b", "a"));

        Post saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getBody()).isEqualTo("b");
        assertThat(saved.getAuthor()).isEqualTo("a");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("게시글을 수정하면 동일 인스턴스의 갱신된 상태가 저장 경계로 전달된다")
    void update_passes_mutated_post_to_save_port() {
        // existing 의 createdAt/updatedAt 을 과거로 설정 — Application 이 캡처하는 FIXED_NOW 가
        // 역행 금지 invariant 통과 + advances 의미 보존 (PLAN-0007 Risks #2).
        LocalDateTime past = FIXED_NOW.minusDays(1);
        Post existing = PostFixtures.aReconstitutedPost(42L, past, past);
        String originalAuthor = existing.getAuthor();
        when(loadPostPort.findById(42L)).thenReturn(Optional.of(existing));
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        when(savePostPort.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(new UpdatePostUseCase.UpdatePostCommand(42L, "t2", "b2"));

        Post saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getTitle()).isEqualTo("t2");
        assertThat(saved.getBody()).isEqualTo("b2");
        assertThat(saved.getAuthor()).isEqualTo(originalAuthor);
        assertThat(saved.getCreatedAt()).isEqualTo(past);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("존재하지 않는 게시글 수정은 예외를 던지고 저장하지 않는다")
    void update_throws_and_does_not_save_when_not_found() {
        when(loadPostPort.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.update(new UpdatePostUseCase.UpdatePostCommand(1L, "t", "b")))
                .isInstanceOf(PostNotFoundException.class);

        verify(savePostPort, never()).save(any());
    }

    @Test
    @DisplayName("게시글을 삭제하면 삭제 경계가 호출되고 저장은 일어나지 않는다")
    void delete_invokes_delete_port_and_does_not_save_when_row_exists() {
        when(deletePostPort.deleteById(5L)).thenReturn(1);

        sut.delete(5L);

        verify(deletePostPort).deleteById(5L);
        verify(savePostPort, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 삭제는 예외를 던지고 저장하지 않는다")
    void delete_throws_and_does_not_save_when_not_found() {
        when(deletePostPort.deleteById(9L)).thenReturn(0);

        assertThatThrownBy(() -> sut.delete(9L))
                .isInstanceOf(PostNotFoundException.class);

        verify(savePostPort, never()).save(any());
    }
}
