# Backup and recovery

## Targets

- Database: daily backup target with minimum 30-day retention.
- Recovery-time objective: four hours, subject to VM/cloud testing.
- Claim audit and policy records must be included in consistent database backups.
- S3 object durability/versioning is enabled by the Terraform source; confirm lifecycle and recovery requirements against the document retention schedule.

## AWS

The RDS module enables storage encryption and a 30-day automated-backup retention window and prevents accidental deletion. No RDS instance exists until Terraform is reviewed and applied by an authorized operator. Enable point-in-time recovery, alarm on backup failures, test restore to an isolated instance, and document KMS/key access.

## VM MySQL

Schedule a MySQL 8 logical or physical backup using least-privileged backup credentials; encrypt output before off-host transfer. Store keys separately. Example logical backup (adapt user/secret handling; do not put passwords in command history):

```powershell
mysqldump --single-transaction --routines --triggers --databases smart_insurance > smart-insurance.sql
```

Restore into a fresh isolated MySQL 8 database, verify Flyway version, table counts, foreign-key checks, and sample customer/claim histories, then record the elapsed recovery time. Do not test restore over the only production copy.

## Storage

Back up local document directories independently of MySQL and test metadata/object consistency. For S3, enable versioning and block public access; use lifecycle/archive/replication only after retention approval. No backup job or cloud recovery has been activated or tested by this source generation.
