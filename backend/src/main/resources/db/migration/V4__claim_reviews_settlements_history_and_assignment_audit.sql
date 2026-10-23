ALTER TABLE claim_history
    ADD COLUMN actor_role VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN previous_amount DECIMAL(14,2) NULL,
    ADD COLUMN new_amount DECIMAL(14,2) NULL;

ALTER TABLE claim_reviews
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN closed_at TIMESTAMP(6) NULL;
CREATE INDEX ix_claim_reviews_claim_closed ON claim_reviews(claim_id, closed_at);

CREATE TABLE claim_settlements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    settlement_reference VARCHAR(100) NOT NULL UNIQUE,
    amount DECIMAL(14,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_settlement_claim FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_settlement_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_settlement_amount CHECK (amount > 0)
) ENGINE=InnoDB;
CREATE INDEX ix_settlement_claim_created ON claim_settlements(claim_id, created_at);

CREATE TABLE agent_assignment_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_assignment_history_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT fk_assignment_history_agent FOREIGN KEY (agent_id) REFERENCES users(id),
    CONSTRAINT fk_assignment_history_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT chk_assignment_history_action CHECK (action IN ('ASSIGNED','REMOVED'))
) ENGINE=InnoDB;
CREATE INDEX ix_assignment_history_customer ON agent_assignment_history(customer_id, created_at);
