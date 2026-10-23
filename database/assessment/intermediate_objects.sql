CREATE OR REPLACE VIEW v_claim_status_counts AS
SELECT status, COUNT(*) AS claim_count, SUM(claimed_amount) AS claimed_total
FROM claims GROUP BY status;

CREATE INDEX ix_payments_status_created ON payments(status, created_at);

DELIMITER //
CREATE PROCEDURE sp_claim_counts(IN p_start DATE, IN p_end DATE)
BEGIN
  SELECT status, COUNT(*) AS claim_count
  FROM claims
  WHERE created_at >= p_start AND created_at < DATE_ADD(p_end, INTERVAL 1 DAY)
  GROUP BY status;
END//

CREATE FUNCTION fn_settlement_ratio(p_start DATE, p_end DATE)
RETURNS DECIMAL(8,4)
READS SQL DATA
BEGIN
  DECLARE v_ratio DECIMAL(8,4);
  SELECT COUNT(CASE WHEN status = 'SETTLED' THEN 1 END)
    / NULLIF(COUNT(CASE WHEN status IN ('SETTLED','REJECTED') THEN 1 END), 0)
  INTO v_ratio FROM claims WHERE created_at >= p_start AND created_at < DATE_ADD(p_end, INTERVAL 1 DAY);
  RETURN COALESCE(v_ratio, 0);
END//
DELIMITER ;
