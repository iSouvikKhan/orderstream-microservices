package com.orderstream.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Optional;

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
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> chain.filter(exchange.mutate()
                        .request(withUserHeader(exchange.getRequest(), user))
                        .build()));
    }

    /**
     * Request headers may already be read-only at this point (e.g. after Spring Security has wrapped
     * the request), so build a fresh copy instead of mutating them in place.
     */
    private static ServerHttpRequest withUserHeader(ServerHttpRequest request, Optional<String> user) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(request.getHeaders());
        headers.remove(USER_HEADER);
        user.ifPresent(u -> headers.set(USER_HEADER, u));
        HttpHeaders readOnly = HttpHeaders.readOnlyHttpHeaders(headers);
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return readOnly;
            }
        };
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
