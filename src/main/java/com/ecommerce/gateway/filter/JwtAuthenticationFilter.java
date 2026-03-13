package com.ecommerce.gateway.filter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(WebClient.Builder webClientBuilder,
                                   @Value("#{'${security.public-paths:/auth/**,/actuator/**,/swagger-ui/**,/v3/api-docs/**,/fallback/**}'.split(',')}")
                                   List<String> publicPaths) {
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
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);
        return webClient.post()
                .uri("/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                           response -> Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")))
                .bodyToMono(TokenValidationResponse.class)
                .flatMap(validationResponse -> {
                    String userId = validationResponse.getUserId();
                    String email = validationResponse.getEmail();
                    String role = validationResponse.getRole();

                    if (userId == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token response: missing userId"));
                    }

                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder
                                    .header("X-User-Id", userId)
                                    .header("X-User-Email", email != null ? email : "")
                                    .header("X-User-Role", role != null ? role : ""))
                            .build();

                    return chain.filter(mutated);
                })
                .onErrorResume(ex -> {
                    if (ex instanceof ResponseStatusException) return Mono.error(ex);
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed", ex));
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
