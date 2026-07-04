package com.puckzone.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Define las rutas del gateway hacia los 4 microservicios.
 * No reescribe paths: cada servicio expone sus endpoints con el prefijo completo.
 * La ruta /ws/** cubre tanto el upgrade a WebSocket como los transportes HTTP de SockJS.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator puckzoneRoutes(RouteLocatorBuilder builder,
                                       @Value("${puckzone.services.auth}") String authUrl,
                                       @Value("${puckzone.services.matchmaking}") String matchmakingUrl,
                                       @Value("${puckzone.services.game}") String gameUrl,
                                       @Value("${puckzone.services.ranking}") String rankingUrl) {
        return builder.routes()
                .route("auth", r -> r.path("/api/auth/**").uri(authUrl))
                .route("matchmaking", r -> r.path("/api/matching/**").uri(matchmakingUrl))
                .route("game", r -> r.path("/api/game/**").uri(gameUrl))
                .route("ranking", r -> r.path("/api/ranking/**").uri(rankingUrl))
                .route("game-ws", r -> r.path("/ws/**").uri(gameUrl))
                .build();
    }
}
