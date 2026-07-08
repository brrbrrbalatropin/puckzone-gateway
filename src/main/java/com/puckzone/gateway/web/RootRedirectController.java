package com.puckzone.gateway.web;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * La raíz del gateway no enruta a ningún servicio: sin esto, abrirla en el
 * navegador muestra el Whitelabel 404 de Spring. Redirige al frontend, que
 * por convención es el PRIMER origen de puckzone.cors.allowed-origins
 * (en Azure la URL pública del frontend; en dev, localhost:5173).
 *
 * Al ser un controller y no una ruta del gateway, los GlobalFilter
 * (JWT y rate limit) no aplican aquí — igual que en /actuator/health.
 */
@RestController
public class RootRedirectController {

    private final URI frontendUri;

    public RootRedirectController(@Value("${puckzone.cors.allowed-origins}") List<String> allowedOrigins) {
        this.frontendUri = URI.create(allowedOrigins.getFirst());
    }

    @GetMapping("/")
    public Mono<Void> redirectToFrontend(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(frontendUri);
        return exchange.getResponse().setComplete();
    }
}
