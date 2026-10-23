package com.smartinsurance.dto;

import com.smartinsurance.enums.KycStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class CustomerDtos {
    private CustomerDtos() { }
    public record ProfileUpdate(@NotNull LocalDate dateOfBirth, @NotBlank @Size(max = 500) String address,
                                @NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 100) String nationalId) { }
    public record ProfileResponse(Long userId, String fullName, String email, LocalDate dateOfBirth,
                                  String address, String maskedPhone, String maskedNationalId, KycStatus kycStatus) { }
}
