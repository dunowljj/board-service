package com.dunowljj.board.adapter.in.web.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QueryLogProperties.class)
public class TraceIdFilterConfig {

    /**
     * One slot ahead of Spring Security's filter chain
     * ({@code SecurityProperties.DEFAULT_FILTER_ORDER = -100}). Held as a literal
     * here so this module does not pull in the Security starter before the
     * Security ADR lands. Replace with {@code SecurityProperties.DEFAULT_FILTER_ORDER - 1}
     * at that time.
     */
    // TODO(Security ADR): replace with SecurityProperties.DEFAULT_FILTER_ORDER - 1
    private static final int FILTER_ORDER = -101;

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(QueryLogProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter(properties.valueAllowlist()));
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
