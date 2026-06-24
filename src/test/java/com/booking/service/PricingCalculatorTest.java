package com.booking.service;

import com.booking.entity.PricingTier;
import com.booking.entity.SeatCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingCalculatorTest {

    @Test
    void regularSeat_regularTier_isBasePrice() {
        BigDecimal p = PricingCalculator.priceFor(SeatCategory.REGULAR,
                new BigDecimal("200.00"), new BigDecimal("400.00"), PricingTier.REGULAR);
        assertEquals(new BigDecimal("200.00"), p);
    }

    @Test
    void premiumSeat_weekendTier_appliesMultiplier() {
        BigDecimal p = PricingCalculator.priceFor(SeatCategory.PREMIUM,
                new BigDecimal("200.00"), new BigDecimal("400.00"), PricingTier.WEEKEND);
        // 400 * 1.25 = 500.00
        assertEquals(new BigDecimal("500.00"), p);
    }

    @Test
    void regularSeat_premiumTier_appliesMultiplier() {
        BigDecimal p = PricingCalculator.priceFor(SeatCategory.REGULAR,
                new BigDecimal("100.00"), new BigDecimal("250.00"), PricingTier.PREMIUM);
        // 100 * 1.15 = 115.00
        assertEquals(new BigDecimal("115.00"), p);
    }
}
