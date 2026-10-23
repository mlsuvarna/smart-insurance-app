package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class ClaimTransitionValidatorTest {
    private final ClaimTransitionValidator validator = new ClaimTransitionValidator();

    @Test
    void acceptsEverySpecifiedTransition() {
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.SUBMITTED, ClaimStatus.WITHDRAWN));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.INFO_REQUESTED, ClaimStatus.UNDER_REVIEW));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED));
        assertDoesNotThrow(() -> validator.validate(ClaimStatus.APPROVED, ClaimStatus.SETTLED));
    }

    @Test
    void rejectsInvalidTransitionsIncludingTerminalStateChanges() {
        for (ClaimStatus status : ClaimStatus.values()) {
            assertThrows(BusinessRuleException.class, () -> validator.validate(status, status));
        }
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(ClaimStatus.INFO_REQUESTED, ClaimStatus.APPROVED));
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(ClaimStatus.SETTLED, ClaimStatus.UNDER_REVIEW));
    }
}
