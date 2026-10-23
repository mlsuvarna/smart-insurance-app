# Runtime report queries

Admin report SQL lives in `backend/src/main/java/com/smartinsurance/service/ReportService.java` and is parameterized through Spring JDBC. `database/assessment/reporting_queries.sql` provides companion MySQL examples. Verify aggregates against the report definitions before business use; product margin is a simplified premiums-minus-claims approximation, not actuarial profitability.
