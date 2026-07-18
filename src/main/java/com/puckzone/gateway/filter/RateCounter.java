package com.puckzone.gateway.filter;

import reactor.core.publisher.Mono;

/**
 * Contador del rate limit por IP y ventana de 1 minuto. Dos implementaciones
 * elegidas por {@code puckzone.rate-limit.store}: en memoria (desarrollo
 * local, réplica única) y en Redis (producción: el límite es GLOBAL aunque
 * el gateway tenga varias réplicas — con contadores por instancia, N
 * réplicas multiplicarían el límite efectivo por N).
 */
public interface RateCounter {

    /**
     * Registra un request de la IP en la ventana dada.
     *
     * @return el total de requests de esa IP en la ventana, incluido este
     */
    Mono<Long> increment(String ip, long minuteWindow);
}
