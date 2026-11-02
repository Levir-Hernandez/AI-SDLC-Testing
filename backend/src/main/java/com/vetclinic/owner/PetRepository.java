package com.vetclinic.owner;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {
    Page<Pet> findByOwnerId(Long ownerId, Pageable pageable);

    List<Pet> findByOwnerId(Long ownerId);
}
