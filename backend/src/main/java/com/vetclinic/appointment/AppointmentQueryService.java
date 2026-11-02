package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentQueryService {
    private final AppointmentRepository appointments;
    private final PaymentRepository payments;
    private final PetRepository pets;
    private final PetService petService;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public AppointmentQueryService(AppointmentRepository appointments, PaymentRepository payments,
                                   PetRepository pets, PetService petService, AppointmentLifecycle lifecycle,
                                   Clock clock) {
        this.appointments = appointments;
        this.payments = payments;
        this.pets = pets;
        this.petService = petService;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** TASK.md: owners see only their own appointments; veterinarians only their assigned ones. */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> list(CurrentUser user, AppointmentFilter filter, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startAt"));
        Long vetId = filter.veterinarianId();
        List<Long> petIds = null;
        if (user.role() == Role.OWNER) {
            petIds = pets.findByOwnerId(user.ownerId()).stream().map(Pet::getId).toList();
            if (petIds.isEmpty()) {
                return PageResponse.from(new PageImpl<AppointmentResponse>(List.of(), sorted, 0));
            }
        } else if (user.role() == Role.VETERINARIAN) {
            vetId = user.veterinarianId();
        }
        Specification<Appointment> spec = buildSpec(filter, vetId, petIds);
        Page<AppointmentResponse> page = appointments.findAll(spec, sorted)
                .map(a -> toResponse(a, user.actor()));
        return PageResponse.from(page);
    }

    private Specification<Appointment> buildSpec(AppointmentFilter filter, Long vetId, List<Long> petIds) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (petIds != null) {
                ps.add(root.get("pet").get("id").in(petIds));
            }
            if (vetId != null) {
                ps.add(cb.equal(root.get("veterinarian").get("id"), vetId));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                ps.add(root.get("status").in(filter.statuses()));
            }
            if (filter.date() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startAt"), filter.date().atStartOfDay()));
                ps.add(cb.lessThan(root.<LocalDateTime>get("startAt"), filter.date().plusDays(1).atStartOfDay()));
            }
            if (filter.debt() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<Payment> p = sq.from(Payment.class);
                sq.select(p.<Long>get("id")).where(cb.equal(p.get("appointment").get("id"), root.get("id")));
                Predicate debt = cb.and(cb.equal(root.get("status"), AppointmentStatus.CLOSED),
                        cb.not(cb.exists(sq)));
                ps.add(filter.debt() ? debt : cb.not(debt));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(CurrentUser user, Long id) {
        return toResponse(requireScoped(user, id), user.actor());
    }

    /** Out-of-scope appointments look like missing ones (404 APPOINTMENT_NOT_FOUND). */
    @Transactional(readOnly = true)
    public Appointment requireScoped(CurrentUser user, Long id) {
        Appointment a = appointments.findById(id).orElseThrow(() -> notFound(id));
        boolean inScope = switch (user.role()) {
            case RECEPTIONIST -> true;
            case OWNER -> a.getPet().getOwner().getId().equals(user.ownerId());
            case VETERINARIAN -> a.getVeterinarian().getId().equals(user.veterinarianId());
        };
        if (!inScope) {
            throw notFound(id);
        }
        return a;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> historyForPet(CurrentUser user, Long petId, Pageable pageable) {
        petService.requireAccessible(user, petId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "startAt"));
        return PageResponse.from(appointments.findByPetId(petId, sorted).map(a -> toResponse(a, user.actor())));
    }

    public AppointmentResponse toResponse(Appointment a, Actor actor) {
        Pet pet = a.getPet();
        Long toId = a.getStatus() == AppointmentStatus.RESCHEDULED
                ? appointments.findByRescheduledFromId(a.getId()).stream().findFirst().map(Appointment::getId)
                        .orElse(null)
                : null;
        boolean debt = a.getStatus() == AppointmentStatus.CLOSED && !payments.existsByAppointmentId(a.getId());
        return new AppointmentResponse(a.getId(),
                new AppointmentResponse.PetSummary(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed()),
                pet.getOwner().getName(),
                new AppointmentResponse.ServiceSummary(a.getService().getId(), a.getService().getName(),
                        a.getService().getDurationMinutes()),
                new AppointmentResponse.VetSummary(a.getVeterinarian().getId(), a.getVeterinarian().getName()),
                a.getStartAt(), a.getEndAt(), a.getStatus(), a.getBookedBy(), a.getTotal(), a.getObservations(),
                a.getCancelReason(), a.getReservedUntil(),
                a.getRescheduledFrom() == null ? null : a.getRescheduledFrom().getId(), toId, debt,
                a.getStartAt().isAfter(LocalDateTime.now(clock)),
                List.copyOf(lifecycle.allowedActions(a, actor)));
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("APPOINTMENT_NOT_FOUND", "Appointment not found: " + id);
    }
}
