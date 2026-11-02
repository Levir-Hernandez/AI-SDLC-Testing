package com.vetclinic.owner.dto;

import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePetRequest(@NotBlank String name, @NotNull Species species, @NotNull Breed breed) {
}
