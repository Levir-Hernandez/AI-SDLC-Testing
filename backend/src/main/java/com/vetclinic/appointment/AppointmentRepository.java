package com.vetclinic.appointment;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    String BLOCKING = "(a.status in (com.vetclinic.appointment.AppointmentStatus.SCHEDULED,"
            + " com.vetclinic.appointment.AppointmentStatus.IN_PROGRESS)"
            + " or (a.status = com.vetclinic.appointment.AppointmentStatus.RESERVED and a.reservedUntil > :now))";

    @Query("select count(a) > 0 from Appointment a where a.veterinarian.id = :vetId and a.startAt < :end"
            + " and a.endAt > :start and " + BLOCKING + " and (:excludeId is null or a.id <> :excludeId)")
    boolean existsOverlap(@Param("vetId") Long vetId, @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end, @Param("now") LocalDateTime now,
                          @Param("excludeId") Long excludeId);

    @Query("select a from Appointment a where a.veterinarian.id = :vetId and a.startAt < :dayEnd"
            + " and a.endAt > :dayStart and " + BLOCKING + " order by a.startAt")
    List<Appointment> findBlocking(@Param("vetId") Long vetId, @Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd, @Param("now") LocalDateTime now);

    List<Appointment> findByStatusAndReservedUntilLessThanEqual(AppointmentStatus status, LocalDateTime time);

    List<Appointment> findByRescheduledFromId(Long id);
}
