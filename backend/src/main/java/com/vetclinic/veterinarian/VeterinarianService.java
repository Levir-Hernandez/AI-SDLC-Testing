package com.vetclinic.veterinarian;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VeterinarianService {
    private final VeterinarianRepository repo;
    private final ScheduleValidator validator;
    private final AccountService accounts;

    public VeterinarianService(VeterinarianRepository repo, ScheduleValidator validator, AccountService accounts) {
        this.repo = repo;
        this.validator = validator;
        this.accounts = accounts;
    }

    @Transactional
    public VeterinarianResponse create(CreateVeterinarianRequest req) {
        List<ScheduleBlock> blocks = req.schedule().stream().map(ScheduleBlockDto::toEntity).toList();
        validator.validate(blocks);
        Veterinarian vet = repo.save(new Veterinarian(req.name(), req.specialization(),
                req.costPerService().setScale(2, RoundingMode.HALF_UP), blocks));
        accounts.create(req.username(), req.password(), Role.VETERINARIAN, req.name(), null, vet.getId());
        return VeterinarianResponse.from(vet);
    }

    @Transactional(readOnly = true)
    public PageResponse<VeterinarianResponse> list(Pageable pageable) {
        return PageResponse.from(repo.findAll(pageable).map(VeterinarianResponse::from));
    }

    @Transactional(readOnly = true)
    public VeterinarianResponse get(Long id) {
        return VeterinarianResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Veterinarian require(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian not found: " + id));
    }
}
