package com.vetclinic.owner.dto;

import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import java.util.List;

public record OwnerResponse(Long id, String name, String phone, List<PetResponse> pets) {
    public static OwnerResponse from(Owner o, List<Pet> pets) {
        return new OwnerResponse(o.getId(), o.getName(), o.getPhone(), pets.stream().map(PetResponse::from).toList());
    }
}
