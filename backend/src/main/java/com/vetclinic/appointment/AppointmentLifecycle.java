package com.vetclinic.appointment;

import static com.vetclinic.appointment.Actor.RECEPTIONIST;
import static com.vetclinic.appointment.Actor.SYSTEM;
import static com.vetclinic.appointment.Actor.VETERINARIAN;
import static com.vetclinic.appointment.AppointmentAction.*;
import static com.vetclinic.appointment.AppointmentStatus.*;

import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Single place enforcing the TASK.md appointment transition table. */
@Component
public class AppointmentLifecycle {
    private record Rule(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {}

    private static final List<Rule> RULES = List.of(
            new Rule(RESERVED, CONFIRM, SCHEDULED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
            new Rule(RESERVED, CANCEL, CANCELLED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
            new Rule(RESERVED, EXPIRE, CANCELLED, EnumSet.of(SYSTEM)),
            new Rule(SCHEDULED, START, IN_PROGRESS, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Rule(SCHEDULED, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Rule(SCHEDULED, CANCEL, CANCELLED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
            new Rule(IN_PROGRESS, FINISH, FINISHED, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Rule(IN_PROGRESS, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Rule(IN_PROGRESS, CANCEL, CANCELLED, EnumSet.of(RECEPTIONIST)),
            new Rule(FINISHED, PAY, PAID, EnumSet.of(RECEPTIONIST)),
            new Rule(FINISHED, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)),
            new Rule(PAID, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)));

    public void apply(Appointment a, AppointmentAction action, Actor actor, TransitionContext ctx) {
        AppointmentStatus current = a.getStatus();
        Rule rule = RULES.stream().filter(r -> r.from() == current && r.action() == action).findFirst()
                .orElseThrow(() -> new ConflictException("INVALID_TRANSITION",
                        "Cannot " + action + " an appointment in status " + current));
        if (!rule.actors().contains(actor)) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED",
                    actor + " cannot " + action + " an appointment in status " + current);
        }
        String reason = null;
        if (action == CANCEL) {
            if (ctx.reason() == null || ctx.reason().isBlank()) {
                throw new BadRequestException("REASON_REQUIRED", "A cancellation reason is required");
            }
            reason = ctx.reason().trim();
        }
        if (action == CONFIRM && (a.getReservedUntil() == null || !a.getReservedUntil().isAfter(ctx.now()))) {
            throw new ConflictException("RESERVATION_EXPIRED", "The reservation has expired");
        }
        a.applyStatus(rule.to(), reason);
    }

    public boolean canEditObservations(AppointmentStatus status) {
        return status != CLOSED && status != CANCELLED && status != RESCHEDULED;
    }

    public Set<AppointmentAction> allowedActions(Appointment a, Actor actor) {
        Set<AppointmentAction> out = EnumSet.noneOf(AppointmentAction.class);
        for (Rule r : RULES) {
            if (r.from() == a.getStatus() && r.action() != EXPIRE && r.actors().contains(actor)) {
                out.add(r.action());
            }
        }
        if ((actor == VETERINARIAN || actor == RECEPTIONIST) && canEditObservations(a.getStatus())) {
            out.add(EDIT_OBSERVATIONS);
        }
        return out;
    }
}
