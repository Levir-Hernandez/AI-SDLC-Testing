package com.vetclinic.appointment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TASK.md: an unconfirmed reservation expires and frees the slot. */
@Component
public class ReservationExpiryJob {
    private final AppointmentActionService actions;

    public ReservationExpiryJob(AppointmentActionService actions) {
        this.actions = actions;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiry-sweep:PT30S}")
    public void sweep() {
        actions.expireDue();
    }
}
