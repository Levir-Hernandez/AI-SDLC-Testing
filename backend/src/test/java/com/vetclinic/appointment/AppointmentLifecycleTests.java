package com.vetclinic.appointment;

import static com.vetclinic.appointment.Actor.*;
import static com.vetclinic.appointment.AppointmentAction.*;
import static com.vetclinic.appointment.AppointmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.shared.error.DomainException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppointmentLifecycleTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final AppointmentLifecycle lifecycle = new AppointmentLifecycle();

    private static Appointment appt(AppointmentStatus status) {
        return new Appointment(null, null, null, NOW, NOW.plusHours(1), status, RECEPTIONIST,
                java.math.BigDecimal.TEN, NOW.plusMinutes(10), null, NOW);
    }

    private static TransitionContext ctx() {
        return new TransitionContext("because", NOW);
    }

    private record Row(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {}

    private static final java.util.List<Row> ROWS = java.util.List.of(
            new Row(RESERVED, CONFIRM, SCHEDULED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(RESERVED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(RESERVED, EXPIRE, CANCELLED, EnumSet.of(SYSTEM)),
            new Row(SCHEDULED, START, IN_PROGRESS, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Row(SCHEDULED, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Row(SCHEDULED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(IN_PROGRESS, FINISH, FINISHED, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Row(IN_PROGRESS, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Row(IN_PROGRESS, CANCEL, CANCELLED, EnumSet.of(RECEPTIONIST)),
            new Row(FINISHED, PAY, PAID, EnumSet.of(RECEPTIONIST)),
            new Row(FINISHED, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)),
            new Row(PAID, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)));

    static Stream<Arguments> allowedTransitions() {
        return ROWS.stream().flatMap(r -> r.actors().stream().map(a -> Arguments.of(r.from(), r.action(), r.to(), a)));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void appliesListedTransition(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Actor actor) {
        Appointment a = appt(from);
        lifecycle.apply(a, action, actor, ctx());
        assertThat(a.getStatus()).isEqualTo(to);
        if (action == CANCEL) {
            assertThat(a.getCancelReason()).isEqualTo("because");
        }
    }

    static Stream<Arguments> unlistedPairs() {
        return Stream.of(AppointmentStatus.values()).flatMap(s -> Stream.of(AppointmentAction.values())
                .filter(act -> act != EDIT_OBSERVATIONS)
                .filter(act -> ROWS.stream().noneMatch(r -> r.from() == s && r.action() == act))
                .map(act -> Arguments.of(s, act)));
    }

    @ParameterizedTest
    @MethodSource("unlistedPairs")
    void rejectsUnlistedPairs(AppointmentStatus status, AppointmentAction action) {
        assertThatThrownBy(() -> lifecycle.apply(appt(status), action, RECEPTIONIST, ctx()))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_TRANSITION");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void invalidTransitionMessage() {
        assertThatThrownBy(() -> lifecycle.apply(appt(CLOSED), START, RECEPTIONIST, ctx()))
                .hasMessage("Cannot START an appointment in status CLOSED");
    }

    @Test
    void ownerCannotStartOrCancelInProgress() {
        for (AppointmentAction act : new AppointmentAction[] {START, CANCEL}) {
            AppointmentStatus s = act == START ? SCHEDULED : IN_PROGRESS;
            assertThatThrownBy(() -> lifecycle.apply(appt(s), act, OWNER, ctx()))
                    .isInstanceOfSatisfying(DomainException.class, e -> {
                        assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED");
                        assertThat(e.getStatus().value()).isEqualTo(403);
                    });
        }
    }

    @Test
    void veterinarianCannotPay() {
        assertThatThrownBy(() -> lifecycle.apply(appt(FINISHED), PAY, VETERINARIAN, ctx()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
    }

    @Test
    void cancelRequiresReason() {
        for (String reason : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> lifecycle.apply(appt(SCHEDULED), CANCEL, OWNER,
                    new TransitionContext(reason, NOW)))
                    .isInstanceOfSatisfying(DomainException.class, e -> {
                        assertThat(e.getCode()).isEqualTo("REASON_REQUIRED");
                        assertThat(e.getStatus().value()).isEqualTo(400);
                    });
        }
    }

    @Test
    void confirmAfterReservationExpiredIsRejected() {
        Appointment a = appt(RESERVED);
        assertThatThrownBy(() -> lifecycle.apply(a, CONFIRM, OWNER,
                new TransitionContext(null, NOW.plusMinutes(10))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RESERVATION_EXPIRED"));
        assertThat(a.getStatus()).isEqualTo(RESERVED);
    }

    @Test
    void allowedActionsPerRole() {
        assertThat(lifecycle.allowedActions(appt(SCHEDULED), RECEPTIONIST))
                .containsExactlyInAnyOrder(START, CANCEL, RESCHEDULE, EDIT_OBSERVATIONS);
        assertThat(lifecycle.allowedActions(appt(SCHEDULED), OWNER)).containsExactly(CANCEL);
        assertThat(lifecycle.allowedActions(appt(RESERVED), RECEPTIONIST))
                .containsExactlyInAnyOrder(CONFIRM, CANCEL, EDIT_OBSERVATIONS);
        assertThat(lifecycle.allowedActions(appt(CLOSED), RECEPTIONIST)).isEmpty();
    }

    @Test
    void observationsNotEditableWhenClosedCancelledOrRescheduled() {
        assertThat(lifecycle.canEditObservations(CLOSED)).isFalse();
        assertThat(lifecycle.canEditObservations(CANCELLED)).isFalse();
        assertThat(lifecycle.canEditObservations(RESCHEDULED)).isFalse();
        assertThat(lifecycle.canEditObservations(PAID)).isTrue();
        assertThat(lifecycle.canEditObservations(IN_PROGRESS)).isTrue();
    }

    @Test
    void finalStatuses() {
        assertThat(CLOSED.isFinal() && CANCELLED.isFinal() && RESCHEDULED.isFinal()).isTrue();
        assertThat(PAID.isFinal()).isFalse();
    }
}
