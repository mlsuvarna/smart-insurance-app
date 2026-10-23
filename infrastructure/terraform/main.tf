module "network" {
  source             = "./modules/network"
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  nat_gateway_count  = var.nat_gateway_count
}

module "security" {
  source           = "./modules/security"
  vpc_id           = module.network.vpc_id
  application_port = 8080
}

module "database" {
  source            = "./modules/database"
  name              = var.db_name
  master_username   = var.db_master_username
  instance_class    = var.db_instance_class
  subnet_ids        = module.network.private_subnet_ids
  security_group_id = module.security.database_security_group_id
}

module "storage" {
  source      = "./modules/storage"
  bucket_name = var.s3_bucket_name
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/smart-insurance/${var.environment}/application"
  retention_in_days = 30
}

module "compute" {
  source                       = "./modules/compute"
  aws_region                   = var.aws_region
  environment                  = var.environment
  ami_id                       = var.ami_id
  instance_type                = var.instance_type
  public_subnet_ids            = module.network.public_subnet_ids
  private_subnet_ids           = module.network.private_subnet_ids
  application_security_group_id = module.security.application_security_group_id
  load_balancer_security_group_id = module.security.load_balancer_security_group_id
  bucket_arn                   = module.storage.bucket_arn
  bucket_name                  = module.storage.bucket_name
  database_endpoint            = module.database.endpoint
  database_name                = var.db_name
  application_db_parameter_arn = var.application_db_parameter_arn
  jwt_parameter_arn            = var.jwt_parameter_arn
  pii_parameter_arn            = var.pii_parameter_arn
  kms_key_arn                  = var.kms_key_arn
  ecr_repository_arn           = var.ecr_repository_arn
  application_image            = var.application_image
  frontend_origin              = var.frontend_origin
  certificate_arn              = var.certificate_arn
  log_group_name               = aws_cloudwatch_log_group.application.name
}

module "monitoring" {
  source                    = "./modules/monitoring"
  environment               = var.environment
  alarm_email               = var.alarm_email
  load_balancer_arn_suffix  = module.compute.load_balancer_arn_suffix
  target_group_arn_suffix   = module.compute.target_group_arn_suffix
  autoscaling_group_name    = module.compute.autoscaling_group_name
  database_identifier       = module.database.identifier
}
