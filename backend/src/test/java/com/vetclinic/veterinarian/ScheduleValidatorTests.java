package com.vetclinic.veterinarian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.shared.error.BadRequestException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleValidatorTests {
    private final ScheduleValidator validator = new ScheduleValidator();

    private static ScheduleBlock block(DayOfWeek d, String s, String e) {
        return new ScheduleBlock(d, LocalTime.parse(s), LocalTime.parse(e));
    }

    @Test
    void acceptsNonOverlappingAndAdjacentBlocks() {
        assertThatCode(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, "09:00", "12:00"),
                block(DayOfWeek.MONDAY, "12:00", "15:00"),
                block(DayOfWeek.TUESDAY, "09:00", "13:00")))).doesNotThrowAnyException();
    }

    @Test
    void rejectsOverlap() {
        assertThatThrownBy(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, "12:00", "15:00"),
                block(DayOfWeek.MONDAY, "09:00", "13:00"))))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_SCHEDULE");
                    assertThat(e.getMessage())
                            .isEqualTo("Blocks on MONDAY 09:00–13:00 and 12:00–15:00 overlap");
                });
    }

    @Test
    void rejectsStartEqualOrAfterEnd() {
        assertThatThrownBy(() -> validator.validate(List.of(block(DayOfWeek.MONDAY, "09:00", "09:00"))))
                .hasMessage("Block on MONDAY must start before it ends");
        assertThatThrownBy(() -> validator.validate(List.of(block(DayOfWeek.MONDAY, "10:00", "09:00"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> validator.validate(List.of())).isInstanceOf(BadRequestException.class);
    }
}
