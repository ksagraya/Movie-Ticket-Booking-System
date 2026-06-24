package com.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "show_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}),
        indexes = {
                @Index(name = "idx_showseat_show", columnList = "show_id"),
                @Index(name = "idx_showseat_status_expiry", columnList = "status,hold_expires_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShowSeat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowSeatStatus status;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Version
    private Long version;
}
