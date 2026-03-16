package com.ecommerce.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;

public class RouteFilter{ }
//public class RouteFilter implements GlobalFilter {
//
//    private static final Logger log = LoggerFactory.getLogger(RouteFilter.class);
//
//    private final ReactiveDiscoveryClient discoveryClient;
//
//    public RouteFilter(ReactiveDiscoveryClient discoveryClient) {
//        this.discoveryClient = discoveryClient;
//    }
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//
//        // 1. Equivalent to Zuul's shouldFilter() path matching logic
//        String requestURI = exchange.getRequest().getURI().getPath();
//
//        // 2. Extract current targeted Route URI (e.g., lb://user-service)
//        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
//
//        // If it's not a load-balanced route, just continue the chain
//        if (routeUri == null || !"lb".equals(routeUri.getScheme())) {
//            return chain.filter(exchange);
//        }
//
//        String currentServiceId = routeUri.getHost(); // Extracts 'user-service'
//        String targetReadReplicaId = getReadReplicaId(currentServiceId);
//
//        if (targetReadReplicaId != null) {
//            // 3. Asynchronously (non-blocking) check if the read replica exists
//            return discoveryClient.getInstances(targetReadReplicaId)
//                    .collectList()
//                    .flatMap(instances -> {
//                        if (instances != null && !instances.isEmpty()) {
//                            log.info("Request Forwarding To {} Read....", currentServiceId);
//                            // 4. Mutate the route URI to point to the read replica
//                            mutateRouteUri(exchange, routeUri, targetReadReplicaId);
//                        } else {
//                            log.info("Request Forwarding to {} Write (No read replicas available)....", currentServiceId);
//                        }
//
//                        // Proceed with the updated or original exchange
//                        return chain.filter(exchange);
//                    });
//        }
//
//        return chain.filter(exchange);
//    }
//
//    /**
//     * Determines the corresponding read replica service ID based on the incoming service ID.
//     */
//    private String getReadReplicaId(String serviceId) {
//        if (serviceId == null) return null;
//
//        return switch (serviceId.toLowerCase()) {
//            case "user-service" -> "user-service-read";
//            case "admin-service" -> "admin-service-read";
//            case "live-radio" -> "live-radio-read-service";
//            case "podcast-service" -> "podcast-read-service";
//            default -> null;
//        };
//    }
//
//    /**
//     * Updates the GATEWAY_REQUEST_URL_ATTR so the subsequent LoadBalancer filter
//     * resolves the new service ID instead of the original one.
//     */
//    private void mutateRouteUri(ServerWebExchange exchange, URI originalUri, String newServiceId) {
//        try {
//            URI newUri = new URI(originalUri.getScheme(), newServiceId, originalUri.getPath(),
//                    originalUri.getQuery(), originalUri.getFragment());
//            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newUri);
//        } catch (URISyntaxException e) {
//            log.error("Error formatting new route URI for read replica: {}", newServiceId, e);
//        }
//    }
//}
