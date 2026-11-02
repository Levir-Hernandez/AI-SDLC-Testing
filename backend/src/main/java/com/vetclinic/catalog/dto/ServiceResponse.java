package com.vetclinic.catalog.dto;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import java.math.BigDecimal;

public record ServiceResponse(Long id, ServiceCode code, String name, int durationMinutes, BigDecimal basePrice) {
    public static ServiceResponse from(ClinicService s) {
        return new ServiceResponse(s.getId(), s.getCode(), s.getName(), s.getDurationMinutes(), s.getBasePrice());
    }
}
