package com.ecommerce.gateway.route;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.function.Function;

/**
 * Generic, scalable Spring Cloud Gateway routing configuration.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Routes are data, not code — each service is described by a {@link ServiceRoute} record.</li>
 *   <li>A single {@code routeFor()} helper converts a {@link ServiceRoute} into a live route,
 *       eliminating per-endpoint duplication.</li>
 *   <li>Adding a new service or a new protected endpoint requires only a new {@link ServiceRoute}
 *       entry — zero changes to routing logic.</li>
 * </ul>
 *
 * <p>Supported URL conventions:
 * <ul>
 *   <li>{@code /api/{service-name}/**} — standard API-prefixed services (auth, user, …)</li>
 *   <li>{@code /{resource}/**}          — resource-named services (products, orders, …)</li>
 * </ul>
 */
@Configuration
public class RouteConfig {

    // -------------------------------------------------------------------------
    // Route descriptor — pure data; no logic lives here
    // -------------------------------------------------------------------------

    /**
     * Immutable descriptor for a single logical route.
     *
     * @param routeId     Unique identifier used in logs, metrics, and circuit-breaker naming.
     * @param pathPattern Ant-style path pattern matched by the gateway predicate
     *                    (e.g. {@code /api/auth/**} or {@code /api/auth/login}).
     * @param serviceId   Downstream service name as registered in the service registry
     *                    (e.g. {@code auth-service}).
     * @param rewriteFrom Regex applied to the incoming path before forwarding
     *                    (must capture a named group {@code segment}).
     * @param rateLimiter {@link RedisRateLimiter} instance to enforce for this route.
     */
    record ServiceRoute(
            String routeId,
            String pathPattern,
            String serviceId,
            String rewriteFrom,
            RedisRateLimiter rateLimiter
    ) {}

    // -------------------------------------------------------------------------
    // Route registry — add new services/endpoints here only
    // -------------------------------------------------------------------------

    /**
     * Central registry of all routed services.
     *
     * <p>Convention:
     * <pre>
     *   /api/{service-name}/**  →  rewriteFrom = "/api/(?&lt;segment&gt;.*)"
     *   /{resource}/**          →  rewriteFrom = "/{resource}/(?&lt;segment&gt;.*)"
     * </pre>
     *
     * <p>Sensitive endpoints (login, register) receive dedicated rate-limiter beans
     * while sharing every other filter with the rest of the routes.
     */
    private List<ServiceRoute> buildRouteRegistry(
            RedisRateLimiter loginRateLimiter,
            RedisRateLimiter registerRateLimiter,
            RedisRateLimiter defaultRateLimiter) {

        return List.of(

                // ── Auth endpoints with dedicated, stricter rate limits ──────────────────
                new ServiceRoute(
                        "auth-login",
                        "/api/auth/login",
                        "auth-service",
                        "/api/(?<segment>.*)",
                        loginRateLimiter),

                new ServiceRoute(
                        "auth-register",
                        "/api/auth/register",
                        "auth-service",
                        "/api/(?<segment>.*)",
                        registerRateLimiter),

                // ── Auth wildcard catch-all (remaining auth endpoints) ───────────────────
                new ServiceRoute(
                        "auth-service",
                        "/api/auth/**",
                        "auth-service",
                        "/api/(?<segment>.*)",
                        defaultRateLimiter),

                // ── /api/{service-name}/** — add new API-prefixed services below ─────────
                new ServiceRoute(
                        "user-service",
                        "/api/users/**",           // matches UserController @RequestMapping("/users")
                        "user-service",
                        "/api/(?<segment>.*)",     // /api/users/me  →  /users/me
                        defaultRateLimiter),

                // ── /{resource}/** — add new resource-named services below ───────────────
                new ServiceRoute(
                        "product-service",
                        "/products/**",
                        "product-service",
                        "/products/(?<segment>.*)",
                        defaultRateLimiter),

                new ServiceRoute(
                        "order-service",
                        "/orders/**",
                        "order-service",
                        "/orders/(?<segment>.*)",
                        defaultRateLimiter)

                /*
                 * ── To onboard a new service, append a ServiceRoute here ─────────────────
                 *
                 * Example — inventory service:
                 *   new ServiceRoute(
                 *       "inventory-service",
                 *       "/api/inventory-service/**",
                 *       "inventory-service",
                 *       "/api/(?<segment>.*)",
                 *       defaultRateLimiter)
                 *
                 * No other changes are required.
                 */
        );
    }

    // -------------------------------------------------------------------------
    // RouteLocator bean — iterates the registry; no per-endpoint wiring needed
    // -------------------------------------------------------------------------

    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Qualifier("loginRateLimiter")    RedisRateLimiter loginRateLimiter,
            @Qualifier("registerRateLimiter") RedisRateLimiter registerRateLimiter,
            @Qualifier("defaultRateLimiter")  RedisRateLimiter defaultRateLimiter,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver) {

        RouteLocatorBuilder.Builder routes = builder.routes();

        for (ServiceRoute route : buildRouteRegistry(loginRateLimiter, registerRateLimiter, defaultRateLimiter)) {
            routes = routeFor(routes, route, userOrIpKeyResolver);
        }

        return routes.build();
    }

    // -------------------------------------------------------------------------
    // Generic route builder — single source of truth for all routing behaviour
    // -------------------------------------------------------------------------

    /**
     * Registers a single {@link ServiceRoute} into the builder and returns the
     * updated builder for fluent chaining.
     *
     * <p>All routes receive identical filter semantics:
     * <ol>
     *   <li>Path rewrite via regex capture group {@code segment}</li>
     *   <li>{@code X-Request-Source} header injection</li>
     *   <li>Redis-backed rate limiting (limiter is route-specific)</li>
     *   <li>Idempotent-method retry on gateway errors</li>
     *   <li>Circuit breaker with service-specific fallback URI</li>
     * </ol>
     */
    private RouteLocatorBuilder.Builder routeFor(
            RouteLocatorBuilder.Builder routes,
            ServiceRoute route,
            KeyResolver keyResolver) {

        return routes.route(route.routeId(), r -> r
                .path(route.pathPattern())
                .filters(commonFilters(route.serviceId(), route.rewriteFrom(), route.rateLimiter(), keyResolver))
                .uri("lb://" + route.serviceId()));
    }

    // -------------------------------------------------------------------------
    // Shared filter chain — resilience + observability in one place
    // -------------------------------------------------------------------------

    /**
     * Builds the standard filter chain applied uniformly to every route.
     *
     * <p>Centralising filters here guarantees that resilience policies
     * (retry, circuit breaker) and observability headers are never accidentally
     * omitted when a new route is added.
     *
     * @param serviceName         Used to scope the circuit-breaker name and fallback URI.
     * @param rewritePathPattern  Regex with a {@code segment} capture group.
     * @param redisRateLimiter    Rate-limiter instance (may vary per route).
     * @param keyResolver         Strategy for extracting the rate-limit key.
     * @return A composable filter function accepted by {@link RouteLocatorBuilder}.
     */
    private Function<GatewayFilterSpec, UriSpec> commonFilters(
            String serviceName,
            String rewritePathPattern,
            RedisRateLimiter redisRateLimiter,
            KeyResolver keyResolver) {

        return filters -> filters

                // 1. Rewrite incoming path before forwarding to the downstream service
                .rewritePath(rewritePathPattern, "/${segment}")

                // 2. Mark every request as originating from this gateway (useful for downstream audit logs)
                .setRequestHeader("X-Request-Source", "api-gateway")

                // 3. Token-bucket rate limiting backed by Redis (distributed, cluster-safe)
                .requestRateLimiter(config -> {
                    config.setRateLimiter(redisRateLimiter);
                    config.setKeyResolver(keyResolver);
                })

                // 4. Retry on transient infrastructure errors (GET only — POST is NOT idempotent)
                .retry(retry -> retry
                        .setRetries(2)
                        .setMethods(HttpMethod.GET)  // Never retry POST/PUT/DELETE — side effects!
                        .setStatuses(
                                HttpStatus.BAD_GATEWAY,
                                HttpStatus.SERVICE_UNAVAILABLE,
                                HttpStatus.GATEWAY_TIMEOUT))

                // 5. Circuit breaker — opens on sustained failures; routes to a local fallback
                .circuitBreaker(cb -> cb
                        .setName(serviceName + "-circuit-breaker")
                        .setFallbackUri("forward:/fallback/" + serviceName));
    }
}