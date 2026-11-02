package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {
    @Mock AppointmentRepository appointments;
    @Mock VeterinarianRepository vets;
    @Mock ClinicServiceRepository services;
    @Mock PetService petService;
    @Mock AppointmentQueryService queries;
    BookingService booking;
    Veterinarian vet = vet(5L);
    ClinicService hour = service(1L, 60);
    Pet pet = pet(7L, 10L);

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(NOW);
        AvailabilityService availability = new AvailabilityService(vets, services, appointments, clock);
        booking = new BookingService(appointments, vets, services, petService, availability, queries, clock,
                Duration.ofMinutes(10));
        lenient().when(services.findById(1L)).thenReturn(Optional.of(hour));
        lenient().when(vets.findById(5L)).thenReturn(Optional.of(vet));
        lenient().when(vets.findLockedById(5L)).thenReturn(Optional.of(vet));
        lenient().when(petService.requireAccessible(any(), eq(7L))).thenReturn(pet);
        lenient().when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateAppointmentRequest req() {
        return new CreateAppointmentRequest(7L, 1L, 5L, MON_10);
    }

    private Appointment saved() {
        ArgumentCaptor<Appointment> c = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).save(c.capture());
        return c.getValue();
    }

    private static String code(Throwable t) {
        return ((DomainException) t).getCode();
    }

    @Test
    void receptionistBooksScheduledWithTotalFixedAtBooking() {
        booking.book(RECEPTION, req());

        Appointment a = saved();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(a.getBookedBy()).isEqualTo(Actor.RECEPTIONIST);
        assertThat(a.getTotal()).isEqualByComparingTo("60.00");
        assertThat(a.getTotal().scale()).isEqualTo(2);
        assertThat(a.getEndAt()).isEqualTo(MON_10.plusMinutes(60));
        assertThat(a.getReservedUntil()).isNull();
    }

    @Test
    void ownerBooksReservedWithTenMinuteHold() {
        booking.book(OWNER10, req());

        Appointment a = saved();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESERVED);
        assertThat(a.getBookedBy()).isEqualTo(Actor.OWNER);
        assertThat(a.getReservedUntil()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void overlapIsSlotTaken() {
        when(appointments.existsOverlap(eq(5L), eq(MON_10), eq(MON_10.plusMinutes(60)), eq(NOW), isNull()))
                .thenReturn(true);

        assertThatThrownBy(() -> booking.book(RECEPTION, req())).satisfies(e -> assertThat(code(e)).isEqualTo("SLOT_TAKEN"));
        verify(appointments, never()).save(any());
    }

    @Test
    void startInThePastIsRejected() {
        var past = new CreateAppointmentRequest(7L, 1L, 5L, NOW.minusHours(1));

        assertThatThrownBy(() -> booking.book(RECEPTION, past))
                .satisfies(e -> assertThat(code(e)).isEqualTo("START_IN_PAST"));
    }

    @Test
    void startEqualToNowIsRejected() {
        var now = new CreateAppointmentRequest(7L, 1L, 5L, NOW);

        assertThatThrownBy(() -> booking.book(RECEPTION, now))
                .satisfies(e -> assertThat(code(e)).isEqualTo("START_IN_PAST"));
    }

    @Test
    void offScheduleIsSlotUnavailable() {
        var evening = new CreateAppointmentRequest(7L, 1L, 5L, MON_10.withHour(18));

        assertThatThrownBy(() -> booking.book(RECEPTION, evening))
                .satisfies(e -> assertThat(code(e)).isEqualTo("SLOT_UNAVAILABLE"));
    }

    @Test
    void ownerWithAnotherOwnersPetGetsPetNotFound() {
        when(petService.requireAccessible(OWNER11, 7L)).thenThrow(
                new com.vetclinic.shared.error.NotFoundException("PET_NOT_FOUND", "Pet not found: 7"));

        assertThatThrownBy(() -> booking.book(OWNER11, req()))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PET_NOT_FOUND"));
    }

    @Test
    void veterinarianCannotBook() {
        assertThatThrownBy(() -> booking.book(VET5, req()))
                .satisfies(e -> assertThat(((DomainException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void unknownServiceIsNotFound() {
        var bad = new CreateAppointmentRequest(7L, 99L, 5L, MON_10);

        assertThatThrownBy(() -> booking.book(RECEPTION, bad))
                .satisfies(e -> assertThat(code(e)).isEqualTo("SERVICE_NOT_FOUND"));
    }

    @Test
    void negativeServicePriceIsRejectedDefensively() {
        ClinicService negative = withId(new ClinicService(ServiceCode.GROOMING, "X", 60, new BigDecimal("-1.00")), 1L);
        when(services.findById(1L)).thenReturn(Optional.of(negative));

        assertThatThrownBy(() -> booking.book(RECEPTION, req()))
                .satisfies(e -> assertThat(code(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void negativeVetCostIsRejectedDefensively() {
        Veterinarian negative = withId(new Veterinarian("Neg", "X", new BigDecimal("-5.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))), 5L);
        when(vets.findLockedById(5L)).thenReturn(Optional.of(negative));

        assertThatThrownBy(() -> booking.book(RECEPTION, req()))
                .satisfies(e -> assertThat(code(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void createScheduledPassesExcludeIdToOverlapCheck() {
        booking.createScheduled(pet, hour, vet, MON_10, Actor.RECEPTIONIST, null, 42L);

        verify(appointments).existsOverlap(5L, MON_10, MON_10.plusMinutes(60), NOW, 42L);
    }
}
