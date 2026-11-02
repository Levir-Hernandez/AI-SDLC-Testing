package com.vetclinic.veterinarian.dto;

import com.vetclinic.veterinarian.ScheduleBlock;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleBlockDto(@NotNull DayOfWeek day, @NotNull LocalTime start, @NotNull LocalTime end) {
    public static ScheduleBlockDto from(ScheduleBlock b) {
        return new ScheduleBlockDto(b.getDay(), b.getStart(), b.getEnd());
    }

    public ScheduleBlock toEntity() {
        return new ScheduleBlock(day, start, end);
    }
}
