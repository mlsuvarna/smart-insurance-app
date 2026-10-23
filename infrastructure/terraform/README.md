# AWS Terraform architecture

This source defines a cost-conscious development topology; it has **not** been initialized, validated, or deployed. It requires an existing, version-pinned backend image in ECR, a validated ACM certificate, externally managed DNS, encrypted SSM parameters, and a unique S3 bucket name.

## Network and traffic

```text
Browser -- HTTPS/443 --> public ALB -- HTTP/8080 --> private EC2 Auto Scaling Group
                         |                              |-- MySQL/3306 --> private RDS
                         |                              |-- HTTPS/443 --> S3, ECR, SSM, Secrets Manager,
                         |                              |                 CloudWatch (via NAT)
                         +-- HTTP/80 redirects to HTTPS
```

The VPC has public and private subnets in at least two availability zones. The ALB is public; backend instances and MySQL are private. Security groups expose only ALB ports 80/443, allow app port 8080 from the ALB security group, and allow MySQL 3306 only from the application security group. There is no inbound SSH rule; use SSM. One shared NAT gateway is the cost-conscious default and introduces cross-AZ dependency; set `nat_gateway_count` to the AZ count for production resilience and cost review. The ACM certificate must cover `api_domain_name`; create its DNS CNAME/alias to the ALB separately.

## Compute, secrets, and storage

The ASG uses a supplied Amazon Linux AMI and runs the version-pinned ECR backend image and a local ClamAV container. EC2 uses IMDSv2 and an instance profile. Its IAM policy grants only the configured ECR repository, the `claims/*` S3 prefix, specified encrypted SSM parameters/KMS key, and application CloudWatch log stream. RDS generates and manages its master password in Secrets Manager, which is reserved for controlled administration. Provision an application database account with only the schema/DML/trigger privileges Flyway requires, then store its username/password as an encrypted SSM JSON parameter (`{"username":"...","password":"..."}`); EC2 never receives the RDS master secret. JWT and PII encryption keys must also be provisioned as SecureString SSM parameters encrypted by `kms_key_arn`.

RDS MySQL is private, encrypted, deletion-protected, and configured for 30-day automated backups. S3 blocks public access, enforces TLS, enables server-side encryption and versioning, moves current objects to infrequent access after 90 days, and expires noncurrent versions after seven years. Keep document-retention policy aligned with business/legal obligations before deployment.

The backend container emits application logs to a 30-day CloudWatch log group. CloudWatch alarms cover unhealthy ALB targets, ALB 5xx responses, application CPU, and RDS CPU. An optional SNS email subscription requires confirmation. Frontend hosting is separate; point `frontend_origin` at the deployed React site and configure its API base URL to `https://api_domain_name`.

## State and operational safety

Copy `backend.tf.example` to `backend.tf` only after creating an encrypted, versioned remote-state bucket and a DynamoDB lock table. Restrict state access: plans contain infrastructure metadata and can include sensitive provider-computed values. Never commit state, `.tfvars`, credentials, or key material. Use a protected CI environment and review plans before any apply.

The EC2 user-data bootstrap pulls the supplied image and starts containers; it is not an application image build/publish pipeline. Confirm the ECR image, ACM certificate, DNS record, SSM parameters, NAT egress, CloudWatch alarms, and RDS connectivity before rollout. Diagnose bootstrap failures using SSM and the instance's cloud-init logs. Do not claim that backups, alarms, or deployments are active until applied and verified in AWS.

## Review commands

Run these only in an AWS-enabled VM after supplying non-secret infrastructure values and arranging the remote state backend. They inspect/plan source and do not authorize applying changes:

```powershell
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -var-file="dev.tfvars"
```

An apply requires a separate, explicit infrastructure approval and reviewed plan.
