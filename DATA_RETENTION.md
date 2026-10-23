# Data retention and privacy

- **Claim history/audit:** retain at least seven years from claim closure, subject to applicable legal holds and local regulation. MySQL triggers prevent update/delete of `claim_history`; archive with an approved, verifiable process rather than application deletion.
- **Documents:** retain per policy/regulatory schedule. S3 versioning is enabled by the Terraform module; non-current object versions expire after 2,555 days in the current example. Tune this lifecycle only after legal review; it does not replace a legal hold.
- **Customer profile/PII:** collect only required KYC data. Customer deletion must be a controlled privacy workflow that considers policy, claim, payment and audit retention. The current API does not implement account deletion, anonymization or a complete data-subject request flow.
- **Logs and notifications:** do not place tokens, passwords, raw uploads, national IDs, banking or medical data into logs/email. Configure retention in the deployed logging environment.

The current schema does not contain banking or medical fields. Any additions require an encryption/key-rotation and access review before use.
