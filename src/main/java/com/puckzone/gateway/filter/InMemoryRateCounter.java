package com.puckzone.gateway.filter;

import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador en memoria (desarrollo local y tests, réplica única): mapa
 * IP:ventana → contador, con barrido de ventanas viejas al cambiar de
 * minuto — la misma mecánica que tenía el filtro antes del seam.
 */
public class InMemoryRateCounter implements RateCounter {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastSeenMinute = new AtomicLong(-1);

    @Override
    public Mono<Long> increment(String ip, long minuteWindow) {
        purgeOldWindows(minuteWindow);
        String key = ip + ":" + minuteWindow;
        return Mono.just((long) counters.computeIfAbsent(key, k -> new AtomicInteger())
                .incrementAndGet());
    }

    /** El primer request de cada minuto barre los contadores de ventanas anteriores. */
    private void purgeOldWindows(long currentMinute) {
        if (lastSeenMinute.getAndSet(currentMinute) != currentMinute) {
            String suffix = ":" + currentMinute;
            counters.keySet().removeIf(key -> !key.endsWith(suffix));
        }
    }
}
