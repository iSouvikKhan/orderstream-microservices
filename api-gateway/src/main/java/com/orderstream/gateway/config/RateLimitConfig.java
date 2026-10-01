package com.orderstream.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /** Rate-limit per authenticated user; anonymous traffic is limited per client IP. */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(user -> "user:" + user)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(InetSocketAddress::getAddress)
                        .map(a -> a.getHostAddress())
                        .orElse("unknown")));
    }
}
