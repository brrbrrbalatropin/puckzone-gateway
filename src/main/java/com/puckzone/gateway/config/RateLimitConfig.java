package com.puckzone.gateway.config;

import com.puckzone.gateway.filter.InMemoryRateCounter;
import com.puckzone.gateway.filter.RateCounter;
import com.puckzone.gateway.filter.RedisRateCounter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Dónde vive el contador del rate limit según
 * {@code puckzone.rate-limit.store} (mismo patrón que el store de
 * matchmaking): {@code memory} para desarrollo local con réplica única;
 * {@code redis} en Azure (lo inyecta Terraform) — obligatorio con 2+
 * réplicas o cada instancia contaría por su lado y el límite efectivo se
 * multiplicaría.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "puckzone.rate-limit.store", havingValue = "memory")
    RateCounter inMemoryRateCounter() {
        return new InMemoryRateCounter();
    }

    // matchIfMissing: si la property faltara en prod, mejor exigir Redis que
    // degradar en silencio a contadores por instancia.
    @Bean
    @ConditionalOnProperty(name = "puckzone.rate-limit.store", havingValue = "redis",
            matchIfMissing = true)
    RateCounter redisRateCounter(ReactiveStringRedisTemplate redis) {
        return new RedisRateCounter(redis);
    }
}
