CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER','AGENT','CLAIMS_OFFICER','ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE insurance_products (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    annual_premium DECIMAL(12,2) NOT NULL,
    coverage_limit DECIMAL(14,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_product_premium CHECK (annual_premium > 0),
    CONSTRAINT chk_product_coverage CHECK (coverage_limit > 0)
) ENGINE=InnoDB;

CREATE TABLE policies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    policy_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    coverage_amount DECIMAL(14,2) NOT NULL,
    premium_amount DECIMAL(12,2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_policy_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_policy_product FOREIGN KEY (product_id) REFERENCES insurance_products(id),
    CONSTRAINT chk_policy_dates CHECK (end_date > start_date),
    CONSTRAINT chk_policy_status CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED','PENDING_PAYMENT'))
) ENGINE=InnoDB;
CREATE INDEX ix_policies_customer_created ON policies(customer_id, created_at);

CREATE TABLE claims (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_number VARCHAR(40) NOT NULL UNIQUE,
    policy_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    claim_type VARCHAR(80) NOT NULL,
    incident_date DATE NOT NULL,
    description VARCHAR(4000) NOT NULL,
    claimed_amount DECIMAL(14,2) NOT NULL,
    approved_amount DECIMAL(14,2) NULL,
    settlement_amount DECIMAL(14,2) NULL,
    status VARCHAR(30) NOT NULL,
    assigned_officer_id BIGINT NULL,
    sla_due_at TIMESTAMP(6) NOT NULL,
    sla_paused_at TIMESTAMP(6) NULL,
    sla_paused_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_id) REFERENCES policies(id),
    CONSTRAINT fk_claim_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_claim_officer FOREIGN KEY (assigned_officer_id) REFERENCES users(id),
    CONSTRAINT chk_claim_amount CHECK (claimed_amount > 0),
    CONSTRAINT chk_claim_status CHECK (status IN ('SUBMITTED','UNDER_REVIEW','INFO_REQUESTED','APPROVED','REJECTED','SETTLED','WITHDRAWN'))
) ENGINE=InnoDB;
CREATE INDEX ix_claim_customer_created ON claims(customer_id, created_at);
CREATE INDEX ix_claim_officer_queue ON claims(assigned_officer_id, status, created_at);
CREATE INDEX ix_claim_sla ON claims(status, sla_due_at);

CREATE TABLE claim_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NULL,
    remarks VARCHAR(2000) NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_history_claim FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_history_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE INDEX ix_claim_history_timeline ON claim_history(claim_id, created_at);

CREATE TABLE agent_customer_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    CONSTRAINT uq_agent_customer UNIQUE (agent_id, customer_id),
    CONSTRAINT fk_assignment_agent FOREIGN KEY (agent_id) REFERENCES users(id),
    CONSTRAINT fk_assignment_customer FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_reference VARCHAR(50) NOT NULL UNIQUE,
    policy_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_policy FOREIGN KEY (policy_id) REFERENCES policies(id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED'))
) ENGINE=InnoDB;
CREATE INDEX ix_payments_customer_date ON payments(customer_id, created_at);

CREATE TABLE sla_configurations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_type VARCHAR(80) NOT NULL UNIQUE,
    duration_days INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_sla_duration CHECK (duration_days > 0)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    claim_id BIGINT NULL,
    event_type VARCHAR(80) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notification_claim FOREIGN KEY (claim_id) REFERENCES claims(id)
) ENGINE=InnoDB;
CREATE INDEX ix_notifications_unread ON notifications(user_id, is_read, created_at);

INSERT INTO insurance_products(name, category, description, annual_premium, coverage_limit) VALUES
('Motor Protect', 'Motor', 'Vehicle damage and liability protection subject to policy terms.', 480.00, 50000.00),
('Health Secure', 'Health', 'Health expense protection subject to policy terms.', 720.00, 100000.00),
('Home Shield', 'Property', 'Residential property protection subject to policy terms.', 360.00, 150000.00);
