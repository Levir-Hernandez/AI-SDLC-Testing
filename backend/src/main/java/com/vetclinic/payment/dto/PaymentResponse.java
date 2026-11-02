package com.vetclinic.payment.dto;

import com.vetclinic.payment.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long appointmentId, BigDecimal amount, LocalDateTime paidAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getAppointment().getId(), p.getAmount(), p.getPaidAt());
    }
}
