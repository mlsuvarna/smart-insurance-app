# Smart Insurance Claim Management

A cloud-oriented monorepo starter for customer policy management and insurance-claim processing. It contains a Java 21/Spring Boot REST API, React/Vite portal, MySQL 8 schema managed by Flyway, local/S3 document storage adapters, Docker Compose, CI workflows, AWS Terraform and operator documentation.

## Architecture and modules

- `backend/`: API, stateless JWT security, JPA domain, workflow services, migrations and tests.
- `frontend/`: responsive React single-page portal with customer and role-aware views.
- `database/assessment/`: MySQL query/report examples; runtime DDL remains under backend Flyway migrations.
- `infrastructure/terraform/`: reusable AWS VPC, private RDS, EC2, private S3 and CloudWatch modules.
- `.github/workflows/`: backend/frontend checks, dependency scan and Terraform validation.

Claim transitions are enforced in `ClaimTransitionValidator` and `ClaimService`; configurable queues route claim types, SLA pause time is recorded on `Claim`, and transaction-scoped `ClaimHistory` captures actor-role/transition snapshots. Customer KYC fields are AES-GCM encrypted by `AesGcmPiiCipher`. Documents use metadata in MySQL and binaries in local storage or S3. Development scanning fails closed and is not malware protection; production uses the ClamAV adapter.

## Local setup

Requirements: Java 21, Maven 3.9+, Node.js 22+, npm, and MySQL 8. MySQL is not installed on the current Windows workstation; Docker/MySQL validation has not been performed. Do not use an alternate database for production configuration.

1. Create a local MySQL database and a least-privileged account as described in [VM_DEPLOYMENT.md](VM_DEPLOYMENT.md).
2. Copy `.env.example` to `.env`; replace all placeholders with locally generated secrets. Do not commit `.env`.
3. Configure environment variables in the current shell (PowerShell example):

   ```powershell
   $env:DB_URL = "jdbc:mysql://localhost:3306/smart_insurance?serverTimezone=UTC"
   $env:DB_USERNAME = "smart_app"
   $env:DB_PASSWORD = "<local database password>"
   $env:JWT_SECRET = "<at least 32 random bytes>"
   $env:PII_ENCRYPTION_KEY = "<base64-encoded 32-byte key>"
   $env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
   ```

4. Start backend (Flyway migrates the database at startup):

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

5. In another terminal:

   ```powershell
   cd frontend
   npm install
   $env:VITE_API_BASE_URL = "http://localhost:8080/api/v1"
   npm run dev
   ```

Public registration creates customers only. To bootstrap the first admin, set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (minimum 16 characters) once before backend startup; remove them afterward. Privileged account provisioning must be controlled and audited.

Default URLs: frontend `http://localhost:5173`; API `http://localhost:8080`; Swagger `http://localhost:8080/swagger-ui/index.html`; health `http://localhost:8080/actuator/health`.

## Docker Compose

Copy and secure `.env`, set database/JWT/PII values, then run `docker compose config` for a static configuration check. Starting containers is intentionally left for an approved environment. Compose uses MySQL 8.4, local persisted document storage and required environment-provided secrets; it does not connect to a payment gateway.

## Build and tests

```powershell
cd backend
mvn clean verify
cd ..\frontend
npm install
npm test
npm run build
```

The backend `verify` phase runs Checkstyle, MySQL Testcontainers integration tests when Docker is available, and a JaCoCo 70% service-line coverage gate. The gate's measured result has not been determined; all checks must execute in the VM. Generate and retain `frontend/package-lock.json` by running `npm install` in the VM, then use `npm ci` for repeatable frontend checks. Do not fabricate the lockfile. See [TESTING.md](TESTING.md).

## Security and limitations

JWT signing requires a strong environment-provided key; password changes revoke prior JWTs by incrementing the token version. KYC writes require an AES key. Production profile switches document scanning to ClamAV; development scanning always marks results FAILED and **is not malware detection**. Configure `SCANNER_HOST`/`SCANNER_PORT` and a reachable ClamAV daemon before uploads in production. S3 storage requires AWS SDK default credentials and a private bucket. Payment processing is explicitly internal/mock and is not a gateway.

This source has not been build-, test-, coverage-, or deployment-certified. JaCoCo measurement, Maven/MySQL/Testcontainers, frontend checks, lockfile generation, browser workflows, and infrastructure validation require VM verification. See [REQUIREMENTS_TRACEABILITY.md](REQUIREMENTS_TRACEABILITY.md) and [SECURITY.md](SECURITY.md) before use with real customer data.

## Deployment

See [VM_DEPLOYMENT.md](VM_DEPLOYMENT.md) for VM installation/run steps and [infrastructure/terraform/README.md](infrastructure/terraform/README.md) for AWS infrastructure. Terraform is source only; no apply/cloud operations are performed.
