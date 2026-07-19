package com.puckzone.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El portero de la plataforma: públicas sin token, el resto con JWT
 * válido (Bearer, o ?token= solo en /ws), y un refresh token NUNCA sirve
 * como credencial aunque su firma sea válida.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "puckzone-dev-secret-change-me-please-32bytes-min!!";

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String accessToken() {
        return Jwts.builder().subject("u1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
    }

    /** Deja pasar = el chain se invoca y el filtro no escribió status. */
    private void assertPasses(MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
        assertNull(exchange.getResponse().getStatusCode());
    }

    private void assertRejected(MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void lasRutasPublicasPasanSinToken() {
        assertPasses(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build()));
    }

    @Test
    void lasSpecsDeSwaggerSonPublicas() {
        assertPasses(MockServerWebExchange.from(
                MockServerHttpRequest.get("/docs/game/v3/api-docs").build()));
    }

    @Test
    void unaRutaProtegidaSinTokenDa401() {
        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global").build()));
    }

    @Test
    void elBearerValidoPasa() {
        assertPasses(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .header("Authorization", "Bearer " + accessToken()).build()));
    }

    @Test
    void unTokenFirmadoConOtroSecretoDa401() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "otro-secreto-cualquiera-de-al-menos-32-bytes!!!!".getBytes(StandardCharsets.UTF_8));
        String intruso = Jwts.builder().subject("u1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();

        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .header("Authorization", "Bearer " + intruso).build()));
    }

    @Test
    void unTokenVencidoDa401() {
        String vencido = Jwts.builder().subject("u1")
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key).compact();

        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .header("Authorization", "Bearer " + vencido).build()));
    }

    @Test
    void enWsSeAceptaElTokenPorQueryParam() {
        // SockJS no puede mandar headers en el handshake.
        assertPasses(MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/info").queryParam("token", accessToken()).build()));
    }

    @Test
    void wsSinTokenDa401() {
        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/info").build()));
    }

    @Test
    void elQueryParamNoSirveFueraDeWs() {
        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .queryParam("token", accessToken()).build()));
    }

    @Test
    void unRefreshTokenNoEsCredencialAunqueLaFirmaSeaValida() {
        String refresh = Jwts.builder().subject("u1")
                .claim("type", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000))
                .signWith(key).compact();

        assertRejected(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .header("Authorization", "Bearer " + refresh).build()));
    }
}
