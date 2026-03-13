package com.ecommerce.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class UserOrIpKeyResolverTest {

    @Test
    void resolvesUserIdWhenPresent() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        RedisRateLimiterConfig config = new RedisRateLimiterConfig(properties);
        KeyResolver resolver = config.userOrIpKeyResolver();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("X-User-Id", "user-123")
                        .build());

        String key = resolver.resolve(exchange).block();
        assertThat(key).isEqualTo("user:user-123");
    }

    @Test
    void resolvesIpWhenNoUserId() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        RedisRateLimiterConfig config = new RedisRateLimiterConfig(properties);
        KeyResolver resolver = config.userOrIpKeyResolver();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                        .build());

        String key = resolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:127.0.0.1");
    }
}
