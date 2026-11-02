package com.vetclinic.appointment;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Pet;
import com.vetclinic.veterinarian.Veterinarian;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appt_vet_start", columnList = "veterinarian_id,start_at"),
        @Index(name = "idx_appt_pet", columnList = "pet_id"),
        @Index(name = "idx_appt_status", columnList = "status"),
        @Index(name = "idx_appt_rescheduled_from", columnList = "rescheduled_from_id")})
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ClinicService service;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id", nullable = false)
    private Veterinarian veterinarian;
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor bookedBy;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    @Column(length = 2000)
    private String observations;
    private LocalDateTime reservedUntil;
    private String cancelReason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_id")
    private Appointment rescheduledFrom;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Appointment() {}

    public Appointment(Pet pet, ClinicService service, Veterinarian veterinarian, LocalDateTime startAt,
                       LocalDateTime endAt, AppointmentStatus status, Actor bookedBy, BigDecimal total,
                       LocalDateTime reservedUntil, Appointment rescheduledFrom, LocalDateTime createdAt) {
        this.pet = pet;
        this.service = service;
        this.veterinarian = veterinarian;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
        this.bookedBy = bookedBy;
        this.total = total;
        this.reservedUntil = reservedUntil;
        this.rescheduledFrom = rescheduledFrom;
        this.createdAt = createdAt;
    }

    /** Only AppointmentLifecycle changes the status. */
    void applyStatus(AppointmentStatus newStatus, String cancelReason) {
        this.status = newStatus;
        if (cancelReason != null) {
            this.cancelReason = cancelReason;
        }
    }

    public void setObservations(String observations) { this.observations = observations; }

    public Long getId() { return id; }
    public Pet getPet() { return pet; }
    public ClinicService getService() { return service; }
    public Veterinarian getVeterinarian() { return veterinarian; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public Actor getBookedBy() { return bookedBy; }
    public BigDecimal getTotal() { return total; }
    public String getObservations() { return observations; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }
    public String getCancelReason() { return cancelReason; }
    public Appointment getRescheduledFrom() { return rescheduledFrom; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
