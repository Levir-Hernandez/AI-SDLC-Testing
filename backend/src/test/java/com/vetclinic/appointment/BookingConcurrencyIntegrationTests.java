package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;

import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** TASK.md: two bookings for the same veterinarian and overlapping time cannot both succeed. */
@SpringBootTest
class BookingConcurrencyIntegrationTests {
    @Autowired BookingService booking;
    @Autowired AppointmentRepository appointments;
    @Autowired OwnerRepository owners;
    @Autowired PetRepository pets;
    @Autowired VeterinarianRepository vets;
    @Autowired ClinicServiceRepository services;

    Owner owner;
    Pet pet;
    Veterinarian vet;
    ClinicService service;
    boolean serviceCreated;

    @BeforeEach
    void setUp() {
        owner = owners.save(new Owner("Race", "555-race-" + System.nanoTime()));
        pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
        List<ScheduleBlock> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(new ScheduleBlock(d, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        vet = vets.save(new Veterinarian("Dr. Race", "General", new BigDecimal("20.00"), week));
        service = services.findByCode(ServiceCode.GROOMING).orElse(null);
        if (service == null) {
            service = services.save(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("40.00")));
            serviceCreated = true;
        }
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll(appointments.findAll().stream()
                .filter(a -> a.getVeterinarian().getId().equals(vet.getId())).toList());
        pets.delete(pet);
        owners.delete(owner);
        vets.delete(vet);
        if (serviceCreated) {
            services.delete(service);
        }
    }

    @Test
    void onlyOneOfTwoSimultaneousBookingsSucceeds() throws Exception {
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(10, 0);
        CreateAppointmentRequest req = new CreateAppointmentRequest(pet.getId(), service.getId(), vet.getId(), start);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<String> attempt = () -> {
            barrier.await();
            try {
                booking.book(RECEPTION, req);
                return "OK";
            } catch (DomainException e) {
                return e.getCode();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);
            List<String> results = List.of(first.get(), second.get());

            assertThat(results).containsExactlyInAnyOrder("OK", "SLOT_TAKEN");
        } finally {
            pool.shutdownNow();
        }
        long saved = appointments.findAll().stream()
                .filter(a -> a.getVeterinarian().getId().equals(vet.getId())).count();
        assertThat(saved).isEqualTo(1);
    }
}
