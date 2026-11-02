package com.vetclinic.owner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    boolean existsByPhone(String phone);

    @Query("select o from Owner o where :q is null or lower(o.name) like lower(concat('%', :q, '%')) or o.phone like concat('%', :q, '%')")
    Page<Owner> search(@Param("q") String q, Pageable pageable);
}
