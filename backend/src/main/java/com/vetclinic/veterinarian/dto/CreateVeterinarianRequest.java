package com.vetclinic.veterinarian.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateVeterinarianRequest(
        @NotBlank String name,
        @NotBlank String specialization,
        @NotNull @DecimalMin("0.00") BigDecimal costPerService,
        @NotBlank String username,
        @Size(min = 6) String password,
        @NotEmpty @Valid List<ScheduleBlockDto> schedule) {
}
