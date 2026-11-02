package com.vetclinic.veterinarian;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VeterinarianRepository extends JpaRepository<Veterinarian, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Veterinarian v where v.id = :id")
    Optional<Veterinarian> findLockedById(@Param("id") Long id);
}
