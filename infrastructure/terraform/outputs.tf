output "application_url" {
  value = "https://${var.api_domain_name}"
}
output "database_endpoint" {
  value = module.database.endpoint
}
output "database_admin_secret_arn" {
  value = module.database.secret_arn
}
output "document_bucket" {
  value = module.storage.bucket_name
}
output "vpc_id" {
  value = module.network.vpc_id
}
