CREATE TABLE customer_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY,
    date_of_birth DATE NULL,
    address_encrypted TEXT NULL,
    phone_encrypted TEXT NULL,
    national_id_encrypted TEXT NULL,
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED'))
) ENGINE=InnoDB;

CREATE TABLE claim_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_claim_type_product UNIQUE (product_id, name),
    CONSTRAINT fk_claim_type_product FOREIGN KEY (product_id) REFERENCES insurance_products(id)
) ENGINE=InnoDB;

CREATE TABLE claim_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scan_engine VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    uploaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_document_claim FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_document_uploader FOREIGN KEY (uploader_id) REFERENCES users(id),
    CONSTRAINT chk_document_size CHECK (size_bytes > 0),
    CONSTRAINT chk_document_scan CHECK (scan_status IN ('PENDING','CLEAN','INFECTED','FAILED'))
) ENGINE=InnoDB;
CREATE INDEX ix_documents_claim_status ON claim_documents(claim_id, scan_status);

CREATE TABLE claim_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_comment_claim FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_comment_visibility CHECK (visibility IN ('CUSTOMER','INTERNAL'))
) ENGINE=InnoDB;

CREATE TABLE claim_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    outcome VARCHAR(30) NULL,
    notes VARCHAR(4000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_review_claim FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_review_officer FOREIGN KEY (officer_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    event_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE INDEX ix_audit_resource ON audit_log(resource_type, resource_id, created_at);

DELIMITER //
CREATE TRIGGER claim_history_no_update BEFORE UPDATE ON claim_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim history is insert-only';
END//
CREATE TRIGGER claim_history_no_delete BEFORE DELETE ON claim_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim history is retained and cannot be deleted';
END//
CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are insert-only';
END//
CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are retained and cannot be deleted';
END//
DELIMITER ;
