package com.ecommerce.gateway.filter;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import com.octal.fsm.repositories.AllowedSuffixesRepository;
import com.octal.fsm.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Component
public class RouteFilter extends ZuulFilter {
    private static Logger log = LoggerFactory.getLogger(RouteFilter.class);

    private final LoadBalancerClient loadBalancerClient;

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        String requestURI = request.getRequestURI();
        List<String> allowedSuffixes = allowedSuffixesRepository.getUrlSuffix();
//        boolean matched = allowedSuffixes.stream().anyMatch(suffix ->
//                Pattern.compile(suffix).matcher(requestURI).find());

//        if (matched) {
//            log.info("Matched");
//        } else {
//            log.info("Not Matched");
//        }
        return false;
    }

    private final DiscoveryClient discoveryClient;
    private final AllowedSuffixesRepository allowedSuffixesRepository;

    public RouteFilter(LoadBalancerClient loadBalancerClient, DiscoveryClient discoveryClient, AllowedSuffixesRepository allowedSuffixesRepository) {
        this.loadBalancerClient = loadBalancerClient;
        this.discoveryClient = discoveryClient;
        this.allowedSuffixesRepository = allowedSuffixesRepository;
    }

    @Override
    public Object run() throws ZuulException {

        RequestContext ctx = RequestContext.getCurrentContext();
        String currentServiceId = (String) ctx.get("serviceId");
        if (!TextUtils.isEmpty(currentServiceId)) {
            ServiceInstance instance = loadBalancerClient.choose(currentServiceId);
            if (instance != null) {
                String resolvedHost = instance.getHost();
                int resolvedPort = instance.getPort();
                System.out.println("Zuul resolved " + currentServiceId + " to: " + resolvedHost + ":" + resolvedPort);
            } else {
                System.out.println("No instance found for service ID: " + currentServiceId);
            }
            if (currentServiceId.equalsIgnoreCase("user-service")) {
                // Check if user-service-read is registered
                List<ServiceInstance> userInstances = discoveryClient.getInstances("user-service-read");
                if (userInstances != null && !userInstances.isEmpty()) {
                    log.info("Request Forwarding To User Read....");
//                    ctx.set("serviceId", "user-service-read"); // Redirect to user-service-read
                } else {
                    log.info("Request Forwarding to User Write....");
                    ctx.set("serviceId", "user-service"); // Fallback to actual destination service
                }
            } else if (currentServiceId.equalsIgnoreCase("admin-service")) {
                List<ServiceInstance> userInstances = discoveryClient.getInstances("admin-service-read");
                if (userInstances != null && !userInstances.isEmpty()) {
                    log.info("Request Forwarding To Admin Read....");
//                    ctx.set("serviceId", "admin-service-read"); // Redirect to user-service-read
                } else {
                    log.info("Request Forwarding to Admin Write....");
                    ctx.set("serviceId", "admin-service"); // Fallback to actual destination service
                }
            } else if (currentServiceId.equalsIgnoreCase("live-radio")) {
                List<ServiceInstance> radioInstances = discoveryClient.getInstances("live-radio-read-service");
                if (radioInstances != null && !radioInstances.isEmpty()) {
                    log.info("Request Forwarding To Radio Read....");
                    ctx.set("serviceId", "live-radio-read-service"); // Redirect to user-service-read
                } else {
                    log.info("Request Forwarding to User Write....");
                    ctx.set("serviceId", "live-radio"); // Fallback to actual destination service
                }
            } else if (currentServiceId.equalsIgnoreCase("podcast-service")) {
                List<ServiceInstance> radioInstances = discoveryClient.getInstances("podcast-read-service");
                if (radioInstances != null && !radioInstances.isEmpty()) {
                    log.info("Request Forwarding To Podcast Read....");
                    ctx.set("serviceId", "podcast-read-service"); // Redirect to user-service-read
                } else {
                    log.info("Request Forwarding to Podcast Write....");
                    ctx.set("serviceId", "podcast-service"); // Fallback to actual destination service
                }
            }
        }

        ctx.setRouteHost(null); // Ensures routing via service discovery
        return null;
    }

    @Override
    public String filterType() {
        return "route";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

}
