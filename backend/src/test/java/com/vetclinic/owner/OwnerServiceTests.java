package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccount;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.shared.error.DomainException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTests {
    @Mock OwnerRepository owners;
    @Mock PetRepository pets;
    @Mock UserAccountRepository accountRepo;
    @Mock PasswordEncoder encoder;
    OwnerService service;

    @BeforeEach
    void setUp() {
        service = new OwnerService(owners, pets, new AccountService(accountRepo, encoder));
    }

    private static CreateOwnerRequest req(List<CreatePetRequest> pets) {
        return new CreateOwnerRequest("Ann", "5551234567", "secret1", pets);
    }

    @Test
    void createsOwnerAccountAndInitialPets() {
        when(owners.existsByPhone("5551234567")).thenReturn(false);
        when(owners.save(any(Owner.class))).thenAnswer(i -> {
            Owner o = i.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 3L);
            return o;
        });
        when(pets.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode("secret1")).thenReturn("HASH");
        when(accountRepo.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        OwnerResponse res = service.create(req(List.of(
                new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG))));

        assertThat(res.id()).isEqualTo(3L);
        assertThat(res.pets()).hasSize(1);
        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(accountRepo).save(cap.capture());
        assertThat(cap.getValue().getUsername()).isEqualTo("5551234567");
        assertThat(cap.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(cap.getValue().getOwnerId()).isEqualTo(3L);
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("HASH");
    }

    @Test
    void duplicatePhoneIsRejected() {
        when(owners.existsByPhone("5551234567")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req(null)))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("PHONE_TAKEN");
        verify(owners, never()).save(any());
    }

    @Test
    void mismatchedInitialPetSavesNothing() {
        assertThatThrownBy(() -> service.create(req(List.of(
                new CreatePetRequest("Tom", Species.DOG, Breed.PERSIAN)))))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("BREED_SPECIES_MISMATCH");
        verify(owners, never()).save(any());
        verify(pets, never()).saveAll(any());
        verify(accountRepo, never()).save(any());
    }

    @Test
    void unknownOwnerIsNotFound() {
        when(owners.findById(5L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(5L))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("OWNER_NOT_FOUND");
    }
}
