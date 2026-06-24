package com.booking.service;

import com.booking.entity.PricingTier;
import com.booking.entity.SeatCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PricingCalculator {

    private PricingCalculator() {}

    private static final BigDecimal WEEKEND_MULTIPLIER = new BigDecimal("1.25");
    private static final BigDecimal PREMIUM_TIER_MULTIPLIER = new BigDecimal("1.15");

    /**
     * Price for a seat in a show.
     * - Regular seats: basePriceRegular; Premium seats: basePricePremium.
     * - WEEKEND tier multiplies by 1.25; PREMIUM tier multiplies by 1.15; REGULAR tier = 1x.
     */
    public static BigDecimal priceFor(SeatCategory cat, BigDecimal basePriceRegular,
                                      BigDecimal basePricePremium, PricingTier tier) {
        BigDecimal base = (cat == SeatCategory.PREMIUM) ? basePricePremium : basePriceRegular;
        BigDecimal mult = switch (tier) {
            case WEEKEND -> WEEKEND_MULTIPLIER;
            case PREMIUM -> PREMIUM_TIER_MULTIPLIER;
            case REGULAR -> BigDecimal.ONE;
        };
        return base.multiply(mult).setScale(2, RoundingMode.HALF_UP);
    }
}
