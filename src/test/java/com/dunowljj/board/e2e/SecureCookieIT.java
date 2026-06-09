package com.dunowljj.board.e2e;

import com.dunowljj.board.config.PostgresTestcontainersConfig;
import com.dunowljj.board.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cookie {@code Secure} 속성이 {@code server.servlet.session.cookie.secure=true} 주입 시 실제로
 * 적용되는지 검증 (ADR-0011 §7 *기본값 의존 금지*). prod 배포에서 Secure 누락 회귀 차단.
 *
 * <p>별도 property context 라 다른 E2E 와 ApplicationContext 를 공유하지 않는다.
 */
@SpringBootTest(properties = "server.servlet.session.cookie.secure=true")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Tag("integration")
class SecureCookieIT {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("secure=true 면 CSRF(XSRF-TOKEN) 쿠키에 Secure 속성이 붙는다")
    void csrf_cookie_carries_secure_when_enabled() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(SecurityConfig.CSRF_COOKIE_NAME + "=")
                        .contains("Secure"));
    }
}
