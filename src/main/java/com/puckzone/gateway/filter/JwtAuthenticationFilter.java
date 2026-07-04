package com.puckzone.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Valida el JWT en todas las rutas salvo las públicas, ANTES de enrutar al
 * microservicio destino. La validación es local (firma HS256 con el secreto
 * compartido + expiración); nunca se llama a auth.
 *
 * El token llega en "Authorization: Bearer <jwt>", excepto en /ws/** donde
 * SockJS no puede enviar headers en el handshake y se acepta ?token=<jwt>.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** Corre después del rate limiting (-200) y antes de enrutar. */
    public static final int ORDER = -100;

    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/register", "/api/auth/login");
    private static final String WS_PREFIX = "/ws";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${puckzone.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest(), path);
        if (token == null || !isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private String extractToken(ServerHttpRequest request, String path) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        if (path.startsWith(WS_PREFIX)) {
            return request.getQueryParams().getFirst("token");
        }
        return null;
    }

    private boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
