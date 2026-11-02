package com.vetclinic.auth;

import com.vetclinic.appointment.Actor;

public record CurrentUser(Long userId, Role role, Long ownerId, Long veterinarianId) {
    public Actor actor() {
        return Actor.from(role);
    }
}
