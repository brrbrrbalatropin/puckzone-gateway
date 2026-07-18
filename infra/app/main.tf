resource "azurerm_container_app" "gateway" {
  name                         = "puckzone-gateway"
  resource_group_name          = data.terraform_remote_state.base.outputs.resource_group_name
  container_app_environment_id = data.terraform_remote_state.base.outputs.container_app_environment_id
  revision_mode                = "Single"

  # El gateway valida JWT localmente con el secreto compartido de produccion
  # (el mismo con el que auth firma), leido del remote state de infra/base.
  secret {
    name  = "jwt-secret"
    value = data.terraform_remote_state.base.outputs.jwt_secret
  }

  # Token que el MetricsTokenFilter exige en /actuator/prometheus (el gateway
  # es publico); el Prometheus interno manda el mismo token en su scrape.
  secret {
    name  = "metrics-token"
    value = data.terraform_remote_state.base.outputs.metrics_token
  }

  template {
    # 2 replicas: el UNICO punto de entrada de la plataforma deja de ser
    # punto unico de falla (y era el cuello de ~200 partidas en la prueba
    # al limite de 2026-07-18). El rate limit por IP sigue siendo global:
    # el contador vive en el Redis del environment (RATE_LIMIT_STORE=redis).
    min_replicas = 2
    max_replicas = 2

    container {
      # 0.5/1Gi como el resto: con menos CPU el arranque de Spring supera los
      # ~30s y la liveness probe mata el contenedor antes de abrir el puerto.
      name   = "gateway"
      image  = var.image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name        = "PUCKZONE_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      env {
        name        = "PUCKZONE_METRICS_TOKEN"
        secret_name = "metrics-token"
      }
      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = var.cors_allowed_origins
      }
      env {
        name  = "RATE_LIMIT_PER_MINUTE"
        value = tostring(var.rate_limit_per_minute)
      }
      # Servicios internos del environment: HTTP plano por nombre de app
      # (allow_insecure_connections en sus ingress; el cert de .internal. no
      # es confiable para Java, ver decision 2026-07-08).
      env {
        name  = "AUTH_SERVICE_URL"
        value = "http://puckzone-auth"
      }
      env {
        name  = "MATCHMAKING_SERVICE_URL"
        value = "http://puckzone-matchmaking"
      }
      env {
        name  = "GAME_SERVICE_URL"
        value = "http://puckzone-game"
      }
      # Shards de game EN ORDEN (la posicion i atiende /ws-{i}). Debe coincidir
      # con el GAME_SHARD_URLS de matchmaking y con locals.game_shards del
      # infra/app de puckzone-game (output game_shard_urls de game.tfstate).
      env {
        name  = "GAME_SHARD_URLS"
        value = "http://puckzone-game,http://puckzone-game-1"
      }
      env {
        name  = "RANKING_SERVICE_URL"
        value = "http://puckzone-ranking"
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = data.terraform_remote_state.base.outputs.application_insights_connection_string
      }
      # Contador del rate limit en el Redis del environment (al final de la
      # lista: el diff de env de Container Apps es posicional).
      env {
        name  = "RATE_LIMIT_STORE"
        value = "redis"
      }
      env {
        name  = "SPRING_DATA_REDIS_HOST"
        value = data.terraform_remote_state.base.outputs.redis_host
      }
      env {
        name  = "SPRING_DATA_REDIS_PORT"
        value = "6379"
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/liveness"
        # Margen para el arranque de Spring; sin esto la probe empieza a fallar
        # de inmediato y ACA reinicia el contenedor en bucle.
        initial_delay = 20
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
        initial_delay = 10
      }
    }
  }

  # EXTERNO: unico punto de entrada de la plataforma desde internet (frontend).
  # HTTPS obligatorio hacia afuera (allow_insecure en false, su default): ACA
  # redirige http->https y el cert del dominio publico es valido.
  # transport auto soporta los WebSockets de /ws (SockJS/STOMP proxy a game).
  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  lifecycle {
    # El pipeline actualiza la imagen con az containerapp update; sin esto,
    # cada terraform apply intentaria devolver la app a la imagen inicial.
    ignore_changes = [template[0].container[0].image]
  }
}
