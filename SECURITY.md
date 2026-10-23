# Security controls and deployment notes

## Authentication and authorization

The API uses stateless bearer JWTs, BCrypt cost 12 password hashes, authenticated-by-default routing and method authorization. Tokens snapshot a user's `token_version`; password changes increment it and invalidate previously issued JWTs on subsequent validation. Public registration always issues a CUSTOMER role. A one-time environment-controlled admin bootstrap requires both email and a 16-character minimum password; remove bootstrap values after first use. Claim/policy/document actions perform ownership, assignment, and claims-queue membership checks on the server. UI route hiding is convenience only.

JWT signing has no production fallback: `JWT_SECRET` must be at least 32 bytes. Production secrets must be stored in a secret manager, not in source, Docker image, Terraform user data or command history. JWTs are held in browser `sessionStorage`, which is vulnerable to script injection; deploy a strict CSP and consider an httpOnly secure-cookie architecture before production.

## PII, encryption and logging

KYC address, phone and national-ID values use AES-GCM application encryption with a fresh 96-bit IV and a 256-bit base64-encoded `PII_ENCRYPTION_KEY`. The key is not stored with ciphertext. Back it up securely; loss makes encrypted profile values unrecoverable. Responses mask phone/national-ID values. `full_name` and email remain database-visible PII and require encrypted database/storage and TLS protections. No database credential or token should be logged.

Use TLS at a reverse proxy/load balancer for production. Configure allowed CORS origins narrowly. Avoid request-body logging on authentication, profile, claim, payment and upload paths. Correlation IDs are generated or validated and placed in MDC; database audit correlation IDs are captured where an audit service writes the event.

## Documents

The backend enforces size/count, extension/content-type match, file signatures, generated storage keys and access checks. DOCX uploads receive bounded ZIP entry/path/expanded-size inspection. Files live outside MySQL in local storage or a private S3 bucket. Downloads require scan state `CLEAN`. The development-only scanner always returns `FAILED` and is explicitly not malware protection. Production profile selects the ClamAV INSTREAM adapter; configure `SCANNER_HOST`, `SCANNER_PORT`, network controls and scanner availability. Scanner failures fail the upload request and do not make documents downloadable. Verify production scanner outage behavior and storage compensation in the VM.

S3 bucket public access is blocked and server-side encryption is enabled. EC2 instance IAM permits object get/put on the claims prefix. The generated EC2 role is not yet granted RDS secret retrieval; configure secrets access with a narrowly scoped Secrets Manager policy in a reviewed deployment.

## Audit, payments and verification limits

Claim state/history writes are transactional and MySQL migrations block updates/deletes of claim history. Premium payments are explicitly mock-only: customer requests create PENDING records; authorized administrators drive PENDING→SUCCESS/FAILED and SUCCESS→REFUNDED transitions. No card/bank credentials are accepted and no real payment gateway is integrated. Notification email uses a safe local log adapter unless a production notifier is configured. Test sources cover role/ownership, queue restrictions, lifecycle and CLEAN-only download behavior; execution, coverage measurement and all MySQL/production scanner checks still require VM verification.

Use a minimum 7-year claim-history retention, least-privileged database/app users, encrypted backups, vulnerability scanning and a protected deployment approval process. No cloud resources have been created.
