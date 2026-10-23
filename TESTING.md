# Testing and validation

## Test source and coverage target

- JUnit 5/Mockito source covers registration/login/password change and JWT invalidation, customer KYC/PII masking, product/policy/payment rules, payment detail/history ownership, claim transitions/reasoned rejection/submit-only withdrawal/INFO_REQUESTED response, SLA pause/resume, queue membership and routing constraints, notifications, report metrics/date filters, valid document upload and CLEAN-only download.
- Testcontainers MySQL source exercises Flyway migrations and MySQL schema, authenticated product API and public OpenAPI access, admin-only functionality/reports, customer ownership and agent isolation, officer queue restrictions, the full claim lifecycle including initial and response document uploads, timestamped actor/reason history and database-enforced history immutability.
- JaCoCo writes `backend/target/site/jacoco/index.html` and enforces at least 70% line coverage for `com.smartinsurance.service` during `mvn verify`.
- **Coverage has not been measured.** Test compilation/execution, actual JaCoCo percentage, MySQL migrations/native queries, Testcontainers availability and the threshold result are **REQUIRES VM VERIFICATION**.
- Frontend tests include Vitest/Testing Library checks, but browser workflows and complete role/API acceptance remain VM verification.

## Commands

Run these only in the VM with Java 21, Maven, Node.js/npm, MySQL-compatible Testcontainers support and the approved dependency sources. No dependencies or tools were installed or downloaded on the current workstation.

```powershell
Set-Location .\backend
mvn clean test
mvn verify
Set-Location ..\frontend
npm install
npm ci
npm test
npm run build
Set-Location ..\infrastructure\terraform
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

`mvn verify` runs the Testcontainers tests when Docker is available and enforces the 70% service-layer JaCoCo gate. Do not replace MySQL-specific verification with H2. Generate `frontend\package-lock.json` with the VM `npm install` command, retain/review the generated lockfile, then use `npm ci` for reproducible frontend validation. Do not hand-create the lockfile.

## Current source-only validation status

No commands requiring Maven, frontend dependencies, MySQL, Docker, Terraform, AWS, or other unavailable tools were run. Earlier local checks established that Maven artifacts and frontend packages are not present. No coverage percentage or passing test/build result is claimed.

## VM / system acceptance checklist

- Register a customer; verify role `CUSTOMER` and BCrypt hash in MySQL.
- Bootstrap an administrator only with controlled environment variables; assign agent and customer roles and create their assignment.
- Confirm customer A cannot read customer B's policy/claim and an unassigned agent receives 403.
- Verify CUSTOMER, AGENT, CLAIMS_OFFICER and ADMIN endpoint access, including queue membership and assigned-customer boundaries.
- Create a policy using an active product and a valid claim type; verify eligibility dates and coverage amount.
- Exercise each claim transition and invalid transition, including rejection reason, submit-only withdrawal and approved-only settlement.
- Request information and respond; verify SLA pause duration/resume, officer notification and customer-safe timeline entries.
- Exercise payment PENDING/SUCCESS/FAILED/REFUNDED transitions and confirm customer roles cannot invoke mock processing.
- Upload malformed, mismatched, oversized and allowed files; verify document download requires `CLEAN` status. Verify production scanning reports `CLAMAV`; scanner outage must fail closed.
- Confirm generated package-lock consistency with `npm ci`; inspect and retain the generated lockfile.
- Run filtered admin reports against MySQL fixtures and compare counts/sums with direct SQL.
- Restore a backup into a separate database and test the documented RTO procedure.

No end-to-end, MySQL, Docker, Terraform, cloud, or browser workflow result is claimed by this document.
