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

    // Map of path patterns to allowed roles (must be ordered to match specific paths first)
    private final Map<String, List<String>> roleRules = createRoleRules();

    private Map<String, List<String>> createRoleRules() {
        Map<String, List<String>> rules = new java.util.LinkedHashMap<>();
        rules.put("/api/users/me", List.of("CUSTOMER", "VENDOR", "ADMIN"));
        rules.put("/api/users/**", List.of("ADMIN"));
        rules.put("/api/vendors/**", List.of("ADMIN", "VENDOR"));
        rules.put("/api/products/create", List.of("VENDOR"));
        rules.put("/api/orders/**", List.of("CUSTOMER", "ADMIN"));
        return rules;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        for (Map.Entry<String, List<String>> entry : roleRules.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
                if (role == null || !isRoleAllowed(role, entry.getValue())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: insufficient role"));
                }
            }
        }

        return chain.filter(exchange);
    }

    private boolean isRoleAllowed(String userRole, List<String> allowedRoles) {
        // userRole might be something like "ROLE_ADMIN" or "ADMIN". We can handle both.
        String normalizedUserRole = userRole.replace("ROLE_", "").toUpperCase();
        return allowedRoles.stream()
                .anyMatch(r -> r.equalsIgnoreCase(normalizedUserRole) || r.equalsIgnoreCase(userRole));
    }

    @Override
    public int getOrder() {
        return -90; // Runs after JwtAuthenticationFilter (which is -100)
    }
}
