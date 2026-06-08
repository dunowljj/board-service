package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.DeletePostUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.in.result.AuditedPostResult;
import com.dunowljj.board.application.port.out.CreatePostPort;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.UpdatePostPort;
import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.common.error.NotPostOwnerException;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.dunowljj.board.domain.post.PostFixtures.DEFAULT_AUTHOR_ID;
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
    @Mock CreatePostPort createPostPort;
    @Mock UpdatePostPort updatePostPort;
    @Mock DeletePostPort deletePostPort;

    PostCommandService sut;

    @BeforeEach
    void setUp() {
        sut = new PostCommandService(loadPostPort, createPostPort, updatePostPort, deletePostPort);
    }

    @Test
    @DisplayName("게시글을 등록하면 입력값으로 채워진 Post 가 create 경계로 전달되고 audit 결과가 반환된다")
    void create_passes_post_with_expected_state_to_create_port() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        when(createPostPort.create(captor.capture()))
                .thenAnswer(inv -> new AuditedPost(inv.getArgument(0), "관리자", FIXED_NOW, FIXED_NOW));

        AuditedPostResult result = sut.create(new CreatePostUseCase.CreatePostCommand("t", "b", DEFAULT_AUTHOR_ID));

        Post saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getBody()).isEqualTo("b");
        assertThat(saved.getAuthorId()).isEqualTo(DEFAULT_AUTHOR_ID);

        assertThat(result.title()).isEqualTo("t");
        assertThat(result.body()).isEqualTo("b");
        assertThat(result.authorId()).isEqualTo(DEFAULT_AUTHOR_ID);
        assertThat(result.authorNickname()).isEqualTo("관리자");
        assertThat(result.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("본인 글을 수정하면 동일 인스턴스의 갱신된 상태가 update 경계로 전달된다")
    void update_passes_mutated_post_to_update_port() {
        LocalDateTime past = FIXED_NOW.minusDays(1);
        Post existing = Post.reconstitute(42L, "title", "body", DEFAULT_AUTHOR_ID);
        when(loadPostPort.findById(42L))
                .thenReturn(Optional.of(new AuditedPost(existing, "관리자", past, past)));
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        when(updatePostPort.update(captor.capture()))
                .thenAnswer(inv -> new AuditedPost(inv.getArgument(0), "관리자", past, FIXED_NOW));

        AuditedPostResult result = sut.update(
                new UpdatePostUseCase.UpdatePostCommand(42L, "t2", "b2", DEFAULT_AUTHOR_ID));

        Post saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getTitle()).isEqualTo("t2");
        assertThat(saved.getBody()).isEqualTo("b2");
        assertThat(saved.getAuthorId()).isEqualTo(DEFAULT_AUTHOR_ID);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.title()).isEqualTo("t2");
        assertThat(result.body()).isEqualTo("b2");
        assertThat(result.createdAt()).isEqualTo(past);
        assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("타인 글 수정은 NotPostOwnerException 을 던지고 update 경계는 호출되지 않는다")
    void update_throws_when_not_owner() {
        Post existing = Post.reconstitute(42L, "t", "b", 100L);
        when(loadPostPort.findById(42L))
                .thenReturn(Optional.of(new AuditedPost(existing, "다른사람", FIXED_NOW, FIXED_NOW)));

        assertThatThrownBy(() -> sut.update(
                new UpdatePostUseCase.UpdatePostCommand(42L, "t2", "b2", 99L)))
                .isInstanceOf(NotPostOwnerException.class);

        verify(updatePostPort, never()).update(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 수정은 예외를 던지고 update 경계는 호출되지 않는다")
    void update_throws_and_does_not_save_when_not_found() {
        when(loadPostPort.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.update(
                new UpdatePostUseCase.UpdatePostCommand(1L, "t", "b", DEFAULT_AUTHOR_ID)))
                .isInstanceOf(PostNotFoundException.class);

        verify(updatePostPort, never()).update(any());
    }

    @Test
    @DisplayName("본인 글을 삭제하면 삭제 경계가 호출되고 update 는 일어나지 않는다")
    void delete_invokes_delete_port_and_does_not_save_when_row_exists() {
        Post existing = Post.reconstitute(5L, "t", "b", DEFAULT_AUTHOR_ID);
        when(loadPostPort.findById(5L))
                .thenReturn(Optional.of(new AuditedPost(existing, "관리자", FIXED_NOW, FIXED_NOW)));
        when(deletePostPort.deleteById(5L)).thenReturn(1);

        sut.delete(new DeletePostUseCase.DeletePostCommand(5L, DEFAULT_AUTHOR_ID));

        verify(deletePostPort).deleteById(5L);
        verify(updatePostPort, never()).update(any());
        verify(createPostPort, never()).create(any());
    }

    @Test
    @DisplayName("타인 글 삭제는 NotPostOwnerException 을 던지고 deleteById 는 호출되지 않는다")
    void delete_throws_when_not_owner() {
        Post existing = Post.reconstitute(5L, "t", "b", 100L);
        when(loadPostPort.findById(5L))
                .thenReturn(Optional.of(new AuditedPost(existing, "다른사람", FIXED_NOW, FIXED_NOW)));

        assertThatThrownBy(() -> sut.delete(new DeletePostUseCase.DeletePostCommand(5L, 99L)))
                .isInstanceOf(NotPostOwnerException.class);

        verify(deletePostPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 삭제는 예외를 던지고 deleteById 는 호출되지 않는다")
    void delete_throws_when_not_found() {
        when(loadPostPort.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.delete(new DeletePostUseCase.DeletePostCommand(9L, DEFAULT_AUTHOR_ID)))
                .isInstanceOf(PostNotFoundException.class);

        verify(deletePostPort, never()).deleteById(any());
    }
}
