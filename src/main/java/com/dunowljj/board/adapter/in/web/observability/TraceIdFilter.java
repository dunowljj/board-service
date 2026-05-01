package com.dunowljj.board.adapter.in.web.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";
    public static final String MDC_QUERY = "query";

    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$");

    private final Set<String> queryValueAllowlist;

    public TraceIdFilter(Set<String> queryValueAllowlist) {
        this.queryValueAllowlist = Set.copyOf(queryValueAllowlist);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = traceId(request.getHeader(HEADER));

        response.setHeader(HEADER, traceId);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_PATH, request.getRequestURI());
        MDC.put(MDC_QUERY, sanitizeQuery(request.getQueryString()));

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_QUERY);
        }
    }

    private static String traceId(String header) {
        String candidate = header == null ? "" : header.trim();
        if (isValid(candidate)) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isValid(String candidate) {
        return !candidate.isBlank()
                && candidate.length() <= MAX_TRACE_ID_LENGTH
                && candidate.chars().noneMatch(Character::isISOControl);
    }

    /**
     * Allowlist-based query string sanitization for log output.
     * Keys failing {@link #KEY_PATTERN} collapse to {@code [invalid]}; keys outside
     * the allowlist anonymize to {@code [N keys redacted]} so cardinality is
     * preserved without exposing key names that may themselves carry PII; allow-listed
     * keys render as {@code key=value}. See ADR-0005 §7.
     * <p>
     * URL decode failures (malformed percent-encoding such as {@code ?page=%}) are
     * absorbed as {@code [invalid]} rather than escaping the filter. PLAN-0005-C
     * Risks #9: the filter runs outside {@code DispatcherServlet}, so any exception
     * thrown here would bypass {@link com.dunowljj.board.adapter.in.web.exception.GlobalExceptionHandler}
     * and break the {@code ProblemDetail} response contract.
     */
    private String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int redacted = 0;
        boolean anyInvalid = false;

        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            try {
                int eq = pair.indexOf('=');
                String rawKey = eq < 0 ? pair : pair.substring(0, eq);
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);

                if (!KEY_PATTERN.matcher(key).matches()) {
                    anyInvalid = true;
                    continue;
                }
                if (!queryValueAllowlist.contains(key)) {
                    redacted++;
                    continue;
                }
                String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                if (!out.isEmpty()) {
                    out.append(',');
                }
                out.append(key).append('=').append(value);
            } catch (IllegalArgumentException ex) {
                anyInvalid = true;
            }
        }
        if (redacted > 0) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append('[').append(redacted).append(" keys redacted]");
        }
        if (anyInvalid) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append("[invalid]");
        }
        return out.toString();
    }
}
