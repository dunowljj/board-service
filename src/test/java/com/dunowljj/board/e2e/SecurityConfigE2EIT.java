package com.dunowljj.board.e2e;

import com.dunowljj.board.config.PostgresTestcontainersConfig;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityFilterChain 의 access matrix 검증 (ADR-0011 §3 / §6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Tag("integration")
class SecurityConfigE2EIT {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
        jdbcTemplate.update("delete from users");
    }

    @Test
    @DisplayName("anonymous GET /api/posts 는 200")
    void anonymous_get_posts_is_allowed() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous GET /api/csrf 는 200 + token 발급")
    void anonymous_get_csrf_is_allowed() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("비인증 + CSRF token 있는 POST /api/posts 는 401 + AUTHENTICATION_REQUIRED")
    void unauthenticated_post_returns_401() throws Exception {
        mockMvc.perform(post("/api/posts").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":"b"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("CSRF token 없는 POST /api/auth/register 는 403")
    void register_without_csrf_returns_403() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.c","nickname":"alice","password":"secret123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인된 사용자가 GET /api/users/me 호출하면 200")
    void authenticated_get_me_returns_200() throws Exception {
        MockHttpSession session = new MockHttpSession();

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

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비인증 GET /api/users/me 는 401 + AUTHENTICATION_REQUIRED")
    void anonymous_get_me_returns_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
