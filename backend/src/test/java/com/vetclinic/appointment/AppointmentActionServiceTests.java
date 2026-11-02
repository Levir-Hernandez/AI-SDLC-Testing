package com.vetclinic.appointment;

import static com.vetclinic.appointment.AppointmentStatus.*;
import static com.vetclinic.appointment.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentActionServiceTests {
    static final LocalDateTime LATER = NOW.withHour(14);

    @Mock AppointmentRepository appointments;
    @Mock VeterinarianRepository vets;
    @Mock ClinicServiceRepository services;
    @Mock PaymentRepository payments;
    @Mock PetRepository pets;
    @Mock PetService petService;
    MutableClock clock = new MutableClock(NOW);
    AppointmentLifecycle lifecycle = new AppointmentLifecycle();
    AppointmentActionService actions;
    Veterinarian vet5 = vet(5L);
    Pet pet = pet(7L, 10L);

    @BeforeEach
    void setUp() {
        AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, pets, petService,
                lifecycle, clock);
        AvailabilityService availability = new AvailabilityService(vets, services, appointments, clock);
        BookingService booking = new BookingService(appointments, vets, services, petService, availability, queries,
                clock, Duration.ofMinutes(10));
        actions = new AppointmentActionService(appointments, vets, queries, booking, lifecycle, clock);
        lenient().when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(vets.findById(5L)).thenReturn(Optional.of(vet5));
        lenient().when(vets.findLockedById(5L)).thenReturn(Optional.of(vet5));
        lenient().when(vets.findById(6L)).thenReturn(Optional.of(vet(6L)));
        lenient().when(vets.findLockedById(6L)).thenReturn(Optional.of(vet(6L)));
    }

    private Appointment put(long id, AppointmentStatus status, LocalDateTime reservedUntil) {
        Appointment a = appt(id, pet, vet5, MON_10, status, reservedUntil);
        lenient().when(appointments.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private static String code(Throwable t) {
        return ((DomainException) t).getCode();
    }

    private void assertCode(String expected, Runnable r) {
        assertThatThrownBy(r::run).satisfies(e -> assertThat(code(e)).isEqualTo(expected));
    }

    @Test
    void ownerConfirmsBeforeReservedUntil() {
        put(1L, RESERVED, NOW.plusMinutes(5));

        assertThat(actions.confirm(OWNER10, 1L).status()).isEqualTo(SCHEDULED);
    }

    @Test
    void confirmAtReservedUntilIsExpired() {
        put(1L, RESERVED, NOW.plusMinutes(10));
        clock.set(NOW.plusMinutes(10));

        assertCode("RESERVATION_EXPIRED", () -> actions.confirm(OWNER10, 1L));
    }

    @Test
    void confirmAfterReservedUntilIsExpired() {
        put(1L, RESERVED, NOW.plusMinutes(10));
        clock.set(NOW.plusMinutes(11));

        assertCode("RESERVATION_EXPIRED", () -> actions.confirm(OWNER10, 1L));
    }

    @Test
    void expireDueCancelsOnlyExpiredReservations() {
        Appointment due = put(1L, RESERVED, NOW.plusMinutes(10));
        Appointment live = put(2L, RESERVED, NOW.plusMinutes(20));
        clock.set(NOW.plusMinutes(10));
        when(appointments.findByStatusAndReservedUntilLessThanEqual(RESERVED, NOW.plusMinutes(10)))
                .thenReturn(List.of(due));

        assertThat(actions.expireDue()).isEqualTo(1);

        assertThat(due.getStatus()).isEqualTo(CANCELLED);
        assertThat(due.getCancelReason()).isEqualTo("Reservation expired");
        assertThat(live.getStatus()).isEqualTo(RESERVED);
    }

    @Test
    void ownerCancelsOwnReservedAndScheduled() {
        put(1L, RESERVED, NOW.plusMinutes(5));
        put(2L, SCHEDULED, null);

        assertThat(actions.cancel(OWNER10, 1L, "changed my mind").status()).isEqualTo(CANCELLED);
        assertThat(actions.cancel(OWNER10, 2L, "changed my mind").cancelReason()).isEqualTo("changed my mind");
    }

    @Test
    void receptionistCancelsReservedScheduledAndInProgress() {
        put(1L, RESERVED, NOW.plusMinutes(5));
        put(2L, SCHEDULED, null);
        put(3L, IN_PROGRESS, null);

        for (long id : new long[] {1L, 2L, 3L}) {
            assertThat(actions.cancel(RECEPTION, id, "clinic closed").status()).isEqualTo(CANCELLED);
        }
    }

    @Test
    void anotherOwnersAppointmentIsNotFound() {
        put(1L, SCHEDULED, null);

        assertCode("APPOINTMENT_NOT_FOUND", () -> actions.cancel(OWNER11, 1L, "x"));
    }

    @Test
    void blankReasonIsRequired() {
        put(1L, SCHEDULED, null);

        assertCode("REASON_REQUIRED", () -> actions.cancel(OWNER10, 1L, "  "));
    }

    @Test
    void ownerCannotCancelInProgress() {
        put(1L, IN_PROGRESS, null);

        assertCode("ACTION_NOT_ALLOWED", () -> actions.cancel(OWNER10, 1L, "x"));
    }

    @Test
    void receptionistCannotCancelFinished() {
        put(1L, FINISHED, null);

        assertCode("INVALID_TRANSITION", () -> actions.cancel(RECEPTION, 1L, "x"));
    }

    @Test
    void assignedVetStartsAndFinishes() {
        put(1L, SCHEDULED, null);

        assertThat(actions.start(VET5, 1L).status()).isEqualTo(IN_PROGRESS);
        assertThat(actions.finish(VET5, 1L).status()).isEqualTo(FINISHED);
    }

    @Test
    void receptionistStartsAndFinishesAny() {
        put(1L, SCHEDULED, null);

        assertThat(actions.start(RECEPTION, 1L).status()).isEqualTo(IN_PROGRESS);
        assertThat(actions.finish(RECEPTION, 1L).status()).isEqualTo(FINISHED);
    }

    @Test
    void otherVetStartIsNotFound() {
        put(1L, SCHEDULED, null);

        assertCode("APPOINTMENT_NOT_FOUND", () -> actions.start(VET6, 1L));
    }

    @Test
    void finishingScheduledIsInvalidTransition() {
        put(1L, SCHEDULED, null);

        assertCode("INVALID_TRANSITION", () -> actions.finish(VET5, 1L));
    }

    @Test
    void observationsSavedWhileNotFinal() {
        for (AppointmentStatus s : List.of(SCHEDULED, IN_PROGRESS, FINISHED, PAID)) {
            Appointment a = put(1L, s, null);
            AppointmentResponse res = actions.updateObservations(VET5, 1L, "healthy " + s);
            assertThat(a.getObservations()).isEqualTo("healthy " + s);
            assertThat(res.observations()).isEqualTo("healthy " + s);
        }
    }

    @Test
    void observationsLockedOnFinalStatusesAndNotWritten() {
        for (AppointmentStatus s : List.of(CLOSED, CANCELLED, RESCHEDULED)) {
            Appointment a = put(1L, s, null);
            assertCode("OBSERVATIONS_LOCKED", () -> actions.updateObservations(RECEPTION, 1L, "late note"));
            assertThat(a.getObservations()).isNull();
        }
    }

    @Test
    void ownerCannotEditObservations() {
        Appointment a = put(1L, SCHEDULED, null);

        assertCode("ACTION_NOT_ALLOWED", () -> actions.updateObservations(OWNER10, 1L, "x"));
        assertThat(a.getObservations()).isNull();
    }

    @Test
    void scheduledIsRescheduledToNewTime() {
        Appointment original = put(1L, SCHEDULED, null);

        AppointmentResponse res = actions.reschedule(RECEPTION, 1L, new RescheduleRequest(5L, LATER));

        assertThat(original.getStatus()).isEqualTo(RESCHEDULED);
        assertThat(res.status()).isEqualTo(SCHEDULED);
        assertThat(res.startAt()).isEqualTo(LATER);
        assertThat(res.rescheduledFromId()).isEqualTo(1L);
    }

    @Test
    void inProgressIsReassignedToOtherVet() {
        Appointment original = put(1L, IN_PROGRESS, null);

        AppointmentResponse res = actions.reschedule(RECEPTION, 1L, new RescheduleRequest(6L, LATER));

        assertThat(original.getStatus()).isEqualTo(RESCHEDULED);
        assertThat(res.status()).isEqualTo(SCHEDULED);
        assertThat(res.veterinarian().id()).isEqualTo(6L);
        assertThat(res.startAt()).isEqualTo(LATER);
        assertThat(res.rescheduledFromId()).isEqualTo(1L);
        assertThat(res.bookedBy()).isEqualTo(Actor.RECEPTIONIST);
    }

    @Test
    void rescheduleIntoOccupiedSlotKeepsOriginalScheduled() {
        Appointment original = put(1L, SCHEDULED, null);
        when(appointments.existsOverlap(6L, LATER, LATER.plusMinutes(60), NOW, 1L)).thenReturn(true);

        assertCode("SLOT_TAKEN", () -> actions.reschedule(RECEPTION, 1L, new RescheduleRequest(6L, LATER)));
        assertThat(original.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    void rescheduleFinishedIsInvalidTransition() {
        put(1L, FINISHED, null);

        assertCode("INVALID_TRANSITION", () -> actions.reschedule(RECEPTION, 1L, new RescheduleRequest(6L, LATER)));
    }

    @Test
    void ownerCannotReschedule() {
        put(1L, SCHEDULED, null);

        assertCode("ACTION_NOT_ALLOWED", () -> actions.reschedule(OWNER10, 1L, new RescheduleRequest(6L, LATER)));
    }

    @Test
    void closeFromPaidAndFinished() {
        put(1L, PAID, null);
        put(2L, FINISHED, null);

        assertThat(actions.close(RECEPTION, 1L).status()).isEqualTo(CLOSED);
        assertThat(actions.close(RECEPTION, 2L).status()).isEqualTo(CLOSED);
    }

    @Test
    void closingClosedIsInvalidTransition() {
        put(1L, CLOSED, null);

        assertCode("INVALID_TRANSITION", () -> actions.close(RECEPTION, 1L));
    }
}
