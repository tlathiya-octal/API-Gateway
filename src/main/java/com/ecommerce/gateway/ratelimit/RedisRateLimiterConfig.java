package com.ecommerce.gateway.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class RedisRateLimiterConfig {

    private final GatewayRateLimitProperties properties;

    public RedisRateLimiterConfig(GatewayRateLimitProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RedisRateLimiter loginRateLimiter() {
        GatewayRateLimitProperties.RateLimit limit = properties.getLogin();
        return new RedisRateLimiter(limit.getReplenishRate(), limit.getBurstCapacity(), limit.getRequestedTokens());
    }

    @Bean
    public RedisRateLimiter registerRateLimiter() {
        GatewayRateLimitProperties.RateLimit limit = properties.getRegister();
        return new RedisRateLimiter(limit.getReplenishRate(), limit.getBurstCapacity(), limit.getRequestedTokens());
    }

    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter() {
        GatewayRateLimitProperties.RateLimit limit = properties.getDefaultLimit();
        return new RedisRateLimiter(limit.getReplenishRate(), limit.getBurstCapacity(), limit.getRequestedTokens());
    }

    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (StringUtils.hasText(userId)) {
                return Mono.just("user:" + userId);
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just("ip:" + remoteAddress.getAddress().getHostAddress());
            }

            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(xForwardedFor)) {
                return Mono.just("ip:" + xForwardedFor.split(",")[0].trim());
            }
            return Mono.just("ip:unknown");
        };
    }
}
