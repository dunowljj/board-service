package com.dunowljj.board.adapter.in.web.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Bound from {@code observability.query.*}. {@code value-allowlist} names the query
 * parameter keys whose values may be rendered as {@code key=value} in logs;
 * everything else is anonymized to {@code [N keys redacted]} (see ADR-0005 §7).
 */
@ConfigurationProperties("observability.query")
public record QueryLogProperties(Set<String> valueAllowlist) {

    public QueryLogProperties {
        valueAllowlist = (valueAllowlist == null) ? Set.of() : Set.copyOf(valueAllowlist);
    }
}
