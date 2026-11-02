package com.vetclinic.appointment;

public enum AppointmentStatus {
    RESERVED, SCHEDULED, IN_PROGRESS, FINISHED, PAID, CLOSED, RESCHEDULED, CANCELLED;

    public boolean isFinal() {
        return this == CLOSED || this == RESCHEDULED || this == CANCELLED;
    }
}
