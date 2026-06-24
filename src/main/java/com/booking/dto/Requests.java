package com.booking.dto;

import com.booking.entity.PricingTier;
import com.booking.entity.Role;
import com.booking.entity.SeatCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Requests {

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank @Size(min = 6) String password,
            @NotBlank @Email String email,
            Role role
    ) {}

    public record CityRequest(@NotBlank String name) {}

    public record TheaterRequest(
            @NotBlank String name,
            @NotBlank String address,
            @NotNull Long cityId
    ) {}

    public record SeatLayoutRow(
            @NotBlank String rowLabel,
            @NotNull @Min(1) Integer seatsCount,
            @NotNull SeatCategory category
    ) {}

    public record ScreenRequest(
            @NotBlank String name,
            @NotNull Long theaterId,
            @NotEmpty List<SeatLayoutRow> seatLayout
    ) {}

    public record MovieRequest(
            @NotBlank String title,
            @NotNull @Min(1) Integer durationMinutes,
            String language,
            String genre
    ) {}

    public record ShowRequest(
            @NotNull Long movieId,
            @NotNull Long screenId,
            @NotNull LocalDateTime startTime,
            @NotNull @DecimalMin("0.0") BigDecimal basePriceRegular,
            @NotNull @DecimalMin("0.0") BigDecimal basePricePremium,
            @NotNull PricingTier pricingTier
    ) {}

    public record DiscountCodeRequest(
            @NotBlank String code,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percentage,
            BigDecimal maxDiscount,
            @NotNull LocalDateTime validFrom,
            @NotNull LocalDateTime validTo,
            Integer maxUses,
            Boolean active
    ) {}

    public record RefundPolicyRequest(
            @NotBlank String name,
            @NotNull @Min(0) Integer hoursBeforeShow,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal refundPercentage,
            Boolean active
    ) {}

    public record HoldSeatsRequest(
            @NotNull Long showId,
            @NotEmpty List<Long> showSeatIds
    ) {}

    public record ApplyDiscountRequest(@NotBlank String code) {}
}
