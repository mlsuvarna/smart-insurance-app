package com.smartinsurance.service;

import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.exception.BusinessRuleException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ClaimTransitionValidator {
    private static final Map<ClaimStatus, List<ClaimStatus>> ALLOWED = Map.of(
            ClaimStatus.SUBMITTED, List.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.WITHDRAWN),
            ClaimStatus.UNDER_REVIEW, List.of(ClaimStatus.INFO_REQUESTED, ClaimStatus.APPROVED, ClaimStatus.REJECTED),
            ClaimStatus.INFO_REQUESTED, List.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.APPROVED, List.of(ClaimStatus.SETTLED),
            ClaimStatus.REJECTED, List.of(), ClaimStatus.SETTLED, List.of(), ClaimStatus.WITHDRAWN, List.of());

    public void validate(ClaimStatus current, ClaimStatus next) {
        if (!ALLOWED.get(current).contains(next)) {
            throw new BusinessRuleException("Invalid claim transition: " + current + " -> " + next);
        }
    }
}
