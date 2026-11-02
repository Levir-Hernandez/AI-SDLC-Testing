package com.vetclinic.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOwnerRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optional leading +") String phone,
        @Size(min = 6) String password,
        @Valid List<CreatePetRequest> pets) {
}
