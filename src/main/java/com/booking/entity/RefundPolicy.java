package com.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A refund policy is a set of tiers based on hours-before-show.
 * For simplicity each row is one tier: if cancelled at least {hoursBeforeShow} before show, refund {refundPercentage}%.
 * The most generous applicable tier wins. Active policy is selected by `active=true`.
 */
@Entity
@Table(name = "refund_policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer hoursBeforeShow;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercentage; // 0-100

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
