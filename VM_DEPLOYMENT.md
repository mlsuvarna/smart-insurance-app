# VM deployment guide

This is a source deployment procedure. No VM, cloud services or database have been provisioned.

## 1. Install and verify prerequisites

Install through your VM's approved package source, then verify:

```powershell
java -version
mvn -version
node --version
npm --version
mysql --version
docker --version
```

Use Java 21, Maven 3.9+, Node.js 22/npm, MySQL 8, and optionally Docker Compose. Keep MySQL private; allow 3306 only from the application host/security group.

## 2. Configure MySQL 8

Create the application database and a least-privileged account. Use a secret prompt or a protected secret manager rather than a literal password in shell history:

```sql
CREATE DATABASE smart_insurance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'smart_app'@'<application-host>' IDENTIFIED BY '<strong-unique-secret>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER
  ON smart_insurance.* TO 'smart_app'@'<application-host>';
```

Flyway needs schema migration privileges at first startup; after migrations, reduce grants to application DML plus the minimum operational requirements. Review trigger privileges and reporting queries with your DBA. Never expose MySQL publicly.

## 3. Set application configuration

Set environment variables in the VM service manager/secret store:

```text
DB_URL=jdbc:mysql://<private-db-host>:3306/smart_insurance?serverTimezone=UTC
DB_USERNAME=<least-privileged-user>
DB_PASSWORD=<secret>
JWT_SECRET=<random secret with at least 32 bytes>
JWT_EXPIRATION_MINUTES=60
PII_ENCRYPTION_KEY=<base64-encoded random 32-byte key>
FRONTEND_URL=https://<portal-host>
CORS_ALLOWED_ORIGINS=https://<portal-host>
STORAGE_MODE=s3
AWS_REGION=<region>
S3_BUCKET=<private-bucket>
SCANNER_MODE=clamav
SCANNER_HOST=<private-clamav-host>
SCANNER_PORT=3310
MAX_FILE_SIZE_MB=10
MAX_REQUEST_SIZE_MB=11
MAX_DOCUMENTS_PER_CLAIM=10
DEFAULT_SLA_DAYS=10
EMAIL_MODE=log
# EMAIL_MODE=webhook requires EMAIL_WEBHOOK_URL and EMAIL_API_TOKEN for a configured production email service.
```

Set `SPRING_PROFILES_ACTIVE=prod` for a production-profile deployment. That profile defaults to S3 storage and the ClamAV scanner; configure credentials and private network endpoints before enabling it.

Generate fresh production keys in a secret manager. Back up the PII encryption key separately; losing it loses access to encrypted KYC values. Do not commit `.env` or put secrets into Docker build arguments, Terraform user data or logs. Configure a real TLS-terminating reverse proxy and set forwarded-header handling correctly.

For the first administrator only, set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (at least 16 characters) for one startup; then remove them. Admin registration is never public.

## 4. Build, migrate, and run without Docker

From the repository root:

```powershell
cd backend
mvn clean verify
mvn spring-boot:run
```

Flyway applies versioned MySQL migrations at startup; Hibernate uses `ddl-auto=validate`. Confirm `http://localhost:8080/actuator/health` and `http://localhost:8080/swagger-ui/index.html`. Do not use H2 as a replacement for production MySQL.

In another shell:

```powershell
cd frontend
npm install
$env:VITE_API_BASE_URL = "https://<api-host>/api/v1"
npm test
npm run build
```

The `npm install` step generates `frontend\package-lock.json`; retain and review that file, then run `npm ci` and repeat `npm test` and `npm run build` to verify lockfile reproducibility. No lockfile was manually fabricated.

Serve `frontend/dist` through the reviewed HTTPS web server. `VITE_API_BASE_URL` is a public endpoint, not a secret.

## 5. Docker Compose option

Copy `.env.example` to a protected local `.env`, set all required values, review `docker compose config`, then start only after operator approval:

```powershell
docker compose up --build
```

The Compose file uses MySQL 8.4 and local document volume mounts. It is intended for local/VM evaluation, not a production HA topology.

## 6. Network, roles, and operational validation

Expose only HTTPS (443) at the trusted proxy. Redirect or close plain HTTP in production. Keep backend and MySQL ports private. Configure firewall/security groups narrowly; do not permit public SSH or database access.

Run `mvn clean verify` in the backend; confirm Testcontainers integration tests pass and JaCoCo service-line coverage reaches at least 70%. Then verify CUSTOMER, AGENT, CLAIMS_OFFICER and ADMIN boundaries; the full claim state machine; rejection reason; withdrawal restriction; INFO_REQUESTED SLA pause/resume; queue routing and officer membership; immutable actor-role history; pending payment/mock admin transitions; ownership; and document CLEAN-only download. Register workflows through approved data or admin bootstrap. Configure reachable ClamAV and verify scanner outage fails closed before enabling production downloads. Confirm RDS/S3 policies and backups separately.

Common diagnostics: Flyway migration errors indicate a schema/privilege/version issue; Hibernate validation failure means entity and migration columns differ; JWT login signing fails if the configured key is shorter than 32 bytes; KYC update/decrypt needs a base64-encoded 32-byte encryption key; S3 requires bucket, region and instance/VM credentials; ClamAV connection errors fail uploads.
