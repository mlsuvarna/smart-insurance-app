CREATE TABLE claim_queues (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

INSERT INTO claim_queues(code, name) VALUES ('GENERAL', 'General claims');

ALTER TABLE claims ADD COLUMN queue_id BIGINT NULL;
UPDATE claims SET queue_id = (SELECT id FROM claim_queues WHERE code = 'GENERAL');
ALTER TABLE claims
    MODIFY COLUMN queue_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_claim_queue FOREIGN KEY (queue_id) REFERENCES claim_queues(id);
CREATE INDEX ix_claim_queue_status_created ON claims(queue_id, status, created_at, id);

CREATE TABLE officer_queue_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_officer_queue UNIQUE (queue_id, officer_id),
    CONSTRAINT fk_membership_queue FOREIGN KEY (queue_id) REFERENCES claim_queues(id),
    CONSTRAINT fk_membership_officer FOREIGN KEY (officer_id) REFERENCES users(id),
    CONSTRAINT fk_membership_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE INDEX ix_officer_queue_membership_officer ON officer_queue_memberships(officer_id, queue_id);
