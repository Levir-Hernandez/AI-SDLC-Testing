package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateAppointmentRequest(@NotNull Long petId, @NotNull Long serviceId,
                                       @NotNull Long veterinarianId, @NotNull LocalDateTime startAt) {
}
