variable "vpc_id" { type = string }
variable "application_port" { type = number }

resource "aws_security_group" "load_balancer" {
  name        = "smart-insurance-load-balancer"
  description = "Public HTTP redirect and HTTPS entry point"
  vpc_id      = var.vpc_id
  egress      = []
}

resource "aws_security_group" "application" {
  name        = "smart-insurance-application"
  description = "Application traffic only from the load balancer"
  vpc_id      = var.vpc_id
  egress      = []
}

resource "aws_security_group" "database" {
  name        = "smart-insurance-database"
  description = "MySQL available only from application security group"
  vpc_id      = var.vpc_id
  egress      = []
}

resource "aws_vpc_security_group_ingress_rule" "load_balancer_http" {
  security_group_id = aws_security_group.load_balancer.id
  description       = "HTTP redirect to TLS"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "load_balancer_https" {
  security_group_id = aws_security_group.load_balancer.id
  description       = "HTTPS application traffic"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "load_balancer_to_application" {
  security_group_id            = aws_security_group.load_balancer.id
  description                  = "Forward TLS-terminated traffic to the application"
  referenced_security_group_id = aws_security_group.application.id
  from_port                    = var.application_port
  to_port                      = var.application_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "application_from_load_balancer" {
  security_group_id            = aws_security_group.application.id
  description                  = "Application traffic from the load balancer"
  referenced_security_group_id = aws_security_group.load_balancer.id
  from_port                    = var.application_port
  to_port                      = var.application_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "application_outbound" {
  security_group_id = aws_security_group.application.id
  description       = "Outbound access through private subnet routes"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "database_from_application" {
  security_group_id            = aws_security_group.database.id
  description                  = "MySQL from application instances only"
  referenced_security_group_id = aws_security_group.application.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}

output "application_security_group_id" {
  value = aws_security_group.application.id
}
output "load_balancer_security_group_id" {
  value = aws_security_group.load_balancer.id
}
output "database_security_group_id" {
  value = aws_security_group.database.id
}
