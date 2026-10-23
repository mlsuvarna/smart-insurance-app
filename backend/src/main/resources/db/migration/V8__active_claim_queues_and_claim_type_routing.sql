ALTER TABLE claim_queues
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uq_claim_queue_name UNIQUE (name);

ALTER TABLE claim_types
    ADD COLUMN queue_id BIGINT NULL;

UPDATE claim_types
SET queue_id = (SELECT id FROM claim_queues WHERE code = 'GENERAL');

ALTER TABLE claim_types
    MODIFY COLUMN queue_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_claim_type_queue FOREIGN KEY (queue_id) REFERENCES claim_queues(id);
CREATE INDEX ix_claim_type_queue ON claim_types(queue_id, active);
