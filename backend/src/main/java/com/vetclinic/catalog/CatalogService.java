package com.vetclinic.catalog;

import com.vetclinic.catalog.dto.ServiceResponse;
import com.vetclinic.shared.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final ClinicServiceRepository repo;

    public CatalogService(ClinicServiceRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return repo.findAll().stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClinicService require(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new NotFoundException("SERVICE_NOT_FOUND", "Service not found: " + id));
    }
}
