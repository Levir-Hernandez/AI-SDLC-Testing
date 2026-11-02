package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.Size;

public record ObservationsRequest(@Size(max = 2000) String observations) {
}
