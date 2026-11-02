package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final AppointmentRepository appointments;
    private final VeterinarianRepository vets;
    private final ClinicServiceRepository services;
    private final PetService petService;
    private final AvailabilityService availability;
    private final AppointmentQueryService queries;
    private final Clock clock;
    private final Duration reservationTtl;

    public BookingService(AppointmentRepository appointments, VeterinarianRepository vets,
                          ClinicServiceRepository services, PetService petService, AvailabilityService availability,
                          AppointmentQueryService queries, Clock clock,
                          @Value("${app.booking.reservation-ttl:PT10M}") Duration reservationTtl) {
        this.appointments = appointments;
        this.vets = vets;
        this.services = services;
        this.petService = petService;
        this.availability = availability;
        this.queries = queries;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    /** Receptionist books a SCHEDULED walk-in; owner books a RESERVED hold; veterinarians cannot book. */
    @Transactional
    public AppointmentResponse book(CurrentUser user, CreateAppointmentRequest req) {
        if (user.role() == Role.VETERINARIAN) {
            throw new ForbiddenException("FORBIDDEN", "Veterinarians cannot book appointments");
        }
        Pet pet = petService.requireAccessible(user, req.petId());
        ClinicService service = services.findById(req.serviceId()).orElseThrow(() ->
                new NotFoundException("SERVICE_NOT_FOUND", "Service not found: " + req.serviceId()));
        Veterinarian vet = vets.findById(req.veterinarianId()).orElseThrow(() -> vetNotFound(req.veterinarianId()));
        Appointment created;
        if (user.role() == Role.OWNER) {
            created = create(pet, service, vet, req.startAt(), Actor.OWNER, AppointmentStatus.RESERVED,
                    LocalDateTime.now(clock).plus(reservationTtl), null, null);
        } else {
            created = createScheduled(pet, service, vet, req.startAt(), Actor.RECEPTIONIST, null, null);
        }
        return queries.toResponse(created, user.actor());
    }

    /** Shared by booking and reschedule; TASK.md: two bookings cannot take the same slot. */
    @Transactional
    public Appointment createScheduled(Pet pet, ClinicService service, Veterinarian vet, LocalDateTime start,
                                       Actor bookedBy, Appointment rescheduledFrom, Long excludeId) {
        return create(pet, service, vet, start, bookedBy, AppointmentStatus.SCHEDULED, null, rescheduledFrom,
                excludeId);
    }

    private Appointment create(Pet pet, ClinicService service, Veterinarian vet, LocalDateTime start, Actor bookedBy,
                               AppointmentStatus status, LocalDateTime reservedUntil, Appointment rescheduledFrom,
                               Long excludeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!start.isAfter(now)) {
            throw new BadRequestException("START_IN_PAST", "Appointments must start in the future");
        }
        // Row lock serializes concurrent bookings for the same veterinarian.
        Veterinarian locked = vets.findLockedById(vet.getId()).orElseThrow(() -> vetNotFound(vet.getId()));
        if (service.getBasePrice().signum() < 0 || locked.getCostPerService().signum() < 0) {
            throw new BadRequestException("VALIDATION_FAILED", "Prices must not be negative");
        }
        if (!availability.isBookable(locked, service, start)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "The veterinarian is not available at that time");
        }
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        if (appointments.existsOverlap(locked.getId(), start, end, now, excludeId)) {
            throw new ConflictException("SLOT_TAKEN", "That slot is already taken");
        }
        BigDecimal total = service.getBasePrice().add(locked.getCostPerService()).setScale(2, RoundingMode.HALF_UP);
        return appointments.save(new Appointment(pet, service, locked, start, end, status, bookedBy, total,
                reservedUntil, rescheduledFrom, now));
    }

    private static NotFoundException vetNotFound(Long id) {
        return new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian not found: " + id);
    }
}
