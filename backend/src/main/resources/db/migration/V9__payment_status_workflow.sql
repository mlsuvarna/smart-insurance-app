ALTER TABLE payments
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN processed_at TIMESTAMP(6) NULL,
    ADD COLUMN refunded_at TIMESTAMP(6) NULL;

CREATE TABLE payment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    processor VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_payment_history_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_payment_history_previous CHECK (previous_status IS NULL OR previous_status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT chk_payment_history_new CHECK (new_status IN ('PENDING','SUCCESS','FAILED','REFUNDED'))
) ENGINE=InnoDB;
CREATE INDEX ix_payment_history_payment_created ON payment_status_history(payment_id, created_at);

DELIMITER //
CREATE TRIGGER payment_status_history_no_update BEFORE UPDATE ON payment_status_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment status history is insert-only';
END//
CREATE TRIGGER payment_status_history_no_delete BEFORE DELETE ON payment_status_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment status history cannot be deleted';
END//
DELIMITER ;
