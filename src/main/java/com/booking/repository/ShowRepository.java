package com.booking.repository;

import com.booking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("SELECT s FROM Show s " +
            "WHERE (:cityId IS NULL OR s.screen.theater.city.id = :cityId) " +
            "AND (:movieId IS NULL OR s.movie.id = :movieId) " +
            "AND (:fromTime IS NULL OR s.startTime >= :fromTime) " +
            "AND (:toTime IS NULL OR s.startTime < :toTime) " +
            "ORDER BY s.startTime ASC")
    List<Show> search(@Param("cityId") Long cityId,
                      @Param("movieId") Long movieId,
                      @Param("fromTime") LocalDateTime fromTime,
                      @Param("toTime") LocalDateTime toTime);
}
