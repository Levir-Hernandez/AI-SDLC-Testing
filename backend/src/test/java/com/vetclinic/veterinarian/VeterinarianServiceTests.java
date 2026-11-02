package com.vetclinic.veterinarian;

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
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class VeterinarianServiceTests {
    @Mock VeterinarianRepository repo;
    @Mock UserAccountRepository accountRepo;
    @Mock PasswordEncoder encoder;
    VeterinarianService service;

    @BeforeEach
    void setUp() {
        service = new VeterinarianService(repo, new ScheduleValidator(), new AccountService(accountRepo, encoder));
    }

    private static CreateVeterinarianRequest req(String username, List<ScheduleBlockDto> schedule) {
        return new CreateVeterinarianRequest("Dr. Who", "Surgery", new BigDecimal("40"), username, "secret1", schedule);
    }

    private static ScheduleBlockDto block(int sh, int eh) {
        return new ScheduleBlockDto(DayOfWeek.MONDAY, LocalTime.of(sh, 0), LocalTime.of(eh, 0));
    }

    @Test
    void createsVetWithLinkedAccountAndHashedPassword() {
        when(accountRepo.existsByUsername("who")).thenReturn(false);
        when(encoder.encode("secret1")).thenReturn("HASH");
        when(repo.save(any(Veterinarian.class))).thenAnswer(i -> {
            Veterinarian v = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "id", 7L);
            return v;
        });
        when(accountRepo.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        VeterinarianResponse res = service.create(req("who", List.of(block(9, 12), block(13, 17))));

        assertThat(res.id()).isEqualTo(7L);
        assertThat(res.costPerService()).isEqualByComparingTo("40.00");
        assertThat(res.schedule()).hasSize(2);
        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(accountRepo).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("HASH");
        assertThat(cap.getValue().getRole()).isEqualTo(Role.VETERINARIAN);
        assertThat(cap.getValue().getVeterinarianId()).isEqualTo(7L);
    }

    @Test
    void overlappingScheduleIsRejected() {
        assertThatThrownBy(() -> service.create(req("who", List.of(block(9, 12), block(11, 14)))))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("INVALID_SCHEDULE");
        verify(repo, never()).save(any());
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(repo.save(any(Veterinarian.class))).thenAnswer(i -> i.getArgument(0));
        when(accountRepo.existsByUsername("who")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req("who", List.of(block(9, 12)))))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("USERNAME_TAKEN");
        verify(accountRepo, never()).save(any());
    }

    @Test
    void unknownIdIsNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("VETERINARIAN_NOT_FOUND");
    }
}
