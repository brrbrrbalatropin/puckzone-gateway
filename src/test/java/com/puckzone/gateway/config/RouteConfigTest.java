package com.puckzone.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * La tabla de rutas del sharding de game: /ws-{i} debe apuntar al shard i
 * de puckzone.services.game-shards (en orden), y /ws debe seguir yendo al
 * game "clasico" (shard 0): es el socket de lobby/chat y la compatibilidad
 * hacia atras.
 */
@SpringBootTest(properties =
        "puckzone.services.game-shards=http://localhost:8083,http://localhost:8085")
class RouteConfigTest {

    @Autowired
    @Qualifier("puckzoneRoutes")
    private RouteLocator routes;

    @Test
    void cadaShardTieneSuRutaWsEnOrden() {
        Map<String, String> uriByRouteId = routes.getRoutes()
                .collectMap(Route::getId, route -> route.getUri().toString())
                .block();

        assertNotNull(uriByRouteId);
        assertEquals("http://localhost:8083", uriByRouteId.get("game-ws-0"),
                "/ws-0 debe ir al primer shard de la lista");
        assertEquals("http://localhost:8085", uriByRouteId.get("game-ws-1"),
                "/ws-1 debe ir al segundo shard de la lista");
        assertEquals("http://localhost:8083", uriByRouteId.get("game-ws"),
                "/ws (lobby/chat y compatibilidad) sigue yendo al shard 0");
    }
}
