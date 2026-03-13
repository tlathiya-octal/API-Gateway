package com.ecommerce.gateway.route;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.function.Function;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      @Qualifier("loginRateLimiter") RedisRateLimiter loginRateLimiter,
                                      @Qualifier("registerRateLimiter") RedisRateLimiter registerRateLimiter,
                                      @Qualifier("defaultRateLimiter") RedisRateLimiter defaultRateLimiter,
                                      @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver) {

        return builder.routes()

                .route("auth-login", r -> r
                        .path("/api/auth/login")
                        .filters(commonFilters("auth-service", "/api/(?<segment>.*)", loginRateLimiter, userOrIpKeyResolver))
                        .uri("lb://auth-service"))

                .route("auth-register", r -> r
                        .path("/api/auth/register")
                        .filters(commonFilters("auth-service", "/api/(?<segment>.*)", registerRateLimiter, userOrIpKeyResolver))
                        .uri("lb://auth-service"))

                .route("auth-service", r -> r
                        .path("/api/auth-service/**")
                        .filters(commonFilters("auth-service", "/api/(?<segment>.*)", defaultRateLimiter, userOrIpKeyResolver))
                        .uri("lb://auth-service"))

                .route("user-service", r -> r
                        .path("/api/user-service/**")
                        .filters(commonFilters("user-service", "/api/(?<segment>.*)", defaultRateLimiter, userOrIpKeyResolver))
                        .uri("lb://user-service"))

                .route("product-service", r -> r
                        .path("/products/**")
                        .filters(commonFilters("product-service", "/products/(?<segment>.*)", defaultRateLimiter, userOrIpKeyResolver))
                        .uri("lb://product-service"))

                .route("order-service", r -> r
                        .path("/orders/**")
                        .filters(commonFilters("order-service", "/orders/(?<segment>.*)", defaultRateLimiter, userOrIpKeyResolver))
                        .uri("lb://order-service"))

                .build();
    }

    private Function<GatewayFilterSpec, UriSpec> commonFilters(
            String serviceName,
            String rewritePathPattern,
            RedisRateLimiter redisRateLimiter,
            KeyResolver userOrIpKeyResolver) {

        return filters -> filters

                .rewritePath(rewritePathPattern, "/${segment}")

                .setRequestHeader("X-Request-Source", "api-gateway")

                .requestRateLimiter(config -> {
                    config.setRateLimiter(redisRateLimiter);
                    config.setKeyResolver(userOrIpKeyResolver);
                })

                .retry(retry -> retry
                        .setRetries(2)
                        .setMethods(HttpMethod.GET, HttpMethod.POST)
                        .setStatuses(
                                HttpStatus.BAD_GATEWAY,
                                HttpStatus.SERVICE_UNAVAILABLE,
                                HttpStatus.GATEWAY_TIMEOUT
                        ))

                .circuitBreaker(cb -> cb
                        .setName(serviceName + "-circuit-breaker")
                        .setFallbackUri("forward:/fallback/" + serviceName));
    }
}