package com.vetclinic.veterinarian;

import com.vetclinic.shared.error.BadRequestException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** TASK.md: schedule blocks must not overlap on the same weekday and start must precede end. */
@Component
public class ScheduleValidator {
    public void validate(List<ScheduleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BadRequestException("INVALID_SCHEDULE", "At least one schedule block is required");
        }
        for (ScheduleBlock b : blocks) {
            if (!b.getStart().isBefore(b.getEnd())) {
                throw new BadRequestException("INVALID_SCHEDULE",
                        "Block on " + b.getDay() + " must start before it ends");
            }
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            List<ScheduleBlock> sorted = blocks.stream().filter(b -> b.getDay() == day)
                    .sorted(Comparator.comparing(ScheduleBlock::getStart)).toList();
            for (int i = 1; i < sorted.size(); i++) {
                ScheduleBlock a = sorted.get(i - 1);
                ScheduleBlock b = sorted.get(i);
                if (b.getStart().isBefore(a.getEnd())) {
                    throw new BadRequestException("INVALID_SCHEDULE", "Blocks on " + day + " "
                            + range(a.getStart(), a.getEnd()) + " and " + range(b.getStart(), b.getEnd()) + " overlap");
                }
            }
        }
    }

    private static String range(LocalTime s, LocalTime e) {
        return s + "–" + e;
    }
}
