package com.dunowljj.board.application.service;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.in.result.PostListResult;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import com.dunowljj.board.domain.post.PostFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

    @Mock LoadPostPort loadPostPort;

    PostQueryService sut;

    @BeforeEach
    void setUp() {
        sut = new PostQueryService(loadPostPort);
    }

    @Test
    @DisplayName("식별자로 게시글을 조회하면 Port 가 반환한 Post 를 그대로 돌려준다")
    void getById_returns_post_from_port() {
        Post post = PostFixtures.aReconstitutedPost(3L);
        when(loadPostPort.findById(3L)).thenReturn(Optional.of(post));

        Post result = sut.getById(3L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 예외를 던진다")
    void getById_throws_when_not_found() {
        when(loadPostPort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getById(99L))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("페이지 조회 결과의 totalPages 는 총 개수를 페이지 크기로 나눈 몫이다")
    void list_computes_total_pages_for_exact_division() {
        Post p = PostFixtures.aReconstitutedPost(1L);
        when(loadPostPort.findPage(0, 5)).thenReturn(new PostPage(List.of(p), 10L));

        PostListResult result = sut.list(0, 5);

        assertThat(result.posts()).containsExactly(p);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(10L);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("총 개수가 페이지 크기로 나누어 떨어지지 않으면 totalPages 는 올림 값이다")
    void list_computes_total_pages_with_ceil_division() {
        when(loadPostPort.findPage(0, 3)).thenReturn(new PostPage(List.of(), 10L));

        PostListResult result = sut.list(0, 3);

        assertThat(result.totalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("페이지 크기가 0 이하이면 totalPages 는 0 이다")
    void list_returns_zero_total_pages_when_size_is_non_positive() {
        when(loadPostPort.findPage(0, 0)).thenReturn(new PostPage(List.of(), 0L));

        PostListResult result = sut.list(0, 0);

        assertThat(result.size()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("총 개수가 0 이면 빈 리스트와 함께 totalPages 는 0 이다")
    void list_returns_empty_posts_and_zero_total_pages_when_total_is_zero() {
        when(loadPostPort.findPage(0, 10)).thenReturn(new PostPage(List.of(), 0L));

        PostListResult result = sut.list(0, 10);

        assertThat(result.posts()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
