package com.dunowljj.board.e2e;

import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소유권 검증 — A 사용자 글을 B 사용자가 수정/삭제 시 403 + ACCESS_DENIED (ADR-0011 §9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Tag("integration")
class PostOwnershipIT {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
        jdbcTemplate.update("delete from users");
    }

    private MockHttpSession registerAndLogin(String email, String nickname, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/register").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","nickname":"%s","password":"%s"}
                                """, email, nickname, password)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, password)))
                .andExpect(status().isNoContent());

        return session;
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder,
                                                          MockHttpSession session) {
        return builder.session(session).with(csrf());
    }

    @Test
    @DisplayName("타인 글 수정 시도 시 403 + ACCESS_DENIED")
    void cannot_update_others_post() throws Exception {
        MockHttpSession aliceSession = registerAndLogin("alice@example.com", "alice", "secret123");
        MockHttpSession bobSession = registerAndLogin("bob@example.com", "bob", "secret123");

        MvcResult createResult = mockMvc.perform(authed(post("/api/posts"), aliceSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"alice-post","body":"body"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long postId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(authed(put("/api/posts/" + postId), bobSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"hijacked","body":"hijacked"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("타인 글 삭제 시도 시 403 + ACCESS_DENIED")
    void cannot_delete_others_post() throws Exception {
        MockHttpSession aliceSession = registerAndLogin("alice@example.com", "alice", "secret123");
        MockHttpSession bobSession = registerAndLogin("bob@example.com", "bob", "secret123");

        MvcResult createResult = mockMvc.perform(authed(post("/api/posts"), aliceSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"alice-post","body":"body"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long postId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(authed(delete("/api/posts/" + postId), bobSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("본인 글은 수정/삭제 가능")
    void can_update_and_delete_own_post() throws Exception {
        MockHttpSession aliceSession = registerAndLogin("alice@example.com", "alice", "secret123");

        MvcResult createResult = mockMvc.perform(authed(post("/api/posts"), aliceSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"alice-post","body":"body"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long postId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(authed(put("/api/posts/" + postId), aliceSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"updated","body":"updated"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(authed(delete("/api/posts/" + postId), aliceSession))
                .andExpect(status().isNoContent());
    }
}
