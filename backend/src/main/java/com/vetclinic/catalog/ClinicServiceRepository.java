package com.vetclinic.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, Long> {
    Optional<ClinicService> findByCode(ServiceCode code);
}
