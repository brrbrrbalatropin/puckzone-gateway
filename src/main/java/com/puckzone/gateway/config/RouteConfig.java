package com.puckzone.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Define las rutas del gateway hacia los 4 microservicios.
 * Solo matchmaking reescribe el path (público /api/matching → interno /queue);
 * los demás servicios exponen sus endpoints con el prefijo completo.
 * Las rutas /ws/** y /ws-{shard}/** cubren tanto el upgrade a WebSocket como
 * los transportes HTTP de SockJS.
 *
 * <p>Sharding de game por partida: cada shard es dueño de sus salas y el
 * socket DE PARTIDA debe conectarse al shard que matchmaking asignó —
 * /ws-{i} enruta al shard i de {@code puckzone.services.game-shards}
 * (reescrito al /ws interno del servicio). La ruta /ws sigue yendo al
 * shard 0: es el socket de lobby/chat/social (anclado ahí a propósito) y
 * la compatibilidad con clientes viejos.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator puckzoneRoutes(RouteLocatorBuilder builder,
                                       @Value("${puckzone.services.auth}") String authUrl,
                                       @Value("${puckzone.services.matchmaking}") String matchmakingUrl,
                                       @Value("${puckzone.services.game}") String gameUrl,
                                       @Value("${puckzone.services.ranking}") String rankingUrl,
                                       @Value("${puckzone.services.game-shards}") List<String> gameShardUrls) {
        var routes = builder.routes()
                .route("auth", r -> r.path("/api/auth/**").uri(authUrl))
                .route("matchmaking", r -> r.path("/api/matching/**")
                        .filters(f -> f.rewritePath("/api/matching(?<segment>/?.*)", "/queue${segment}"))
                        .uri(matchmakingUrl))
                .route("game", r -> r.path("/api/game/**").uri(gameUrl))
                .route("ranking", r -> r.path("/api/ranking/**").uri(rankingUrl))
                .route("game-ws", r -> r.path("/ws/**").uri(gameUrl))
                // Specs OpenAPI de los servicios internos para la Swagger UI
                // del gateway: /docs/{servicio}/v3/api-docs -> /v3/api-docs.
                // Publicas en el filtro JWT (documentacion, no datos).
                .route("auth-docs", r -> r.path("/docs/auth/**")
                        .filters(f -> f.rewritePath("/docs/auth(?<segment>/?.*)", "${segment}"))
                        .uri(authUrl))
                .route("matchmaking-docs", r -> r.path("/docs/matchmaking/**")
                        .filters(f -> f.rewritePath("/docs/matchmaking(?<segment>/?.*)", "${segment}"))
                        .uri(matchmakingUrl))
                .route("game-docs", r -> r.path("/docs/game/**")
                        .filters(f -> f.rewritePath("/docs/game(?<segment>/?.*)", "${segment}"))
                        .uri(gameUrl))
                .route("ranking-docs", r -> r.path("/docs/ranking/**")
                        .filters(f -> f.rewritePath("/docs/ranking(?<segment>/?.*)", "${segment}"))
                        .uri(rankingUrl));

        for (int i = 0; i < gameShardUrls.size(); i++) {
            String prefix = "/ws-" + i;
            String shardUrl = gameShardUrls.get(i);
            routes = routes.route("game-ws-" + i, r -> r.path(prefix + "/**")
                    .filters(f -> f.rewritePath(prefix + "(?<segment>/?.*)", "/ws${segment}"))
                    .uri(shardUrl));
        }
        return routes.build();
    }
}
