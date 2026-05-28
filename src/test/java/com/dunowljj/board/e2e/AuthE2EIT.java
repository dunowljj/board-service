package com.dunowljj.board.e2e;

import com.jayway.jsonpath.JsonPath;
import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.dunowljj.board.config.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth E2E — register → login → /me → logout 흐름 (ADR-0011 §4).
 * CSRF token 은 {@code spring-security-test} 의 {@code csrf()} post processor 로 주입.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Tag("integration")
class AuthE2EIT {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from posts");
        jdbcTemplate.update("delete from users");
    }

    @Test
    @DisplayName("register → login → /me → logout 전체 흐름이 성공한다")
    void full_auth_flow() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. 회원가입
        mockMvc.perform(post("/api/auth/register").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","nickname":"alice","password":"secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.nickname").value("alice"));

        // 2. 로그인
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"secret123"}
                                """))
                .andExpect(status().isNoContent());

        // 3. /me — 인증 유지 검증
        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.nickname").value("alice"));

        // 4. 로그아웃
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logoutResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains("JSESSIONID=")
                        .contains("Max-Age=0"));
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("로그인 성공 시 session id 가 변경된다 (session fixation 방어)")
    void login_changes_session_id() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/register").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","nickname":"alice","password":"secret123"}
                                """))
                .andExpect(status().isCreated());

        String preLoginSessionId = session.getId();

        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"secret123"}
                                """))
                .andExpect(status().isNoContent());

        String postLoginSessionId = session.getId();
        org.assertj.core.api.Assertions.assertThat(postLoginSessionId)
                .isNotEqualTo(preLoginSessionId);
    }

    @Test
    @DisplayName("GET /api/csrf 가 token 발급 + headerName 응답")
    void csrf_endpoint_returns_token() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.headerName").value(SecurityConfig.CSRF_HEADER_NAME));
    }

    @Test
    @DisplayName("GET /api/csrf 로 받은 cookie + 설정된 CSRF header 로 mutation 이 성공한다")
    void mutation_accepts_explicit_csrf_cookie_and_header() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value(SecurityConfig.CSRF_HEADER_NAME))
                .andReturn();

        String csrfBody = csrfResult.getResponse().getContentAsString();
        String headerName = JsonPath.read(csrfBody, "$.headerName");
        String token = JsonPath.read(csrfBody, "$.token");
        Cookie xsrfCookie = csrfResult.getResponse().getCookie(SecurityConfig.CSRF_COOKIE_NAME);
        assertThat(xsrfCookie).isNotNull();

        mockMvc.perform(post("/api/auth/register").session(session)
                        .cookie(xsrfCookie)
                        .header(headerName, token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"csrf@example.com","nickname":"csrfuser","password":"secret123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("로그인 실패 시 401 + AUTHENTICATION_FAILED ProblemDetail")
    void login_failure_returns_401_problem_detail() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"wrong123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("email 중복 가입 시 409 + DUPLICATE_EMAIL")
    void register_duplicate_email_returns_409() throws Exception {
        String body = """
                {"email":"alice@example.com","nickname":"alice","password":"secret123"}
                """;
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","nickname":"bob","password":"secret123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("nickname 대소문자 차이만 있어도 canonical 중복 차단")
    void register_duplicate_nickname_canonical_returns_409() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","nickname":"Alice","password":"secret123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"other@example.com","nickname":"alice","password":"secret123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }
}
