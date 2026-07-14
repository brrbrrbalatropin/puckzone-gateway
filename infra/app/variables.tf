variable "subscription_id" {
  description = "Suscripcion de Azure (identificador, no credencial)"
  type        = string
  default     = "d5860455-eb5f-4995-a11c-8be250730e90"
}

# El pipeline CI/CD es dueno del deploy de imagenes: aqui solo se fija la imagen
# inicial. El job de deploy del workflow actualiza el tag sin pasar por Terraform
# (por eso main.tf ignora cambios de imagen, ver lifecycle).
variable "image" {
  description = "Imagen inicial de la Container App"
  type        = string
  default     = "ghcr.io/brrbrrbalatropin/puckzone-gateway:latest"
}

variable "rate_limit_per_minute" {
  description = "Limite de requests por minuto POR IP en el gateway."
  type        = number
  # El limite es por IP y el NAT de la universidad saca a todo el salon por
  # una (o muy pocas) IP publicas: con el default del yaml (100) bastaban
  # 3-4 personas polleando matchmaking para tumbar a todos con 429.
  # 2000 cubre ~21 personas a ~60 req/min pico con margen.
  default = 2000
}

variable "cors_allowed_origins" {
  description = "Origenes permitidos para CORS (separados por coma)."
  type        = string
  # Frontend desplegado en Azure + Vite local para desarrollo.
  default     = "https://puckzone-frontend.calmgrass-8fe4a577.eastus.azurecontainerapps.io,http://localhost:5173"
}
