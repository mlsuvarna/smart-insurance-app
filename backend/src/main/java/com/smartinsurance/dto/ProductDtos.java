package com.smartinsurance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() { }
    public record ProductRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Size(max = 40) String category,
                                 @NotBlank @Size(max = 2000) String description,
                                 @NotNull @DecimalMin("0.01") BigDecimal annualPremium,
                                 @NotNull @DecimalMin("0.01") BigDecimal coverageLimit) { }
    public record ProductResponse(Long id, String name, String category, String description,
                                  BigDecimal annualPremium, BigDecimal coverageLimit, boolean active) { }
}
