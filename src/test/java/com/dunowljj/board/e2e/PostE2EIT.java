package com.dunowljj.board.e2e;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class PostE2EIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // ADR-0006 §5 옵션 (c) — JDBC auto-commit. @AfterEach @Transactional 은
    // 테스트 인스턴스가 AOP 프록시 대상이 아니라 무력화되므로 의도적으로 사용 안 함.
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
    }

    @Test
    @DisplayName("게시글을 등록·조회·수정·목록·삭제하면 마지막 조회에서 404 를 돌려준다")
    void crud_golden_flow_completes_create_to_delete_with_404() throws Exception {
        // Given
        String createBody = """
                {"title":"hello","body":"world","author":"alice"}
                """;

        // When: POST → 201
        String createdJson = mockMvc.perform(post("/api/posts")
                        .contentType(APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("hello"))
                .andExpect(jsonPath("$.author").value("alice"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse().getContentAsString();

        Number idValue = JsonPath.read(createdJson, "$.id");
        long id = idValue.longValue();
        String createdAt = JsonPath.read(createdJson, "$.createdAt");

        // GET → 200, 영속 확인 (X-Trace-Id 어서트는 대표 응답에서만 — Plan Scope 결정)
        mockMvc.perform(get("/api/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("alice"));

        // PUT → 200, 갱신 반영, createdAt 보존
        mockMvc.perform(put("/api/posts/" + id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"hello-v2","body":"world-v2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("hello-v2"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        // GET list → 1 건
        mockMvc.perform(get("/api/posts").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(id));

        // DELETE → 204
        mockMvc.perform(delete("/api/posts/" + id))
                .andExpect(status().isNoContent());

        // GET → 404 POST_NOT_FOUND (오류 경로 대표 — X-Trace-Id 어서트)
        mockMvc.perform(get("/api/posts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("검증 오류 응답은 supplied X-Trace-Id 를 echo 하고 미공급 시 자동 생성한다")
    void error_flow_echoes_or_generates_trace_id() throws Exception {
        // 검증 실패 + supplied 헤더 echo
        mockMvc.perform(post("/api/posts")
                        .header("X-Trace-Id", "e2e-trace-001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"","body":"","author":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(header().string("X-Trace-Id", "e2e-trace-001"));

        // not-found + 미공급 시 UUID 자동 생성
        mockMvc.perform(get("/api/posts/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string("X-Trace-Id",
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }
}
