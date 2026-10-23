# MySQL performance notes

Use `EXPLAIN`/`EXPLAIN ANALYZE` on representative, parameterized claim and payment queries. Indexes in Flyway cover customer timelines, officer queues, status/SLA lookup, and payment history. Select DTO projections and paginate list endpoints as data volumes grow.

Partitioning is a future DBA-managed option, not enabled by default: time partitioning claim history by `created_at` requires including the partition key in unique keys and preserving foreign-key semantics. MySQL limitations around foreign keys and partitioned tables make a tested archival-table strategy more appropriate before partitioning. Retain audit history for at least seven years and never drop it solely to improve query speed.

The scripts in this directory are educational demonstrations; the Flyway migrations are the application runtime schema source. Procedures and functions are not called by the app and must be reviewed against least-privilege database roles before deployment.
