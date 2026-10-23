ALTER TABLE claim_history
    ADD COLUMN previous_officer_id BIGINT NULL,
    ADD COLUMN new_officer_id BIGINT NULL,
    ADD CONSTRAINT fk_claim_history_previous_officer FOREIGN KEY (previous_officer_id) REFERENCES users(id),
    ADD CONSTRAINT fk_claim_history_new_officer FOREIGN KEY (new_officer_id) REFERENCES users(id);

DELIMITER //
CREATE TRIGGER claim_settlements_no_update BEFORE UPDATE ON claim_settlements
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim settlement ledger is insert-only';
END//
CREATE TRIGGER claim_settlements_no_delete BEFORE DELETE ON claim_settlements
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim settlement ledger cannot be deleted';
END//
CREATE TRIGGER agent_assignment_history_no_update BEFORE UPDATE ON agent_assignment_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Agent assignment history is insert-only';
END//
CREATE TRIGGER agent_assignment_history_no_delete BEFORE DELETE ON agent_assignment_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Agent assignment history cannot be deleted';
END//
DELIMITER ;
