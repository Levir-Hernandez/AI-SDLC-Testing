package com.vetclinic.veterinarian;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Embeddable
public class ScheduleBlock {
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek day;
    @Column(name = "start_time", nullable = false)
    private LocalTime start;
    @Column(name = "end_time", nullable = false)
    private LocalTime end;

    protected ScheduleBlock() {}

    public ScheduleBlock(DayOfWeek day, LocalTime start, LocalTime end) {
        this.day = day;
        this.start = start;
        this.end = end;
    }

    public DayOfWeek getDay() { return day; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
}
