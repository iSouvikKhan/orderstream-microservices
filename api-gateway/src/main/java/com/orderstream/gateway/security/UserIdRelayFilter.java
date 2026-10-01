package com.orderstream.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * Propagates the authenticated user to downstream services as {@code X-User-Id}. Any client-sent
 * value is stripped first, so the header can only ever carry a verified JWT subject.
 */
@Component
public class UserIdRelayFilter implements GlobalFilter, Ordered {

    public static final String USER_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .map(user -> exchange.mutate()
                        .request(r -> r.headers(h -> {
                            h.remove(USER_HEADER);
                            h.set(USER_HEADER, user);
                        }))
                        .build())
                .switchIfEmpty(Mono.fromSupplier(() -> exchange.mutate()
                        .request(r -> r.headers(h -> h.remove(USER_HEADER)))
                        .build()))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
