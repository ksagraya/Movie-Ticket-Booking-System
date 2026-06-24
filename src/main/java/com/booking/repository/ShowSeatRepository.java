package com.booking.repository;

import com.booking.entity.ShowSeat;
import com.booking.entity.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowId(Long showId);

    /**
     * Pessimistic write-lock for the rows; serializes concurrent hold attempts on the same seats.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.id IN :ids")
    List<ShowSeat> findAllByIdsForUpdate(@Param("ids") List<Long> ids);

    @Query("SELECT ss FROM ShowSeat ss WHERE ss.status = :status AND ss.holdExpiresAt IS NOT NULL AND ss.holdExpiresAt < :now")
    List<ShowSeat> findExpiredHolds(@Param("status") ShowSeatStatus status, @Param("now") LocalDateTime now);
}
