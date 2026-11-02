package com.vetclinic.owner.dto;

import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;

public record PetResponse(Long id, String name, Species species, Breed breed, Long ownerId) {
    public static PetResponse from(Pet p) {
        return new PetResponse(p.getId(), p.getName(), p.getSpecies(), p.getBreed(), p.getOwner().getId());
    }
}
