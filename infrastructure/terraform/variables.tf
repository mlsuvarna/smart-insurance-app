variable "aws_region" {
  type    = string
  default = "ap-south-1"
}
variable "environment" {
  type    = string
  default = "dev"
}
variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}
variable "availability_zones" {
  type    = number
  default = 2
}
variable "nat_gateway_count" {
  type        = number
  default     = 1
  description = "One shared NAT gateway is the cost-conscious development default; use one per AZ for production."
}
variable "instance_type" {
  type    = string
  default = "t3.small"
}
variable "ami_id" {
  type        = string
  description = "Current supported Amazon Linux AMI ID for the selected region."
}
variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"
}
variable "db_name" {
  type    = string
  default = "smart_insurance"
}
variable "db_master_username" {
  type    = string
  default = "smart_admin"
}
variable "certificate_arn" {
  type        = string
  description = "ARN of a validated ACM certificate in the selected AWS region."
}
variable "api_domain_name" {
  type        = string
  description = "DNS name on the ACM certificate, pointed to the load balancer using a CNAME or alias."
}
variable "frontend_origin" {
  type        = string
  description = "HTTPS origin allowed to call the API, for example the deployed React site."
}
variable "application_image" {
  type        = string
  description = "Version-pinned backend container image URI in ECR."
}
variable "ecr_repository_arn" {
  type        = string
  description = "ARN of the ECR repository containing the backend image."
}
variable "jwt_parameter_arn" {
  type        = string
  description = "ARN of the encrypted SSM parameter containing the JWT signing secret."
}
variable "application_db_parameter_arn" {
  type        = string
  description = "ARN of an encrypted SSM JSON parameter with least-privilege app database username/password."
}
variable "pii_parameter_arn" {
  type        = string
  description = "ARN of the encrypted SSM parameter containing the base64 PII key."
}
variable "kms_key_arn" {
  type        = string
  description = "Customer-managed KMS key used to encrypt the SSM parameters."
}
variable "alarm_email" {
  type        = string
  description = "Optional email endpoint for SNS alarms; subscription must be confirmed."
  default     = ""
}
variable "s3_bucket_name" {
  type        = string
  description = "Globally unique private object bucket name."
}
