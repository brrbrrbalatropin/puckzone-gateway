package com.puckzone.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Protege /actuator/prometheus con un token estático (Bearer). El gateway es
 * la única app con ingress EXTERNO: sin esto sus métricas quedarían públicas
 * en internet (rutas, latencias, internals de la JVM). Es un {@link WebFilter}
 * y no un GlobalFilter porque el actuator se sirve localmente, fuera de las
 * rutas del gateway (los GlobalFilter no aplican ahí). El Prometheus interno
 * manda el token vía {@code authorization.credentials_file} de su scrape config.
 */
@Component
public class MetricsTokenFilter implements WebFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public MetricsTokenFilter(@Value("${puckzone.metrics.token}") String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/actuator/prometheus")) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && MessageDigest.isEqual(expectedToken,
                        header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8))) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
