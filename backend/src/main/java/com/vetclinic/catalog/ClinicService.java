package com.vetclinic.catalog;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

@Entity
@Table(name = "clinic_service")
public class ClinicService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ServiceCode code;
    @Column(nullable = false)
    private String name;
    @Min(1)
    @Column(nullable = false)
    private int durationMinutes;
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    protected ClinicService() {}

    public ClinicService(ServiceCode code, String name, int durationMinutes, BigDecimal basePrice) {
        this.code = code;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.basePrice = basePrice;
    }

    public Long getId() { return id; }
    public ServiceCode getCode() { return code; }
    public String getName() { return name; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getBasePrice() { return basePrice; }
}
