package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AppointmentQueryServiceTests {
    @Mock AppointmentRepository appointments;
    @Mock PaymentRepository payments;
    @Mock PetRepository pets;
    @Mock PetService petService;
    AppointmentQueryService queries;
    Pet pet = pet(7L, 10L);

    @BeforeEach
    void setUp() {
        queries = new AppointmentQueryService(appointments, payments, pets, petService, new AppointmentLifecycle(),
                new MutableClock(NOW));
    }

    private Appointment closed() {
        return appt(1L, pet, vet(5L), MON_10, AppointmentStatus.CLOSED, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerListIsScopedToOwnPets() {
        when(pets.findByOwnerId(10L)).thenReturn(List.of(pet));
        Appointment a = appt(1L, pet, vet(5L), MON_10, AppointmentStatus.SCHEDULED, null);
        when(appointments.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));

        PageResponse<AppointmentResponse> res = queries.list(OWNER10, AppointmentFilter.none(), PageRequest.of(0, 10));

        assertThat(res.content()).hasSize(1);
        verify(pets).findByOwnerId(10L);
    }

    @Test
    void ownerWithoutPetsGetsEmptyPageWithoutQueryingAppointments() {
        when(pets.findByOwnerId(11L)).thenReturn(List.of());

        PageResponse<AppointmentResponse> res = queries.list(OWNER11, AppointmentFilter.none(), PageRequest.of(0, 10));

        assertThat(res.content()).isEmpty();
        verify(appointments, never()).findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void veterinarianListDoesNotConsultPetsAndIgnoresOtherVetFilter() {
        when(appointments.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        queries.list(VET5, new AppointmentFilter(null, null, 6L, null), PageRequest.of(0, 10));

        verify(pets, never()).findByOwnerId(any());
    }

    @Test
    void debtIsTrueOnlyForClosedUnpaid() {
        Appointment a = closed();
        assertThat(queries.toResponse(a, Actor.RECEPTIONIST).debt()).isTrue();

        when(payments.existsByAppointmentId(1L)).thenReturn(true);
        assertThat(queries.toResponse(a, Actor.RECEPTIONIST).debt()).isFalse();

        Appointment finished = appt(2L, pet, vet(5L), MON_10, AppointmentStatus.FINISHED, null);
        assertThat(queries.toResponse(finished, Actor.RECEPTIONIST).debt()).isFalse();
    }

    @Test
    void allowedActionsComeFromTheLifecycle() {
        Appointment a = appt(1L, pet, vet(5L), MON_10, AppointmentStatus.SCHEDULED, null);

        assertThat(queries.toResponse(a, Actor.OWNER).allowedActions()).containsExactly(AppointmentAction.CANCEL);
        assertThat(queries.toResponse(a, Actor.RECEPTIONIST).allowedActions()).contains(AppointmentAction.START,
                AppointmentAction.RESCHEDULE, AppointmentAction.CANCEL, AppointmentAction.EDIT_OBSERVATIONS);
    }

    @Test
    void responseCarriesSummariesAndRescheduledLink() {
        Appointment old = appt(1L, pet, vet(5L), MON_10, AppointmentStatus.RESCHEDULED, null);
        Appointment next = appt(2L, pet, vet(5L), MON_10.plusDays(1), AppointmentStatus.SCHEDULED, null);
        when(appointments.findByRescheduledFromId(1L)).thenReturn(List.of(next));

        AppointmentResponse res = queries.toResponse(old, Actor.RECEPTIONIST);

        assertThat(res.rescheduledToId()).isEqualTo(2L);
        assertThat(res.pet().name()).isEqualTo("Rex7");
        assertThat(res.ownerName()).isEqualTo("Owner10");
        assertThat(res.service().durationMinutes()).isEqualTo(60);
        assertThat(res.veterinarian().id()).isEqualTo(5L);
    }

    @Test
    void historyKeepsOrderAndFlagsUpcoming() {
        Appointment future = appt(2L, pet, vet(5L), NOW.plusDays(3), AppointmentStatus.SCHEDULED, null);
        Appointment past = appt(1L, pet, vet(5L), NOW.minusDays(3), AppointmentStatus.CLOSED, null);
        when(petService.requireAccessible(OWNER10, 7L)).thenReturn(pet);
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(appointments.findByPetId(org.mockito.ArgumentMatchers.eq(7L), captor.capture()))
                .thenReturn(new PageImpl<>(List.of(future, past), PageRequest.of(0, 10), 2));

        PageResponse<AppointmentResponse> res = queries.historyForPet(OWNER10, 7L, PageRequest.of(0, 10));

        assertThat(captor.getValue().getSort().getOrderFor("startAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("startAt").isDescending()).isTrue();

        assertThat(res.content()).extracting(AppointmentResponse::id).containsExactly(2L, 1L);
        assertThat(res.content()).extracting(AppointmentResponse::upcoming).containsExactly(true, false);
    }

    @Test
    void ownerGettingAnotherOwnersAppointmentIsNotFound() {
        when(appointments.findById(1L)).thenReturn(Optional.of(closed()));

        assertThatThrownBy(() -> queries.get(OWNER11, 1L))
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("APPOINTMENT_NOT_FOUND"));
        assertThat(queries.get(OWNER10, 1L).id()).isEqualTo(1L);
    }

    @Test
    void ownerHistoryOfAnotherPetIsNotFound() {
        when(petService.requireAccessible(OWNER11, 7L))
                .thenThrow(new NotFoundException("PET_NOT_FOUND", "Pet not found: 7"));

        assertThatThrownBy(() -> queries.historyForPet(OWNER11, 7L, PageRequest.of(0, 10)))
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("PET_NOT_FOUND"));
    }

    @Test
    void veterinarianHistoryIsForbidden() {
        when(petService.requireAccessible(VET5, 7L)).thenThrow(new ForbiddenException("FORBIDDEN", "no"));

        assertThatThrownBy(() -> queries.historyForPet(VET5, 7L, PageRequest.of(0, 10)))
                .satisfies(e -> assertThat(((DomainException) e).getStatus().value()).isEqualTo(403));
    }
}
