package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global request/response logging filter for the API Gateway.
 *
 * <p>Emits two structured log lines per request:
 * <pre>
 *   →  [CID:abc] INCOMING  POST /api/auth/register  ip=127.0.0.1
 *   ←  [CID:abc] COMPLETED POST /api/auth/register  STATUS:201  TIME:42ms  USER:anonymous
 * </pre>
 *
 * <p>Order is set to {@code -150} — runs after {@link CorrelationIdFilter} (order -200)
 * so the correlation ID is guaranteed to be present, but before JWT validation (-100)
 * so every request — including rejected ones — is logged.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "gateway.requestStartTime";

    // ── Filter order ──────────────────────────────────────────────────────────
    // -200 : CorrelationIdFilter  (generates/propagates X-Correlation-ID)
    // -150 : RequestLoggingFilter (this — reads CID already set above)
    // -100 : JwtAuthenticationFilter
    //  -90 : RbacAuthorizationFilter
    //    0 : Route filters (rewrite, rate-limit, circuit-breaker, …)
    @Override
    public int getOrder() {
        return -150;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        final long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path   = request.getPath().value();
        String cid    = resolveCorrelationId(exchange);
        String ip     = resolveClientIp(request);

        log.info("→  [CID:{}] INCOMING  {} {}  ip={}", cid, method, path, ip);

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    String userId = resolveUserId(exchange);
                    log.info("←  [CID:{}] COMPLETED {} {}  STATUS:{}  TIME:{}ms  USER:{}",
                            cid, method, path, status, durationMs, userId);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveCorrelationId(ServerWebExchange exchange) {
        // Try exchange attribute first (set by CorrelationIdFilter)
        Object attr = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        // Fall back to request header in case this filter runs concurrently
        String header = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        return header != null ? header : "n/a";
    }

    private String resolveUserId(ServerWebExchange exchange) {
        // X-User-Id is injected by JwtAuthenticationFilter after token validation
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        return (userId != null && !userId.isBlank()) ? userId : "anonymous";
    }

    private String resolveClientIp(ServerHttpRequest request) {
        // Honour X-Forwarded-For set by reverse proxies/load balancers
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
