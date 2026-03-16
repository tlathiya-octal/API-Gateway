package com.ecommerce.gateway.filter;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT header enrichment filter.
 *
 * <p><b>What this does:</b><br>
 * For every non-public request that reaches this filter, calls {@code POST /auth/validate}
 * on auth-service to exchange the Bearer token for user claims (userId, email, role) and
 * injects them as downstream headers ({@code X-User-Id}, {@code X-User-Email},
 * {@code X-User-Role}).
 *
 * <p><b>How it relates to {@link SecurityConfig}:</b><br>
 * {@link SecurityConfig} (Spring Security reactive OAuth2 resource server) performs the first
 * layer of JWT signature verification using the shared HMAC secret. If that fails the
 * request is rejected with 401 before this filter even runs.
 * This filter is the second layer — it enriches the exchange with user-identity headers so
 * downstream services can read them without decoding the JWT themselves.
 *
 * <p><b>Public paths</b> configured via {@code security.public-paths} in application.yml are
 * bypassed entirely — they are already permitted by {@link SecurityConfig}.
 *
 * <p>Order {@code -100} — runs after {@link CorrelationIdFilter} (-200) and
 * {@link RequestLoggingFilter} (-150), before {@link RbacAuthorizationFilter} (-90).
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(
            WebClient.Builder webClientBuilder,
            @Value("#{'${security.public-paths:/auth/**,/api/auth/**,/actuator/**,/swagger-ui/**,/v3/api-docs/**,/fallback/**}'.split(',')}")
            List<String> publicPaths) {
        // Uses load-balanced WebClient — resolves "auth-service" via service registry
        this.webClient = webClientBuilder.baseUrl("http://auth-service").build();
        this.publicPaths = publicPaths;
    }

    @lombok.Data
    public static class TokenValidationResponse {
        private boolean valid;
        private String userId;
        private String email;
        private String role;
        private List<String> roles;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Public paths: Spring Security already permits them — no header injection needed
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            // Spring Security (SecurityConfig) will already have rejected this — but be defensive
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header"));
        }

        return webClient.post()
                .uri("/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")))
                .bodyToMono(TokenValidationResponse.class)
                .flatMap(resp -> {
                    if (resp.getUserId() == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Invalid token response: missing userId"));
                    }

                    log.debug("[CID:{}] JWT validated — userId={} role={}",
                            exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTR),
                            resp.getUserId(), resp.getRole());

                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder
                                    .header("X-User-Id",    resp.getUserId())
                                    .header("X-User-Email", resp.getEmail()  != null ? resp.getEmail()  : "")
                                    .header("X-User-Role",  resp.getRole()   != null ? resp.getRole()   : ""))
                            .build();

                    return chain.filter(mutated);
                })
                .onErrorResume(ex -> {
                    if (ex instanceof ResponseStatusException) return Mono.error(ex);
                    log.warn("Token validation call failed: {}", ex.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Token validation failed", ex));
                });
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
