package com.vetclinic.payment;

import com.vetclinic.appointment.Appointment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private LocalDateTime paidAt;

    protected Payment() {}

    public Payment(Appointment appointment, BigDecimal amount, LocalDateTime paidAt) {
        this.appointment = appointment;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
