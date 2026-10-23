variable "aws_region" { type = string }
variable "environment" { type = string }
variable "ami_id" { type = string }
variable "instance_type" { type = string }
variable "public_subnet_ids" { type = list(string) }
variable "private_subnet_ids" { type = list(string) }
variable "application_security_group_id" { type = string }
variable "load_balancer_security_group_id" { type = string }
variable "bucket_arn" { type = string }
variable "bucket_name" { type = string }
variable "database_endpoint" { type = string }
variable "database_name" { type = string }
variable "application_db_parameter_arn" { type = string }
variable "jwt_parameter_arn" { type = string }
variable "pii_parameter_arn" { type = string }
variable "kms_key_arn" { type = string }
variable "ecr_repository_arn" { type = string }
variable "application_image" { type = string }
variable "frontend_origin" { type = string }
variable "certificate_arn" { type = string }
variable "log_group_name" { type = string }

resource "aws_iam_role" "instance" {
  name = "smart-insurance-${var.environment}-ec2-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "application" {
  name = "smart-insurance-${var.environment}-runtime-access"
  role = aws_iam_role.instance.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:DeleteObjectVersion"]
        Resource = "${var.bucket_arn}/claims/*"
      },
      {
        Effect = "Allow"
        Action = ["ssm:GetParameter"]
        Resource = [var.application_db_parameter_arn, var.jwt_parameter_arn, var.pii_parameter_arn]
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = var.kms_key_arn
      },
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = var.ecr_repository_arn
      },
      {
        Effect = "Allow"
        Action = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:${var.aws_region}:*:log-group:${var.log_group_name}:log-stream:*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "instance" {
  name = "smart-insurance-${var.environment}-instance-profile"
  role = aws_iam_role.instance.name
}

resource "aws_launch_template" "application" {
  name_prefix   = "smart-insurance-${var.environment}-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  user_data = base64encode(<<-USERDATA
    #!/bin/bash
    set -euo pipefail
    dnf install -y docker awscli
    systemctl enable --now docker

    aws ecr get-login-password --region ${var.aws_region} \
      | docker login --username AWS --password-stdin ${split("/", var.application_image)[0]}

    APP_DB_SECRET="$(aws ssm get-parameter --with-decryption \
      --name '${var.application_db_parameter_arn}' --query Parameter.Value --output text)"
    DB_USERNAME="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["username"])' <<< "$APP_DB_SECRET")"
    DB_PASSWORD="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])' <<< "$APP_DB_SECRET")"
    JWT_SECRET="$(aws ssm get-parameter --with-decryption \
      --name '${var.jwt_parameter_arn}' --query Parameter.Value --output text)"
    PII_ENCRYPTION_KEY="$(aws ssm get-parameter --with-decryption \
      --name '${var.pii_parameter_arn}' --query Parameter.Value --output text)"

    docker pull ${var.application_image}
    docker pull clamav/clamav:stable
    docker run -d --name clamav --restart unless-stopped --network host clamav/clamav:stable
    CLAMAV_READY=false
    for attempt in $(seq 1 60); do
      if (echo > /dev/tcp/127.0.0.1/3310) >/dev/null 2>&1; then CLAMAV_READY=true; break; fi
      sleep 5
    done
    if [ "$CLAMAV_READY" != true ]; then
      docker logs clamav
      exit 1
    fi
    docker run -d --name smart-insurance-api --restart unless-stopped \
      --network host \
      --log-driver awslogs \
      --log-opt awslogs-region=${var.aws_region} \
      --log-opt awslogs-group=${var.log_group_name} \
      --log-opt awslogs-stream=api-$(hostname) \
      --env SPRING_PROFILES_ACTIVE=prod \
      --env DB_URL="jdbc:mysql://${var.database_endpoint}:3306/${var.database_name}?serverTimezone=UTC" \
      --env DB_USERNAME="$DB_USERNAME" \
      --env DB_PASSWORD="$DB_PASSWORD" \
      --env JWT_SECRET="$JWT_SECRET" \
      --env PII_ENCRYPTION_KEY="$PII_ENCRYPTION_KEY" \
      --env AWS_REGION=${var.aws_region} \
      --env S3_BUCKET=${var.bucket_name} \
      --env STORAGE_MODE=s3 \
      --env SCANNER_MODE=clamav \
      --env SCANNER_HOST=127.0.0.1 \
      --env SCANNER_PORT=3310 \
      --env CORS_ALLOWED_ORIGINS=${var.frontend_origin} \
      --env FRONTEND_URL=${var.frontend_origin} \
      --env EMAIL_MODE=log \
      ${var.application_image}
  USERDATA
  )
  iam_instance_profile {
    name = aws_iam_instance_profile.instance.name
  }
  vpc_security_group_ids = [var.application_security_group_id]
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      encrypted   = true
      volume_size = 20
      volume_type = "gp3"
    }
  }
  tag_specifications {
    resource_type = "instance"
    tags = { Name = "smart-insurance-${var.environment}-api" }
  }
}

resource "aws_lb" "application" {
  name               = "smart-insurance-${var.environment}"
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [var.load_balancer_security_group_id]
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "application" {
  name        = "smart-insurance-${var.environment}-api"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.selected.id
  target_type = "instance"
  health_check {
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

data "aws_subnet" "private" {
  id = var.private_subnet_ids[0]
}
data "aws_vpc" "selected" {
  id = data.aws_subnet.private.vpc_id
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.application.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.application.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.application.arn
  }
}

resource "aws_autoscaling_group" "application" {
  name                = "smart-insurance-${var.environment}-api"
  min_size            = 1
  desired_capacity    = 1
  max_size            = 2
  vpc_zone_identifier = var.private_subnet_ids
  target_group_arns   = [aws_lb_target_group.application.arn]
  health_check_type   = "ELB"
  health_check_grace_period = 600
  launch_template {
    id      = aws_launch_template.application.id
    version = "$Latest"
  }
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
    }
  }
}

resource "aws_autoscaling_policy" "cpu" {
  name                   = "smart-insurance-${var.environment}-cpu"
  autoscaling_group_name = aws_autoscaling_group.application.name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 65
  }
}

output "load_balancer_dns_name" {
  value = aws_lb.application.dns_name
}
output "load_balancer_arn_suffix" {
  value = aws_lb.application.arn_suffix
}
output "target_group_arn_suffix" {
  value = aws_lb_target_group.application.arn_suffix
}
output "autoscaling_group_name" {
  value = aws_autoscaling_group.application.name
}
