package com.ecommerce.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class RbacAuthorizationFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Ordered rules (specific -> generic)
    private final Map<String, List<String>> roleRules = createRoleRules();

    private Map<String, List<String>> createRoleRules() {
        Map<String, List<String>> rules = new java.util.LinkedHashMap<>();

        // specific endpoints first
        rules.put("/api/users/me", List.of("CUSTOMER", "VENDOR", "ADMIN"));
        rules.put("/api/products/create", List.of("VENDOR"));

        // generic patterns later
        rules.put("/api/vendors/**", List.of("ADMIN", "VENDOR"));
        rules.put("/api/orders/**", List.of("CUSTOMER", "ADMIN"));
        rules.put("/api/users/**", List.of("ADMIN"));

        return rules;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        for (Map.Entry<String, List<String>> entry : roleRules.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {

                String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");

                if (role == null) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: role missing"));
                }

                boolean allowed = entry.getValue()
                        .stream()
                        .anyMatch(r -> r.equalsIgnoreCase(role) || role.equalsIgnoreCase("ROLE_" + r));

                if (!allowed) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: insufficient role"));
                }

                // IMPORTANT: stop after first matched rule
                return chain.filter(exchange);
            }
        }

        // no RBAC rule -> allow
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
