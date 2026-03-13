package com.ecommerce.gateway.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {

    private RateLimit login = new RateLimit(1, 60, 12);
    private RateLimit register = new RateLimit(1, 60, 20);
    private RateLimit defaultLimit = new RateLimit(1, 60, 3);

    @Getter
    @Setter
    public static class RateLimit {
        private int replenishRate;
        private int burstCapacity;
        private int requestedTokens;

        public RateLimit() {
        }

        public RateLimit(int replenishRate, int burstCapacity, int requestedTokens) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.requestedTokens = requestedTokens;
        }
    }
}
