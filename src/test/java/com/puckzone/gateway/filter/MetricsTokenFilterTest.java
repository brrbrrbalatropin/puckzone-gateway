package com.puckzone.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /actuator/prometheus exige el token estático (el gateway es público en
 * internet); el resto del tráfico —incluido /actuator/health, que usan los
 * probes de Azure— pasa sin token.
 */
class MetricsTokenFilterTest {

    private static final String TOKEN = "metrics-token-de-prueba";

    private MetricsTokenFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new MetricsTokenFilter(TOKEN);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void prometheusConElTokenCorrectoPasa() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).build());

        filter.filter(exchange, chain).block();

        assertNull(exchange.getResponse().getStatusCode(), "con el token debía pasar");
    }

    @Test
    void prometheusSinTokenRecibe401() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void prometheusConTokenAjenoRecibe401() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer otro-token").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void elHealthDeLosProbesNoExigeToken() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health/liveness").build());

        filter.filter(exchange, chain).block();

        assertNull(exchange.getResponse().getStatusCode(), "health debía pasar sin token");
    }
}
