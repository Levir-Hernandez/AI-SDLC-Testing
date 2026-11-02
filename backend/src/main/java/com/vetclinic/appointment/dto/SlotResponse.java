package com.vetclinic.appointment.dto;

import java.time.LocalDateTime;

public record SlotResponse(Long veterinarianId, String veterinarianName, LocalDateTime startAt, LocalDateTime endAt) {
}
