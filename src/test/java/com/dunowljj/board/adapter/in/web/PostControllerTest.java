package com.dunowljj.board.adapter.in.web;

import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.DeletePostUseCase;
import com.dunowljj.board.application.port.in.GetPostUseCase;
import com.dunowljj.board.application.port.in.ListPostsUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.in.result.PostListResult;
import com.dunowljj.board.common.error.InvalidPostContentException;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreatePostUseCase createPostUseCase;
    @MockitoBean
    GetPostUseCase getPostUseCase;
    @MockitoBean
    UpdatePostUseCase updatePostUseCase;
    @MockitoBean
    DeletePostUseCase deletePostUseCase;
    @MockitoBean
    ListPostsUseCase listPostsUseCase;

    // ============ create ============

    @Test
    @DisplayName("게시글을 등록하면 201 과 PostResponse 본문을 돌려준다")
    void create_returns_201_with_response_body() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post fixture = Post.reconstitute(1L, "title", "body", "author", now, now);
        when(createPostUseCase.create(any())).thenReturn(fixture);

        mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"title","body":"body","author":"author"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("title"))
                .andExpect(jsonPath("$.body").value("body"))
                .andExpect(jsonPath("$.author").value("author"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()));
    }

    @Test
    @DisplayName("작성자가 비어 있으면 400 과 VALIDATION_FAILED 를 돌려준다")
    void create_returns_400_when_author_blank() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":"b","author":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value(notNullValue()))
                .andExpect(jsonPath("$.detail").value(notNullValue()))
                .andExpect(jsonPath("$.instance").value("/api/posts"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("author")))
                .andExpect(jsonPath("$.errors[*].reason").value(notNullValue()));
    }

    @Test
    @DisplayName("제목이 비어 있으면 400 과 VALIDATION_FAILED 를 돌려준다")
    void create_returns_400_when_title_blank() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"","body":"b","author":"a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")));
    }

    @Test
    @DisplayName("body 가 null 이면 400 과 VALIDATION_FAILED 를 돌려준다")
    void create_returns_400_when_body_null() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":null,"author":"a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("body")));
    }

    @Test
    @DisplayName("요청 본문이 깨진 JSON 이면 400 과 MALFORMED_REQUEST 를 돌려준다")
    void create_returns_400_when_body_malformed_json() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
    }

    // ============ getById ============

    @Test
    @DisplayName("게시글을 조회하면 200 과 PostResponse 본문을 돌려준다")
    void getById_returns_200_with_response_body() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post fixture = Post.reconstitute(7L, "title", "body", "author", now, now);
        when(getPostUseCase.getById(7L)).thenReturn(fixture);

        mockMvc.perform(get("/api/posts/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("title"))
                .andExpect(jsonPath("$.author").value("author"));
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 404 와 POST_NOT_FOUND 를 돌려준다")
    void getById_returns_404_when_not_found() throws Exception {
        when(getPostUseCase.getById(99L)).thenThrow(new PostNotFoundException(99L));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/posts/99"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
    }

    // ============ update ============

    @Test
    @DisplayName("게시글을 수정하면 200 과 갱신된 PostResponse 본문을 돌려준다")
    void update_returns_200_with_response_body() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post fixture = Post.reconstitute(3L, "newTitle", "newBody", "author", now, now);
        when(updatePostUseCase.update(any())).thenReturn(fixture);

        mockMvc.perform(put("/api/posts/3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"newTitle","body":"newBody"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("newTitle"))
                .andExpect(jsonPath("$.body").value("newBody"));
    }

    @Test
    @DisplayName("수정 시 제목이 비어 있으면 400 과 VALIDATION_FAILED 를 돌려준다")
    void update_returns_400_when_title_blank() throws Exception {
        mockMvc.perform(put("/api/posts/3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"","body":"b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")));
    }

    @Test
    @DisplayName("수정 대상이 도메인 검증을 어기면 400 과 INVALID_POST_CONTENT 를 돌려준다")
    void update_returns_400_when_domain_rejects_content() throws Exception {
        when(updatePostUseCase.update(any())).thenThrow(new InvalidPostContentException("title"));

        mockMvc.perform(put("/api/posts/3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":"b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POST_CONTENT"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("수정 시 대상이 존재하지 않으면 404 와 POST_NOT_FOUND 를 돌려준다")
    void update_returns_404_when_not_found() throws Exception {
        when(updatePostUseCase.update(any())).thenThrow(new PostNotFoundException(3L));

        mockMvc.perform(put("/api/posts/3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":"b"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ============ delete ============

    @Test
    @DisplayName("게시글을 삭제하면 204 와 빈 본문을 돌려준다")
    void delete_returns_204_with_empty_body() throws Exception {
        mockMvc.perform(delete("/api/posts/5"))
                .andExpect(status().isNoContent());

        verify(deletePostUseCase).delete(5L);
    }

    @Test
    @DisplayName("삭제 시 대상이 존재하지 않으면 404 와 POST_NOT_FOUND 를 돌려준다")
    void delete_returns_404_when_not_found() throws Exception {
        org.mockito.Mockito.doThrow(new PostNotFoundException(9L))
                .when(deletePostUseCase).delete(9L);

        mockMvc.perform(delete("/api/posts/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ============ list ============

    @Test
    @DisplayName("게시글 목록을 조회하면 200 과 PostListResponse 본문을 돌려준다")
    void list_returns_200_with_paged_response_body() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Post p1 = Post.reconstitute(1L, "t1", "b1", "a1", now, now);
        Post p2 = Post.reconstitute(2L, "t2", "b2", "a2", now, now);
        PostListResult result = new PostListResult(List.of(p1, p2), 0, 20, 2L, 1);
        when(listPostsUseCase.list(0, 20)).thenReturn(result);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isArray())
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("t1"))
                .andExpect(jsonPath("$.posts[1].id").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("게시글 목록에 명시적 page/size 를 넘기면 Input Port 로 그대로 전달된다")
    void list_passes_explicit_page_and_size_to_use_case() throws Exception {
        PostListResult result = new PostListResult(List.of(), 1, 5, 0L, 0);
        when(listPostsUseCase.list(1, 5)).thenReturn(result);

        mockMvc.perform(get("/api/posts").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.posts.length()").value(0));

        verify(listPostsUseCase).list(1, 5);
    }

    @Test
    @DisplayName("목록 조회 시 page 가 음수이면 400 과 VALIDATION_FAILED 를 돌려준다")
    void list_returns_400_when_page_negative() throws Exception {
        mockMvc.perform(get("/api/posts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("page")));

        verify(listPostsUseCase, never()).list(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("목록 조회 시 size 가 최대값을 초과하면 400 과 VALIDATION_FAILED 를 돌려준다")
    void list_returns_400_when_size_exceeds_max() throws Exception {
        mockMvc.perform(get("/api/posts").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("size")));

        verify(listPostsUseCase, never()).list(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
