package com.vetclinic.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(@NotNull @DecimalMin("0.00") BigDecimal amount) {
}
