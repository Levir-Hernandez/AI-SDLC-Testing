package com.vetclinic.appointment;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Shared builders for appointment/payment unit tests. NOW is a Monday 08:00. */
public final class Fixtures {
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    public static final LocalDateTime MON_10 = NOW.withHour(10);

    public static final CurrentUser OWNER10 = new CurrentUser(1L, Role.OWNER, 10L, null);
    public static final CurrentUser OWNER11 = new CurrentUser(4L, Role.OWNER, 11L, null);
    public static final CurrentUser RECEPTION = new CurrentUser(2L, Role.RECEPTIONIST, null, null);
    public static final CurrentUser VET5 = new CurrentUser(3L, Role.VETERINARIAN, null, 5L);
    public static final CurrentUser VET6 = new CurrentUser(5L, Role.VETERINARIAN, null, 6L);

    private Fixtures() {}

    /** Clock whose instant can be moved during a test. */
    public static final class MutableClock extends Clock {
        private Instant instant;

        public MutableClock(LocalDateTime at) {
            this.instant = at.toInstant(ZoneOffset.UTC);
        }

        public void set(LocalDateTime at) {
            this.instant = at.toInstant(ZoneOffset.UTC);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static Veterinarian vet(long id) {
        return withId(new Veterinarian("Dr. " + id, "General", new BigDecimal("20.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))), id);
    }

    public static ClinicService service(long id, int minutes) {
        return withId(new ClinicService(ServiceCode.GROOMING, "Grooming", minutes, new BigDecimal("40.00")), id);
    }

    public static Pet pet(long id, long ownerId) {
        Owner o = withId(new Owner("Owner" + ownerId, "555" + ownerId), ownerId);
        return withId(new Pet("Rex" + id, Species.DOG, Breed.BULLDOG, o), id);
    }

    public static Appointment appt(long id, Pet pet, Veterinarian vet, LocalDateTime start,
                                   AppointmentStatus status, LocalDateTime reservedUntil) {
        ClinicService s = service(1L, 60);
        return withId(new Appointment(pet, s, vet, start, start.plusMinutes(60), status, Actor.RECEPTIONIST,
                new BigDecimal("60.00"), reservedUntil, null, NOW.minusDays(1)), id);
    }
}
