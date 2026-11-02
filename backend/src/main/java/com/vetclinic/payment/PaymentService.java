package com.vetclinic.payment;

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentAction;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentRepository;
import com.vetclinic.appointment.TransitionContext;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final AppointmentRepository appointments;
    private final PaymentRepository payments;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public PaymentService(AppointmentRepository appointments, PaymentRepository payments,
                          AppointmentLifecycle lifecycle, Clock clock) {
        this.appointments = appointments;
        this.payments = payments;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** TASK.md: exactly one payment per finished appointment, equal to the total fixed at booking. */
    @Transactional
    public PaymentResponse pay(CurrentUser user, Long appointmentId, PaymentRequest req) {
        if (user.role() != Role.RECEPTIONIST) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED", "Only reception can register payments");
        }
        Appointment a = appointments.findById(appointmentId).orElseThrow(() ->
                new NotFoundException("APPOINTMENT_NOT_FOUND", "Appointment not found: " + appointmentId));
        if (payments.existsByAppointmentId(appointmentId)) {
            throw new ConflictException("ALREADY_PAID", "The appointment is already paid");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TransitionContext ctx = new TransitionContext(null, now);
        // Status check first (no mutation), then the amount, then the transition.
        if (!lifecycle.allowedActions(a, user.actor()).contains(AppointmentAction.PAY)) {
            lifecycle.apply(a, AppointmentAction.PAY, user.actor(), ctx);
        }
        if (req.amount().compareTo(a.getTotal()) != 0) {
            throw new ConflictException("AMOUNT_MISMATCH",
                    "Payment must equal total " + a.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        lifecycle.apply(a, AppointmentAction.PAY, user.actor(), ctx);
        try {
            return PaymentResponse.from(payments.save(new Payment(a, a.getTotal(), now)));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("ALREADY_PAID", "The appointment is already paid");
        }
    }
}
