package com.ecommerce.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";
        String path = exchange.getRequest().getPath().value();
        String correlationId = (String) exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTR);

        // We don't log incoming here since the format implies a single log line per request with TIME


        return chain.filter(exchange)
                .doFinally(signal -> {
                    Long startTime = exchange.getAttribute(START_TIME_ATTR);
                    long durationMs = startTime == null ? 0 : System.currentTimeMillis() - startTime;
                    Integer status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                    if (userId == null || userId.isEmpty()) {
                        userId = "anonymous";
                    }
                    log.info("[CID:{}] {} {} USER:{} STATUS:{} TIME:{}ms",
                            correlationId, method, path, userId, status, durationMs);
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
