package com.dunowljj.board.config.security;

import com.dunowljj.board.application.port.in.LoginUserUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security session-based 인증 + CSRF 정책 (ADR-0011 §2/§3/§6/§7).
 *
 * <p>JSON 커스텀 login 흐름이라 {@code SecurityContextRepository} bean 등록 + login endpoint 가
 * {@code saveContext} 명시 호출 필요 (PLAN-0011 Risk #2).
 *
 * <p>CSRF token 은 {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} — JS 가 cookie 에서 token
 * 읽을 수 있어야 함. {@code secure} 속성은 JSESSIONID 와 동일 profile 값 (`server.servlet.session.cookie.secure`).
 *
 * <p>{@code formLogin} / {@code httpBasic} 모두 disable — 커스텀 JSON endpoint 만 사용.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler,
            LoginUserUseCase loginUserUseCase,
            ObjectMapper objectMapper) throws Exception {

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName(CSRF_COOKIE_NAME);
        csrfTokenRepository.setHeaderName(CSRF_HEADER_NAME);
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));

        // F-b: JSON 커스텀 login 필터. successfulAuthentication 이 session fixation 전략 +
        // SecurityContextRepository 저장을 프레임워크 차원에서 수행 (ADR-0011 §4 amended).
        JsonUsernamePasswordAuthenticationFilter loginFilter =
                new JsonUsernamePasswordAuthenticationFilter(loginUserUseCase, objectMapper);
        loginFilter.setSecurityContextRepository(securityContextRepository);
        loginFilter.setSessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy());
        loginFilter.setAuthenticationSuccessHandler(new JsonLoginSuccessHandler());
        loginFilter.setAuthenticationFailureHandler(new JsonLoginFailureHandler(objectMapper));

        return http
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(c -> c.csrfTokenRepository(csrfTokenRepository))
                .securityContext(c -> c.securityContextRepository(securityContextRepository))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }
}
