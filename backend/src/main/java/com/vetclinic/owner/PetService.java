package com.vetclinic.owner;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetService {
    private final PetRepository pets;
    private final OwnerRepository owners;

    public PetService(PetRepository pets, OwnerRepository owners) {
        this.pets = pets;
        this.owners = owners;
    }

    @Transactional
    public PetResponse add(Long ownerId, CreatePetRequest req) {
        Owner owner = owners.findById(ownerId).orElseThrow(() ->
                new NotFoundException("OWNER_NOT_FOUND", "Owner not found: " + ownerId));
        return PetResponse.from(pets.save(new Pet(req.name(), req.species(), req.breed(), owner)));
    }

    /** TASK.md: owners see only their own pets; veterinarians have no pet directory. */
    @Transactional(readOnly = true)
    public PageResponse<PetResponse> listFor(CurrentUser user, Long ownerId, Pageable pageable) {
        Long target;
        if (user.role() == Role.OWNER) {
            target = user.ownerId();
        } else if (user.role() == Role.RECEPTIONIST) {
            if (ownerId == null) {
                throw new BadRequestException("OWNER_ID_REQUIRED", "ownerId is required");
            }
            target = ownerId;
        } else {
            throw new ForbiddenException("FORBIDDEN", "Not allowed to list pets");
        }
        return PageResponse.from(pets.findByOwnerId(target, pageable).map(PetResponse::from));
    }

    @Transactional(readOnly = true)
    public Pet requireAccessible(CurrentUser user, Long petId) {
        if (user.role() == Role.VETERINARIAN) {
            throw new ForbiddenException("FORBIDDEN", "Not allowed to access pets");
        }
        Pet pet = pets.findById(petId).orElseThrow(() -> notFound(petId));
        if (user.role() == Role.OWNER && !pet.getOwner().getId().equals(user.ownerId())) {
            throw notFound(petId);
        }
        return pet;
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("PET_NOT_FOUND", "Pet not found: " + id);
    }
}
