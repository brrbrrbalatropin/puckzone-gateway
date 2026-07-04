package com.puckzone.gateway.filter;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limiting en memoria por IP con ventana fija de 1 minuto.
 * Corre antes del filtro JWT: rechazar abuso es más barato que validar firmas,
 * y así también protege /api/auth/login (pública) contra fuerza bruta.
 *
 * En memoria es suficiente porque el gateway corre como instancia única;
 * si algún día escala horizontalmente, migrar al RequestRateLimiter con Redis.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    /** Corre antes del JwtAuthenticationFilter (-100). */
    public static final int ORDER = -200;

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastSeenMinute = new AtomicLong(-1);

    public RateLimitingFilter(@Value("${puckzone.rate-limit.requests-per-minute}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long epochSecond = Instant.now().getEpochSecond();
        long minute = epochSecond / 60;
        purgeOldWindows(minute);

        String key = clientIp(exchange.getRequest()) + ":" + minute;
        int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > requestsPerMinute) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(60 - epochSecond % 60));
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    /** El primer request de cada minuto barre los contadores de ventanas anteriores. */
    private void purgeOldWindows(long currentMinute) {
        if (lastSeenMinute.getAndSet(currentMinute) != currentMinute) {
            String suffix = ":" + currentMinute;
            counters.keySet().removeIf(key -> !key.endsWith(suffix));
        }
    }

    /**
     * En Azure el ingress hace de proxy: la IP real del cliente viene en
     * X-Forwarded-For (primer valor). En local se usa la IP de la conexión.
     */
    private String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
