package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AppointmentRepositoryTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Autowired AppointmentRepository appointments;
    @Autowired OwnerRepository owners;
    @Autowired PetRepository pets;
    @Autowired VeterinarianRepository vets;
    @Autowired ClinicServiceRepository services;
    @Autowired PaymentRepository payments;

    Pet pet;
    ClinicService service;
    Veterinarian vet;

    @BeforeEach
    void setUp() {
        Owner owner = owners.save(new Owner("Ann", "555-0100"));
        pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
        service = services.save(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("30.00")));
        vet = vets.save(new Veterinarian("Dr. Who", "General", new BigDecimal("10.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
    }

    private Appointment save(AppointmentStatus status, LocalDateTime reservedUntil) {
        return appointments.save(new Appointment(pet, service, vet, START, END, status, Actor.RECEPTIONIST,
                new BigDecimal("40.00"), reservedUntil, null, NOW));
    }

    @Test
    void scheduledAndInProgressBlock() {
        save(AppointmentStatus.SCHEDULED, null);
        assertThat(appointments.existsOverlap(vet.getId(), START.plusMinutes(30), END.plusMinutes(30), NOW, null)).isTrue();
        Appointment other = save(AppointmentStatus.IN_PROGRESS, null);
        assertThat(appointments.existsOverlap(vet.getId(), START, END, NOW, other.getId())).isTrue();
        assertThat(appointments.findBlocking(vet.getId(), START.toLocalDate().atStartOfDay(),
                START.toLocalDate().plusDays(1).atStartOfDay(), NOW)).hasSize(2);
    }

    @Test
    void liveReservationBlocksButExpiredDoesNot() {
        Appointment live = save(AppointmentStatus.RESERVED, NOW.plusMinutes(10));
        assertThat(appointments.existsOverlap(vet.getId(), START, END, NOW, null)).isTrue();
        assertThat(appointments.existsOverlap(vet.getId(), START, END, NOW.plusMinutes(10), null)).isFalse();
        assertThat(appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED,
                NOW.plusMinutes(10))).containsExactly(live);
    }

    @Test
    void adjacentDoesNotOverlap() {
        save(AppointmentStatus.SCHEDULED, null);
        assertThat(appointments.existsOverlap(vet.getId(), END, END.plusHours(1), NOW, null)).isFalse();
        assertThat(appointments.existsOverlap(vet.getId(), START.minusHours(1), START, NOW, null)).isFalse();
    }

    @Test
    void excludeIdIgnoresOwnRow() {
        Appointment a = save(AppointmentStatus.SCHEDULED, null);
        assertThat(appointments.existsOverlap(vet.getId(), START, END, NOW, a.getId())).isFalse();
    }

    @Test
    void nonBlockingStatusesDoNotOverlap() {
        save(AppointmentStatus.CANCELLED, null);
        save(AppointmentStatus.RESCHEDULED, null);
        save(AppointmentStatus.FINISHED, null);
        assertThat(appointments.existsOverlap(vet.getId(), START, END, NOW, null)).isFalse();
    }

    @Test
    void findsRescheduledFrom() {
        Appointment old = save(AppointmentStatus.RESCHEDULED, null);
        Appointment next = appointments.save(new Appointment(pet, service, vet, START.plusDays(1), END.plusDays(1),
                AppointmentStatus.SCHEDULED, Actor.RECEPTIONIST, new BigDecimal("40.00"), null, old, NOW));
        assertThat(appointments.findByRescheduledFromId(old.getId())).containsExactly(next);
    }

    @Test
    void duplicatePhoneRejected() {
        assertThatThrownBy(() -> owners.saveAndFlush(new Owner("Bob", "555-0100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicatePaymentPerAppointmentRejected() {
        Appointment a = save(AppointmentStatus.FINISHED, null);
        payments.saveAndFlush(new Payment(a, new BigDecimal("40.00"), NOW));
        assertThat(payments.existsByAppointmentId(a.getId())).isTrue();
        assertThatThrownBy(() -> payments.saveAndFlush(new Payment(a, new BigDecimal("40.00"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
