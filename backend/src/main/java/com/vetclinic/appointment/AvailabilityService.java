package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
    static final int STEP_MINUTES = 15;

    private final VeterinarianRepository vets;
    private final ClinicServiceRepository services;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public AvailabilityService(VeterinarianRepository vets, ClinicServiceRepository services,
                               AppointmentRepository appointments, Clock clock) {
        this.vets = vets;
        this.services = services;
        this.appointments = appointments;
        this.clock = clock;
    }

    /** TASK.md: a slot is available when the duration fits a schedule block and does not overlap a blocking appointment. */
    @Transactional(readOnly = true)
    public List<SlotResponse> slots(Long serviceId, Optional<Long> vetId, LocalDate date) {
        ClinicService service = services.findById(serviceId).orElseThrow(() ->
                new NotFoundException("SERVICE_NOT_FOUND", "Service not found: " + serviceId));
        List<Veterinarian> candidates = vetId.isPresent()
                ? List.of(vets.findById(vetId.get()).orElseThrow(() -> notFoundVet(vetId.get())))
                : vets.findAll();
        LocalDateTime now = LocalDateTime.now(clock);
        List<SlotResponse> out = new ArrayList<>();
        if (date.isBefore(now.toLocalDate())) {
            return out;
        }
        for (Veterinarian vet : candidates) {
            List<Appointment> blocking = appointments.findBlocking(vet.getId(), date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay(), now);
            for (ScheduleBlock block : vet.getSchedule()) {
                if (block.getDay() != date.getDayOfWeek()) {
                    continue;
                }
                LocalDateTime blockEnd = date.atTime(block.getEnd());
                for (LocalDateTime s = date.atTime(block.getStart());
                     !s.plusMinutes(service.getDurationMinutes()).isAfter(blockEnd); s = s.plusMinutes(STEP_MINUTES)) {
                    LocalDateTime start = s;
                    LocalDateTime end = s.plusMinutes(service.getDurationMinutes());
                    if (start.isAfter(now) && blocking.stream()
                            .noneMatch(b -> b.getStartAt().isBefore(end) && b.getEndAt().isAfter(start))) {
                        out.add(new SlotResponse(vet.getId(), vet.getName(), start, end));
                    }
                }
            }
        }
        return out;
    }

    /** Future, on the 15-minute grid and fully inside one schedule block of the veterinarian. */
    public boolean isBookable(Veterinarian vet, ClinicService service, LocalDateTime start) {
        if (!start.isAfter(LocalDateTime.now(clock))) {
            return false;
        }
        if (start.getMinute() % STEP_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            return false;
        }
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            return false;
        }
        return vet.getSchedule().stream().anyMatch(b -> b.getDay() == start.getDayOfWeek()
                && !start.toLocalTime().isBefore(b.getStart()) && !end.toLocalTime().isAfter(b.getEnd()));
    }

    private static NotFoundException notFoundVet(Long id) {
        return new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian not found: " + id);
    }
}
