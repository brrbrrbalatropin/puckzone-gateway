output "gateway_url" {
  description = "URL publica de la plataforma; el frontend apunta aqui (API en /api/*, WebSockets en /ws)"
  value       = "https://${azurerm_container_app.gateway.ingress[0].fqdn}"
}
