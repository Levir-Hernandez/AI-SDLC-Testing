package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentActionService {
    private final AppointmentRepository appointments;
    private final VeterinarianRepository vets;
    private final AppointmentQueryService queries;
    private final BookingService booking;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public AppointmentActionService(AppointmentRepository appointments, VeterinarianRepository vets,
                                    AppointmentQueryService queries, BookingService booking,
                                    AppointmentLifecycle lifecycle, Clock clock) {
        this.appointments = appointments;
        this.vets = vets;
        this.queries = queries;
        this.booking = booking;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse confirm(CurrentUser user, Long id) {
        return act(user, id, AppointmentAction.CONFIRM, null);
    }

    @Transactional
    public AppointmentResponse cancel(CurrentUser user, Long id, String reason) {
        return act(user, id, AppointmentAction.CANCEL, reason);
    }

    @Transactional
    public AppointmentResponse start(CurrentUser user, Long id) {
        return act(user, id, AppointmentAction.START, null);
    }

    @Transactional
    public AppointmentResponse finish(CurrentUser user, Long id) {
        return act(user, id, AppointmentAction.FINISH, null);
    }

    @Transactional
    public AppointmentResponse close(CurrentUser user, Long id) {
        return act(user, id, AppointmentAction.CLOSE, null);
    }

    private AppointmentResponse act(CurrentUser user, Long id, AppointmentAction action, String reason) {
        Appointment a = queries.requireScoped(user, id);
        lifecycle.apply(a, action, user.actor(), new TransitionContext(reason, LocalDateTime.now(clock)));
        return queries.toResponse(a, user.actor());
    }

    /** TASK.md: an unconfirmed reservation expires and frees the slot. Returns how many were expired. */
    @Transactional
    public int expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> due = appointments.findByStatusAndReservedUntilLessThanEqual(
                AppointmentStatus.RESERVED, now);
        for (Appointment a : due) {
            lifecycle.apply(a, AppointmentAction.EXPIRE, Actor.SYSTEM,
                    new TransitionContext("Reservation expired", now));
        }
        return due.size();
    }

    /** Observations are editable by veterinarians and receptionists until the appointment is final. */
    @Transactional
    public AppointmentResponse updateObservations(CurrentUser user, Long id, String text) {
        if (user.role() == Role.OWNER) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED", "Owners cannot edit observations");
        }
        Appointment a = queries.requireScoped(user, id);
        if (!lifecycle.canEditObservations(a.getStatus())) {
            throw new ConflictException("OBSERVATIONS_LOCKED",
                    "Observations cannot be edited in status " + a.getStatus());
        }
        a.setObservations(text);
        return queries.toResponse(a, user.actor());
    }

    /** Covers both reschedule and reassign: the old appointment becomes RESCHEDULED, a new SCHEDULED one is created. */
    @Transactional
    public AppointmentResponse reschedule(CurrentUser user, Long id, RescheduleRequest req) {
        Appointment original = queries.requireScoped(user, id);
        Actor actor = user.actor();
        TransitionContext ctx = new TransitionContext(null, LocalDateTime.now(clock));
        // Fail on wrong status/role before touching anything, so a rejected request leaves the original unchanged.
        if (!lifecycle.allowedActions(original, actor).contains(AppointmentAction.RESCHEDULE)) {
            lifecycle.apply(original, AppointmentAction.RESCHEDULE, actor, ctx);
        }
        Veterinarian vet = vets.findById(req.veterinarianId()).orElseThrow(() ->
                new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian not found: " + req.veterinarianId()));
        Appointment created = booking.createScheduled(original.getPet(), original.getService(), vet, req.startAt(),
                Actor.RECEPTIONIST, original, original.getId());
        lifecycle.apply(original, AppointmentAction.RESCHEDULE, actor, ctx);
        return queries.toResponse(created, actor);
    }
}
