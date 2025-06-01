package com.xiaozhi.common.interceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class CozeRequestInterceptor implements WebFilter  {

    @Value("${coze.auth.token}")
    private String token;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getPath().value();
        if (path.startsWith("/api/coze/")) {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return Mono.empty();
            }
            String requestToken = authorization.substring(7);
            if (requestToken == null || !requestToken.equals(token)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return Mono.empty();
            }
        }
        return chain.filter(exchange);
    }

}
