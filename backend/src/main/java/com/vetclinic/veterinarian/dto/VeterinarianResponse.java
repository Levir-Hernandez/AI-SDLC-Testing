package com.vetclinic.veterinarian.dto;

import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.util.List;

public record VeterinarianResponse(Long id, String name, String specialization, BigDecimal costPerService,
                                   List<ScheduleBlockDto> schedule) {
    public static VeterinarianResponse from(Veterinarian v) {
        return new VeterinarianResponse(v.getId(), v.getName(), v.getSpecialization(), v.getCostPerService(),
                v.getSchedule().stream().map(ScheduleBlockDto::from).toList());
    }
}
