# puckzone-gateway

Single entry point for **PuckZone**, a real-time multiplayer air hockey platform for Colombian
university students. Built with Spring Cloud Gateway (WebFlux), it routes all frontend traffic
to the backend microservices, validates JWTs locally, and applies per-IP rate limiting.

Part of a 6-microservice architecture (ARSW course project, Escuela Colombiana de Ingeniería
Julio Garavito, 2026-i): auth, matchmaking, game, ranking, **gateway**, and frontend.

## What it does

- **Routing** — forwards each request to the right microservice (see table below).
- **JWT validation** — verifies the token signature (HMAC, shared secret) and expiration
  locally on every protected route, without calling the auth service. Invalid or missing
  token → `401`.
- **Rate limiting** — max 100 requests per minute per client IP (fixed 1-minute window,
  in-memory). Exceeding it → `429` with a `Retry-After` header.
- **CORS** — handled centrally here for the whole system; downstream services must not
  emit their own CORS headers.

It has **no database and no business logic**.

## Routes

| Public path | Target service | Notes |
|---|---|---|
| `/api/auth/**` | auth (8081) | `POST /api/auth/register` and `POST /api/auth/login` are public; everything else requires a JWT |
| `/api/matching/**` | matchmaking (8082) | Path is rewritten to the service's internal `/queue/**` |
| `/api/game/**` | game (8083) | Requires JWT |
| `/api/ranking/**` | ranking (8084) | Requires JWT |
| `/ws/**` | game (8083) | WebSocket (SockJS/STOMP). Native WS upgrade proxied by the WebFlux gateway |

### WebSocket authentication

Browser SockJS clients cannot send an `Authorization` header during the handshake, so on
`/ws/**` the gateway also accepts the token as a query parameter:

```
http://localhost:8080/ws?token=<jwt>
```

Requests to `/actuator/health` (liveness/readiness probes) are served by the gateway itself
and are not subject to JWT validation or rate limiting.

## Filter chain

1. `RateLimitingFilter` (order `-200`) — cheap rejection first; also shields the public
   login endpoint from brute force.
2. `JwtAuthenticationFilter` (order `-100`) — local signature + expiration check (jjwt).
3. Route handling (path rewrite for matchmaking, WS upgrade for `/ws/**`).

## Configuration

All settings have local-development defaults and are overridable via environment variables
(intended for Azure Container Apps):

| Variable | Default | Purpose |
|---|---|---|
| `PUCKZONE_JWT_SECRET` | dev-only shared secret | HMAC key used to verify JWTs (must match auth) |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | auth service base URL |
| `MATCHMAKING_SERVICE_URL` | `http://localhost:8082` | matchmaking service base URL |
| `GAME_SERVICE_URL` | `http://localhost:8083` | game service base URL |
| `RANKING_SERVICE_URL` | `http://localhost:8084` | ranking service base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | comma-separated list of allowed origins |
| `RATE_LIMIT_PER_MINUTE` | `100` | requests per minute allowed per IP |

## Running locally

Requires **JDK 21** (Lombok breaks on newer JDKs).

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\mvnw.cmd spring-boot:run
```

The gateway listens on **port 8080**. Downstream services are expected on 8081-8084
(see the table above); routes to services that are not running simply fail on that route
without affecting the rest.

### Docker

```bash
docker build -t puckzone-gateway .
docker run -p 8080:8080 puckzone-gateway
```

Health check: `GET /actuator/health`.

## Tech stack

- Java 21, Spring Boot 4.1.x, Spring Cloud Gateway (WebFlux variant — chosen for native
  WebSocket proxying)
- [jjwt](https://github.com/jwtk/jjwt) for local JWT verification
- No database, no JPA
