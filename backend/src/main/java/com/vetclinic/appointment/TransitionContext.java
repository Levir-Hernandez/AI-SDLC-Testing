package com.vetclinic.appointment;

import java.time.LocalDateTime;

public record TransitionContext(String reason, LocalDateTime now) {
}
