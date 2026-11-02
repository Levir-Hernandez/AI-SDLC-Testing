package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTests {
    static final LocalDate MONDAY = NOW.toLocalDate();

    @Mock VeterinarianRepository vets;
    @Mock ClinicServiceRepository services;
    @Mock AppointmentRepository appointments;
    AvailabilityService availability;
    Veterinarian vet = vet(5L);
    ClinicService hour = service(1L, 60);

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(NOW);
        availability = new AvailabilityService(vets, services, appointments, clock);
        lenient().when(services.findById(1L)).thenReturn(Optional.of(hour));
        lenient().when(vets.findById(5L)).thenReturn(Optional.of(vet));
        lenient().when(appointments.findBlocking(eq(5L), any(), any(), any())).thenReturn(List.of());
    }

    private List<SlotResponse> slots() {
        return availability.slots(1L, Optional.of(5L), MONDAY);
    }

    @Test
    void slotsFollowTheGridInsideTheBlockAndIncludeOneEndingAtBlockEnd() {
        List<SlotResponse> slots = slots();

        assertThat(slots).hasSize(29);
        assertThat(slots.get(0).startAt()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(slots.get(1).startAt()).isEqualTo(MONDAY.atTime(9, 15));
        assertThat(slots.get(28).endAt()).isEqualTo(MONDAY.atTime(17, 0));
    }

    @Test
    void withoutVetIdAllVeterinariansAreIncluded() {
        when(vets.findAll()).thenReturn(List.of(vet));

        assertThat(availability.slots(1L, Optional.empty(), MONDAY)).hasSize(29);
    }

    @Test
    void blockingQueryIsAskedWithCurrentTimeSoExpiredReservationsAndFinalStatusesDoNotBlock() {
        slots();

        verify(appointments).findBlocking(5L, MONDAY.atStartOfDay(), MONDAY.plusDays(1).atStartOfDay(), NOW);
    }

    @Test
    void slotOverlappingBlockingAppointmentIsExcluded() {
        Appointment busy = appt(1L, pet(1L, 10L), vet, MONDAY.atTime(10, 0), AppointmentStatus.SCHEDULED, null);
        when(appointments.findBlocking(eq(5L), any(), any(), any())).thenReturn(List.of(busy));

        List<SlotResponse> slots = slots();

        assertThat(slots).noneMatch(s -> s.startAt().isAfter(MONDAY.atTime(9, 0))
                && s.startAt().isBefore(MONDAY.atTime(11, 0)));
        assertThat(slots).anyMatch(s -> s.startAt().equals(MONDAY.atTime(9, 0)));
        assertThat(slots).anyMatch(s -> s.startAt().equals(MONDAY.atTime(11, 0)));
    }

    @Test
    void slotsAtOrBeforeNowAreExcluded() {
        MutableClock clock = new MutableClock(MONDAY.atTime(10, 0));
        availability = new AvailabilityService(vets, services, appointments, clock);

        List<SlotResponse> slots = slots();

        assertThat(slots.get(0).startAt()).isEqualTo(MONDAY.atTime(10, 15));
    }

    @Test
    void pastDateIsEmpty() {
        assertThat(availability.slots(1L, Optional.of(5L), MONDAY.minusDays(7))).isEmpty();
    }

    @Test
    void dayWithoutBlocksIsEmpty() {
        assertThat(availability.slots(1L, Optional.of(5L), MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    void durationLargerThanBlockGivesNoSlot() {
        when(services.findById(2L)).thenReturn(Optional.of(service(2L, 600)));

        assertThat(availability.slots(2L, Optional.of(5L), MONDAY)).isEmpty();
    }

    @Test
    void unknownServiceIsNotFound() {
        assertThatThrownBy(() -> availability.slots(99L, Optional.of(5L), MONDAY))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("SERVICE_NOT_FOUND");
    }

    @Test
    void isBookableAcceptsGridAlignedSlotAndRejectsOthers() {
        assertThat(availability.isBookable(vet, hour, MONDAY.atTime(16, 0))).isTrue();
        assertThat(availability.isBookable(vet, hour, MONDAY.atTime(16, 15))).isFalse();
        assertThat(availability.isBookable(vet, hour, MONDAY.atTime(10, 10))).isFalse();
        assertThat(availability.isBookable(vet, hour, MONDAY.atTime(7, 0))).isFalse();
        assertThat(availability.isBookable(vet, hour, MONDAY.atTime(8, 0))).isFalse();
        assertThat(availability.isBookable(vet, hour, MONDAY.plusDays(1).atTime(10, 0))).isFalse();
    }
}
