package com.puckzone.gateway.filter;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Rate limiting por IP con ventana fija de 1 minuto. Corre antes del filtro
 * JWT: rechazar abuso es más barato que validar firmas, y así también
 * protege /api/auth/login (pública) contra fuerza bruta.
 *
 * <p>El contador vive detrás de {@link RateCounter}: en memoria en local y
 * en Redis en producción, donde el límite es GLOBAL entre las réplicas del
 * gateway. Si el contador falla (Redis caído), el filtro DEJA PASAR
 * (fail-open): perder el límite un rato es mejor que tumbar el único punto
 * de entrada de la plataforma.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    /** Corre antes del JwtAuthenticationFilter (-100). */
    public static final int ORDER = -200;

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateCounter counter;
    private final int requestsPerMinute;

    public RateLimitingFilter(RateCounter counter,
                              @Value("${puckzone.rate-limit.requests-per-minute}") int requestsPerMinute) {
        this.counter = counter;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long epochSecond = Instant.now().getEpochSecond();
        long minute = epochSecond / 60;

        return counter.increment(clientIp(exchange.getRequest()), minute)
                .flatMap(count -> {
                    if (count > requestsPerMinute) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders()
                                .add("Retry-After", String.valueOf(60 - epochSecond % 60));
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("Rate limit sin contador ({}): el request pasa sin limitar",
                            e.getMessage());
                    return chain.filter(exchange);
                });
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
