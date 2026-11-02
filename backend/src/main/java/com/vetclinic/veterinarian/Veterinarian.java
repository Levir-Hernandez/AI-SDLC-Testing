package com.vetclinic.veterinarian;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "veterinarian")
public class Veterinarian {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String specialization;
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costPerService;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "veterinarian_schedule", joinColumns = @JoinColumn(name = "veterinarian_id"))
    private List<ScheduleBlock> schedule = new ArrayList<>();

    protected Veterinarian() {}

    public Veterinarian(String name, String specialization, BigDecimal costPerService, List<ScheduleBlock> schedule) {
        this.name = name;
        this.specialization = specialization;
        this.costPerService = costPerService;
        this.schedule = new ArrayList<>(schedule);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSpecialization() { return specialization; }
    public BigDecimal getCostPerService() { return costPerService; }
    public List<ScheduleBlock> getSchedule() { return schedule; }
}
