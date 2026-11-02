package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.DomainException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PetServiceTests {
    @Mock PetRepository pets;
    @Mock OwnerRepository owners;
    @InjectMocks PetService service;

    static final CurrentUser OWNER1 = new CurrentUser(1L, Role.OWNER, 10L, null);
    static final CurrentUser RECEPTION = new CurrentUser(2L, Role.RECEPTIONIST, null, null);
    static final CurrentUser VET = new CurrentUser(3L, Role.VETERINARIAN, null, 5L);

    private static Owner owner(long id) {
        Owner o = new Owner("O" + id, "555" + id);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    @Test
    void addsDogWithLabradorRetriever() {
        when(owners.findById(10L)).thenReturn(Optional.of(owner(10L)));
        when(pets.save(any(Pet.class))).thenAnswer(i -> i.getArgument(0));

        PetResponse res = service.add(10L, new CreatePetRequest("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER));

        assertThat(res.breed()).isEqualTo(Breed.LABRADOR_RETRIEVER);
        assertThat(res.ownerId()).isEqualTo(10L);
    }

    @Test
    void dogWithPersianIsRejected() {
        when(owners.findById(10L)).thenReturn(Optional.of(owner(10L)));

        assertThatThrownBy(() -> service.add(10L, new CreatePetRequest("Rex", Species.DOG, Breed.PERSIAN)))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("BREED_SPECIES_MISMATCH");
    }

    @Test
    void ownerListsOnlyOwnPetsIgnoringOwnerId() {
        PageRequest pr = PageRequest.of(0, 10);
        Pet p = new Pet("Rex", Species.DOG, Breed.BULLDOG, owner(10L));
        when(pets.findByOwnerId(10L, pr)).thenReturn(new PageImpl<>(List.of(p), pr, 1));

        PageResponse<PetResponse> res = service.listFor(OWNER1, 99L, pr);

        assertThat(res.content()).hasSize(1);
    }

    @Test
    void receptionistWithoutOwnerIdGetsBadRequest() {
        assertThatThrownBy(() -> service.listFor(RECEPTION, null, PageRequest.of(0, 10)))
                .extracting(e -> ((DomainException) e).getStatus().value()).isEqualTo(400);
    }

    @Test
    void veterinarianListingIsForbidden() {
        assertThatThrownBy(() -> service.listFor(VET, 10L, PageRequest.of(0, 10)))
                .extracting(e -> ((DomainException) e).getStatus().value()).isEqualTo(403);
    }

    @Test
    void ownerReadingAnotherOwnersPetIsNotFound() {
        Pet other = new Pet("Tom", Species.CAT, Breed.SIAMESE, owner(11L));
        when(pets.findById(8L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.requireAccessible(OWNER1, 8L))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("PET_NOT_FOUND");
    }

    @Test
    void receptionistReadsAnyPet() {
        Pet other = new Pet("Tom", Species.CAT, Breed.SIAMESE, owner(11L));
        when(pets.findById(8L)).thenReturn(Optional.of(other));

        assertThat(service.requireAccessible(RECEPTION, 8L)).isSameAs(other);
    }
}
