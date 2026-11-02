package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** Proves the list scoping and filters against the real query (the unit tests mock the repository). */
@DataJpaTest
class AppointmentQueryScopeTests {
    @Autowired AppointmentRepository appointments;
    @Autowired OwnerRepository owners;
    @Autowired PetRepository pets;
    @Autowired VeterinarianRepository vets;
    @Autowired ClinicServiceRepository services;
    @Autowired PaymentRepository payments;

    AppointmentQueryService queries;
    Owner ann;
    Owner bob;
    Veterinarian vetA;
    Veterinarian vetB;
    Pet annPet;
    Pet bobPet;
    ClinicService service;

    @BeforeEach
    void setUp() {
        queries = new AppointmentQueryService(appointments, payments, pets, new PetService(pets, owners),
                new AppointmentLifecycle(), new Fixtures.MutableClock(NOW));
        ann = owners.save(new Owner("Ann", "555-1"));
        bob = owners.save(new Owner("Bob", "555-2"));
        annPet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, ann));
        bobPet = pets.save(new Pet("Tom", Species.CAT, Breed.SIAMESE, bob));
        service = services.save(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("40.00")));
        vetA = vets.save(new Veterinarian("A", "G", new BigDecimal("20.00"), List.of()));
        vetB = vets.save(new Veterinarian("B", "G", new BigDecimal("20.00"), List.of()));
    }

    private Appointment save(Pet pet, Veterinarian vet, LocalDateTime start, AppointmentStatus status) {
        return appointments.save(new Appointment(pet, service, vet, start, start.plusHours(1), status,
                Actor.RECEPTIONIST, new BigDecimal("60.00"), null, null, NOW));
    }

    private List<Long> ids(CurrentUser user, AppointmentFilter filter) {
        return queries.list(user, filter, PageRequest.of(0, 20)).content().stream()
                .map(r -> r.id()).toList();
    }

    @Test
    void ownerSeesOnlyOwnPetsSortedByStart() {
        Appointment later = save(annPet, vetA, NOW.plusDays(2), AppointmentStatus.SCHEDULED);
        Appointment sooner = save(annPet, vetB, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        save(bobPet, vetA, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        CurrentUser annUser = new CurrentUser(1L, Role.OWNER, ann.getId(), null);

        assertThat(ids(annUser, AppointmentFilter.none())).containsExactly(sooner.getId(), later.getId());
    }

    @Test
    void veterinarianSeesOnlyAssignedEvenWhenFilteringForAnother() {
        Appointment mine = save(annPet, vetA, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        save(bobPet, vetB, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        CurrentUser vetUser = new CurrentUser(2L, Role.VETERINARIAN, null, vetA.getId());

        assertThat(ids(vetUser, new AppointmentFilter(null, null, vetB.getId(), null))).containsExactly(mine.getId());
    }

    @Test
    void receptionistFiltersByStatusDateAndVet() {
        Appointment a = save(annPet, vetA, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        save(annPet, vetA, NOW.plusDays(1), AppointmentStatus.CANCELLED);
        save(bobPet, vetB, NOW.plusDays(2), AppointmentStatus.SCHEDULED);
        CurrentUser rec = new CurrentUser(3L, Role.RECEPTIONIST, null, null);

        assertThat(ids(rec, new AppointmentFilter(List.of(AppointmentStatus.SCHEDULED),
                NOW.plusDays(1).toLocalDate(), vetA.getId(), null))).containsExactly(a.getId());
    }

    @Test
    void debtFilterKeepsOnlyClosedWithoutPayment() {
        Appointment unpaid = save(annPet, vetA, NOW.plusDays(1), AppointmentStatus.CLOSED);
        Appointment paid = save(annPet, vetA, NOW.plusDays(2), AppointmentStatus.CLOSED);
        payments.save(new Payment(paid, new BigDecimal("60.00"), NOW));
        save(annPet, vetA, NOW.plusDays(3), AppointmentStatus.FINISHED);
        CurrentUser rec = new CurrentUser(3L, Role.RECEPTIONIST, null, null);

        assertThat(ids(rec, new AppointmentFilter(null, null, null, true))).containsExactly(unpaid.getId());
    }
}
