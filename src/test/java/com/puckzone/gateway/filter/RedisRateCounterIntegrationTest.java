package com.puckzone.gateway.filter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El contador compartido contra un Redis REAL (localhost:6379): la cuenta
 * crece de a uno por ventana, las ventanas son independientes, y dos
 * contadores (dos "réplicas" del gateway) ven LA MISMA cuenta — la garantía
 * que hace global el límite por IP. En CI el job levanta Redis como
 * service; sin Redis local la clase se salta (assumption).
 */
class RedisRateCounterIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void connectOrSkip() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        try {
            new StringRedisTemplate(factory).getConnectionFactory().getConnection().ping();
        } catch (RuntimeException e) {
            Assumptions.abort("Sin Redis en localhost:6379 — test de integración omitido");
        }
        redis = new ReactiveStringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void dosReplicasVenLaMismaCuentaYLasVentanasSonIndependientes() {
        // IP única por corrida: el test no depende de limpiar claves.
        String ip = "test-" + UUID.randomUUID();
        var replicaA = new RedisRateCounter(redis);
        var replicaB = new RedisRateCounter(redis);

        assertEquals(1, replicaA.increment(ip, 100).block());
        assertEquals(2, replicaB.increment(ip, 100).block(),
                "la cuenta debe ser global entre réplicas, no por instancia");
        assertEquals(3, replicaA.increment(ip, 100).block());

        assertEquals(1, replicaA.increment(ip, 101).block(),
                "la ventana siguiente arranca de cero");
        assertEquals(1, replicaA.increment("otra-" + ip, 100).block(),
                "cada IP tiene su propia cuenta");
    }
}
