-- Demonstration CRUD (parameterize values in application code; do not execute with user input concatenation).
SELECT id, name, category, annual_premium FROM insurance_products WHERE active = TRUE ORDER BY name;
SELECT p.policy_number, u.email, i.name AS product_name
FROM policies p JOIN users u ON u.id = p.customer_id JOIN insurance_products i ON i.id = p.product_id
WHERE p.status = 'ACTIVE' ORDER BY p.created_at DESC;
SELECT status, COUNT(*) AS claim_count FROM claims GROUP BY status ORDER BY claim_count DESC;
-- Example CRUD insert/update/delete for an isolated local assessment fixture only.
INSERT INTO insurance_products(name, category, description, annual_premium, coverage_limit)
VALUES ('Assessment Example', 'Motor', 'Temporary SQL assessment fixture.', 10.00, 1000.00);
UPDATE insurance_products SET active = FALSE WHERE id = ?;
DELETE FROM insurance_products WHERE name = 'Assessment Example';
-- Never delete audit or claim history rows in application workflows.
