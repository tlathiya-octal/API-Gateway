package com.ecommerce.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_ATTR = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // compute correlation id once and keep it effectively final for lambdas
        final String correlationId;
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (incoming == null || incoming.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.info("Generated new Correlation ID: {}", correlationId);
        } else {
            correlationId = incoming;
        }

        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);

        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.header(CORRELATION_ID_HEADER, correlationId))
                .build();

        return chain.filter(mutated)
                .doFirst(() -> MDC.put(CORRELATION_ID_ATTR, correlationId))
                .doFinally(signal -> MDC.remove(CORRELATION_ID_ATTR));
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
