package com.puckzone.gateway.filter;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Contador compartido en Redis (reactivo — el gateway es WebFlux y no puede
 * bloquear): {@code rl:{ip}:{ventana}} con INCR; el primer request de la
 * ventana le pone TTL de 2 minutos para que Redis limpie solo (el doble de
 * la ventana: sobra margen y no hay que barrer nada).
 */
public class RedisRateCounter implements RateCounter {

    private static final Duration WINDOW_TTL = Duration.ofMinutes(2);

    private final ReactiveStringRedisTemplate redis;

    public RedisRateCounter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Long> increment(String ip, long minuteWindow) {
        String key = "rl:" + ip + ":" + minuteWindow;
        return redis.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redis.expire(key, WINDOW_TTL).thenReturn(count)
                        : Mono.just(count));
    }
}
