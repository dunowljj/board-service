package com.dunowljj.board.e2e;

import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Post E2E — 인증 후 CRUD 골든 플로우 (ADR-0011 §3 쓰기 로그인 필수, §6 CSRF).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Tag("integration")
class PostE2EIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/register").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","nickname":"alice","password":"secret123"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"secret123"}
                                """))
                .andExpect(status().isNoContent());
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
        jdbcTemplate.update("delete from users");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.session(session).with(csrf());
    }

    @Test
    @DisplayName("로그인된 사용자가 게시글을 등록·조회·수정·목록·삭제하면 마지막 조회에서 404 를 돌려준다")
    void crud_golden_flow_completes_create_to_delete_with_404() throws Exception {
        String createdJson = mockMvc.perform(authed(post("/api/posts"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"hello","body":"world"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("hello"))
                .andExpect(jsonPath("$.authorNickname").value("alice"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse().getContentAsString();

        Number idValue = JsonPath.read(createdJson, "$.id");
        long id = idValue.longValue();
        String createdAt = JsonPath.read(createdJson, "$.createdAt");

        mockMvc.perform(get("/api/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorNickname").value("alice"));

        mockMvc.perform(authed(put("/api/posts/" + id))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"hello-v2","body":"world-v2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("hello-v2"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mockMvc.perform(get("/api/posts").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(id))
                .andExpect(jsonPath("$.posts[0].authorNickname").value("alice"));

        mockMvc.perform(authed(delete("/api/posts/" + id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("검증 오류 응답은 supplied X-Trace-Id 를 echo 하고 미공급 시 자동 생성한다")
    void error_flow_echoes_or_generates_trace_id() throws Exception {
        mockMvc.perform(authed(post("/api/posts"))
                        .header("X-Trace-Id", "e2e-trace-001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"","body":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(header().string("X-Trace-Id", "e2e-trace-001"));

        mockMvc.perform(get("/api/posts/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string("X-Trace-Id",
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }
}
