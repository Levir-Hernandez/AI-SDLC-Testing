package com.vetclinic.owner;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerService {
    private final OwnerRepository owners;
    private final PetRepository pets;
    private final AccountService accounts;

    public OwnerService(OwnerRepository owners, PetRepository pets, AccountService accounts) {
        this.owners = owners;
        this.pets = pets;
        this.accounts = accounts;
    }

    @Transactional
    public OwnerResponse create(CreateOwnerRequest req) {
        if (owners.existsByPhone(req.phone())) {
            throw new ConflictException("PHONE_TAKEN", "Phone is already registered: " + req.phone());
        }
        // Validate every pet first so nothing is saved when one is invalid (TASK.md: breed must match species).
        Owner owner = new Owner(req.name(), req.phone());
        List<CreatePetRequest> requested = req.pets() == null ? List.of() : req.pets();
        List<Pet> toSave = new ArrayList<>();
        for (CreatePetRequest p : requested) {
            toSave.add(new Pet(p.name(), p.species(), p.breed(), owner));
        }
        owner = owners.save(owner);
        List<Pet> created = pets.saveAll(toSave);
        accounts.create(req.phone(), req.password(), Role.OWNER, req.name(), owner.getId(), null);
        return OwnerResponse.from(owner, created);
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerResponse> list(String query, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(owners.search(q, pageable)
                .map(o -> OwnerResponse.from(o, pets.findByOwnerId(o.getId()))));
    }

    @Transactional(readOnly = true)
    public OwnerResponse get(Long id) {
        return OwnerResponse.from(require(id), pets.findByOwnerId(id));
    }

    @Transactional(readOnly = true)
    public Owner require(Long id) {
        return owners.findById(id).orElseThrow(() ->
                new NotFoundException("OWNER_NOT_FOUND", "Owner not found: " + id));
    }
}
