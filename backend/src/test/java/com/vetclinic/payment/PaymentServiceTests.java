package com.vetclinic.payment;

import static com.vetclinic.appointment.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentRepository;
import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.DomainException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {
    @Mock AppointmentRepository appointments;
    @Mock PaymentRepository payments;
    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(appointments, payments, new AppointmentLifecycle(), new MutableClock(NOW));
        lenient().when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment a = appt(1L, pet(7L, 10L), vet(5L), MON_10, status, null);
        when(appointments.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private static PaymentRequest pay(String amount) {
        return new PaymentRequest(new BigDecimal(amount));
    }

    private static String code(Throwable t) {
        return ((DomainException) t).getCode();
    }

    @Test
    void exactTotalOnFinishedMarksPaid() {
        Appointment a = appointment(AppointmentStatus.FINISHED);

        PaymentResponse res = service.pay(RECEPTION, 1L, pay("60.00"));

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PAID);
        assertThat(res.amount()).isEqualByComparingTo("60.00");
        assertThat(res.appointmentId()).isEqualTo(1L);
        assertThat(res.paidAt()).isEqualTo(NOW);
    }

    @Test
    void sixtyPointZeroEqualsSixtyPointZeroZero() {
        Appointment a = appointment(AppointmentStatus.FINISHED);

        service.pay(RECEPTION, 1L, pay("60.0"));

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PAID);
    }

    @Test
    void belowTotalIsMismatchAndStatusUnchanged() {
        Appointment a = appointment(AppointmentStatus.FINISHED);

        assertThatThrownBy(() -> service.pay(RECEPTION, 1L, pay("59.99")))
                .satisfies(e -> assertThat(code(e)).isEqualTo("AMOUNT_MISMATCH"))
                .hasMessageContaining("Payment must equal total 60.00");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
        verify(payments, never()).save(any());
    }

    @Test
    void aboveTotalIsMismatch() {
        appointment(AppointmentStatus.FINISHED);

        assertThatThrownBy(() -> service.pay(RECEPTION, 1L, pay("60.01")))
                .satisfies(e -> assertThat(code(e)).isEqualTo("AMOUNT_MISMATCH"));
    }

    @Test
    void scheduledAndInProgressAreInvalidTransition() {
        for (AppointmentStatus s : new AppointmentStatus[] {AppointmentStatus.SCHEDULED,
                AppointmentStatus.IN_PROGRESS}) {
            appointment(s);
            assertThatThrownBy(() -> service.pay(RECEPTION, 1L, pay("60.00")))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_TRANSITION"));
        }
    }

    @Test
    void secondPaymentIsAlreadyPaid() {
        appointment(AppointmentStatus.PAID);
        when(payments.existsByAppointmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.pay(RECEPTION, 1L, pay("60.00")))
                .satisfies(e -> assertThat(code(e)).isEqualTo("ALREADY_PAID"));
    }

    @Test
    void uniqueConstraintRaceIsAlreadyPaid() {
        appointment(AppointmentStatus.FINISHED);
        when(payments.save(any(Payment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.pay(RECEPTION, 1L, pay("60.00")))
                .satisfies(e -> assertThat(code(e)).isEqualTo("ALREADY_PAID"));
    }

    @Test
    void veterinarianCannotPay() {
        assertThatThrownBy(() -> service.pay(VET5, 1L, pay("60.00")))
                .satisfies(e -> assertThat(((DomainException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void unknownAppointmentIsNotFound() {
        assertThatThrownBy(() -> service.pay(RECEPTION, 9L, pay("60.00")))
                .satisfies(e -> assertThat(code(e)).isEqualTo("APPOINTMENT_NOT_FOUND"));
    }
}
