package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RescheduleRequest(@NotNull Long veterinarianId, @NotNull LocalDateTime startAt) {
}
