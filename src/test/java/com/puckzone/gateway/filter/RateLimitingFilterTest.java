package com.puckzone.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ventana fija de 1 minuto por IP: el request N+1 recibe 429 con
 * Retry-After, sin afectar a las demás IPs. La IP real sale del primer
 * valor de X-Forwarded-For (en Azure el ingress hace de proxy).
 */
class RateLimitingFilterTest {

    private static final int LIMIT = 3;

    private RateLimitingFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(new InMemoryRateCounter(), LIMIT);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private MockServerWebExchange requestFrom(String ip) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ranking/global")
                        .header("X-Forwarded-For", ip).build());
    }

    @Test
    void elRequestQuePasaElLimiteRecibe429ConRetryAfter() {
        for (int i = 0; i < LIMIT; i++) {
            var exchange = requestFrom("10.0.0.1");
            filter.filter(exchange, chain).block();
            assertNull(exchange.getResponse().getStatusCode(), "el request " + (i + 1) + " debía pasar");
        }

        var bloqueado = requestFrom("10.0.0.1");
        filter.filter(bloqueado, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, bloqueado.getResponse().getStatusCode());
        assertNotNull(bloqueado.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void cadaIpTieneSuPropioContador() {
        for (int i = 0; i <= LIMIT; i++) {
            filter.filter(requestFrom("10.0.0.1"), chain).block();
        }

        // La otra IP no hereda el bloqueo de la primera.
        var otraIp = requestFrom("10.0.0.2");
        filter.filter(otraIp, chain).block();
        assertNull(otraIp.getResponse().getStatusCode());
    }

    @Test
    void usaElPrimerValorDeXForwardedFor() {
        // Cliente real 1.2.3.4 detrás de dos proxies: cuenta contra 1.2.3.4.
        for (int i = 0; i < LIMIT; i++) {
            filter.filter(requestFrom("1.2.3.4, 10.0.0.9"), chain).block();
        }
        var bloqueado = requestFrom("1.2.3.4");
        filter.filter(bloqueado, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, bloqueado.getResponse().getStatusCode());
    }
}
