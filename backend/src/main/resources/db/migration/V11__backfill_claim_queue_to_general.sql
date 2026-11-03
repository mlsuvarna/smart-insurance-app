UPDATE claims
SET queue_id = (SELECT id FROM claim_queues WHERE code = 'GENERAL')
WHERE queue_id IS NULL;
