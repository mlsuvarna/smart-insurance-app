-- Top 10 customers by recorded premium.
SELECT u.id, u.full_name, SUM(pay.amount) AS premium_total
FROM users u JOIN payments pay ON pay.customer_id = u.id AND pay.status = 'SUCCESS'
GROUP BY u.id, u.full_name ORDER BY premium_total DESC LIMIT 10;

-- Settled-claim ratio (settled / all closed decisions).
SELECT COUNT(CASE WHEN status = 'SETTLED' THEN 1 END) / NULLIF(COUNT(CASE WHEN status IN ('SETTLED','REJECTED') THEN 1 END), 0) AS settlement_ratio
FROM claims WHERE created_at >= ? AND created_at < ?;

-- Monthly premium revenue trend.
SELECT DATE_FORMAT(created_at, '%Y-%m') AS revenue_month, SUM(amount) AS premium_revenue
FROM payments WHERE status = 'SUCCESS' GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY revenue_month;

-- Aggregate premiums and settlements independently to avoid multiplying rows across joins.
WITH premium_totals AS (
  SELECT pol.product_id, SUM(pay.amount) AS premium_collected
  FROM policies pol JOIN payments pay ON pay.policy_id = pol.id
  WHERE pay.status = 'SUCCESS'
  GROUP BY pol.product_id
), settlement_totals AS (
  SELECT pol.product_id, SUM(c.settlement_amount) AS settlements
  FROM policies pol JOIN claims c ON c.policy_id = pol.id
  WHERE c.status = 'SETTLED'
  GROUP BY pol.product_id
)
SELECT p.name,
       COALESCE(premium_totals.premium_collected, 0) - COALESCE(settlement_totals.settlements, 0) AS approximate_margin
FROM insurance_products p
LEFT JOIN premium_totals ON premium_totals.product_id = p.id
LEFT JOIN settlement_totals ON settlement_totals.product_id = p.id
ORDER BY approximate_margin DESC;

SELECT COUNT(*) AS approved_count FROM claims WHERE status = 'APPROVED';
SELECT COUNT(*) AS rejected_count FROM claims WHERE status = 'REJECTED';
SELECT COUNT(*) AS pending_count FROM claims WHERE status IN ('SUBMITTED','UNDER_REVIEW','INFO_REQUESTED');

-- Average processing duration for closed claims.
SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, updated_at)) AS average_processing_seconds
FROM claims WHERE status IN ('SETTLED','REJECTED','WITHDRAWN');

-- CTE and window-function queue analysis (MySQL 8).
WITH queue AS (
  SELECT id, claim_number, assigned_officer_id, status, sla_due_at,
         ROW_NUMBER() OVER (PARTITION BY assigned_officer_id ORDER BY sla_due_at) AS sla_rank
  FROM claims WHERE status IN ('SUBMITTED','UNDER_REVIEW','INFO_REQUESTED')
)
SELECT * FROM queue WHERE sla_due_at < UTC_TIMESTAMP(6) ORDER BY sla_rank;

EXPLAIN SELECT id, claim_number, status, sla_due_at
FROM claims WHERE assigned_officer_id = ? AND status = 'UNDER_REVIEW' ORDER BY created_at;
