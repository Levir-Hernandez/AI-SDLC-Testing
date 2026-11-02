package com.vetclinic.appointment.dto;

import com.vetclinic.appointment.Actor;
import com.vetclinic.appointment.AppointmentAction;
import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AppointmentResponse(Long id, PetSummary pet, String ownerName, ServiceSummary service,
                                  VetSummary veterinarian, LocalDateTime startAt, LocalDateTime endAt,
                                  AppointmentStatus status, Actor bookedBy, BigDecimal total, String observations,
                                  String cancelReason, LocalDateTime reservedUntil, Long rescheduledFromId,
                                  Long rescheduledToId, boolean debt, boolean upcoming,
                                  List<AppointmentAction> allowedActions) {
    public record PetSummary(Long id, String name, Species species, Breed breed) {
    }

    public record ServiceSummary(Long id, String name, int durationMinutes) {
    }

    public record VetSummary(Long id, String name) {
    }
}
