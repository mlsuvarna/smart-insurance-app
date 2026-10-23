variable "name" {
  type = string
}
variable "master_username" {
  type = string
}
variable "instance_class" {
  type = string
}
variable "subnet_ids" {
  type = list(string)
}
variable "security_group_id" {
  type = string
}
resource "aws_db_subnet_group" "main" {
  name       = "smart-insurance-db"
  subnet_ids = var.subnet_ids
}
resource "aws_db_instance" "main" {
  identifier                 = "smart-insurance-${var.name}"
  engine                     = "mysql"
  engine_version             = "8.0"
  instance_class             = var.instance_class
  allocated_storage          = 20
  max_allocated_storage      = 100
  storage_encrypted          = true
  db_name                    = var.name
  username                   = var.master_username
  manage_master_user_password = true
  db_subnet_group_name       = aws_db_subnet_group.main.name
  vpc_security_group_ids     = [var.security_group_id]
  publicly_accessible        = false
  backup_retention_period    = 30
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "smart-insurance-${var.name}-final"
  auto_minor_version_upgrade = true
}
output "endpoint" {
  value = aws_db_instance.main.address
}
output "secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}
output "identifier" {
  value = aws_db_instance.main.identifier
}
