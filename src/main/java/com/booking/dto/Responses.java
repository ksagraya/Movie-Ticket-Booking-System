package com.booking.dto;

import com.booking.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Responses {

    public record UserResponse(Long id, String username, String email, Role role) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole());
        }
    }

    public record CityResponse(Long id, String name) {
        public static CityResponse from(City c) { return new CityResponse(c.getId(), c.getName()); }
    }

    public record TheaterResponse(Long id, String name, String address, Long cityId, String cityName) {
        public static TheaterResponse from(Theater t) {
            return new TheaterResponse(t.getId(), t.getName(), t.getAddress(),
                    t.getCity().getId(), t.getCity().getName());
        }
    }

    public record SeatResponse(Long id, String rowLabel, Integer seatNumber, SeatCategory category) {
        public static SeatResponse from(Seat s) {
            return new SeatResponse(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getCategory());
        }
    }

    public record ScreenResponse(Long id, String name, Long theaterId, List<SeatResponse> seats) {
        public static ScreenResponse from(Screen s, List<Seat> seats) {
            return new ScreenResponse(s.getId(), s.getName(), s.getTheater().getId(),
                    seats.stream().map(SeatResponse::from).collect(Collectors.toList()));
        }
    }

    public record MovieResponse(Long id, String title, Integer durationMinutes, String language, String genre) {
        public static MovieResponse from(Movie m) {
            return new MovieResponse(m.getId(), m.getTitle(), m.getDurationMinutes(), m.getLanguage(), m.getGenre());
        }
    }

    public record ShowResponse(Long id, MovieResponse movie, Long screenId, String screenName,
                                Long theaterId, String theaterName, Long cityId, String cityName,
                                LocalDateTime startTime, BigDecimal basePriceRegular,
                                BigDecimal basePricePremium, PricingTier pricingTier) {
        public static ShowResponse from(Show s) {
            Screen sc = s.getScreen();
            Theater t = sc.getTheater();
            return new ShowResponse(s.getId(), MovieResponse.from(s.getMovie()),
                    sc.getId(), sc.getName(),
                    t.getId(), t.getName(),
                    t.getCity().getId(), t.getCity().getName(),
                    s.getStartTime(), s.getBasePriceRegular(), s.getBasePricePremium(), s.getPricingTier());
        }
    }

    public record ShowSeatResponse(Long id, Long seatId, String rowLabel, Integer seatNumber,
                                    SeatCategory category, ShowSeatStatus status,
                                    BigDecimal price, LocalDateTime holdExpiresAt) {
        public static ShowSeatResponse from(ShowSeat ss) {
            Seat s = ss.getSeat();
            return new ShowSeatResponse(ss.getId(), s.getId(), s.getRowLabel(), s.getSeatNumber(),
                    s.getCategory(), ss.getStatus(), ss.getPrice(), ss.getHoldExpiresAt());
        }
    }

    public record BookingResponse(Long id, Long userId, Long showId, BookingStatus status,
                                   List<ShowSeatResponse> seats, BigDecimal subtotal,
                                   BigDecimal discountAmount, String discountCode,
                                   BigDecimal totalAmount, LocalDateTime createdAt,
                                   LocalDateTime confirmedAt, LocalDateTime cancelledAt,
                                   BigDecimal refundAmount) {
        public static BookingResponse from(Booking b) {
            Set<ShowSeat> seatSet = b.getSeats();
            List<ShowSeatResponse> seats = seatSet == null ? List.of() :
                    seatSet.stream().map(ShowSeatResponse::from).collect(Collectors.toList());
            return new BookingResponse(b.getId(), b.getUser().getId(), b.getShow().getId(),
                    b.getStatus(), seats, b.getSubtotal(), b.getDiscountAmount(),
                    b.getDiscountCode(), b.getTotalAmount(), b.getCreatedAt(),
                    b.getConfirmedAt(), b.getCancelledAt(), b.getRefundAmount());
        }
    }

    public record PaymentResponse(Long id, Long bookingId, BigDecimal amount, PaymentStatus status,
                                   String reference, LocalDateTime createdAt,
                                   LocalDateTime refundedAt, BigDecimal refundedAmount) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getBooking().getId(), p.getAmount(), p.getStatus(),
                    p.getReference(), p.getCreatedAt(), p.getRefundedAt(), p.getRefundedAmount());
        }
    }
}
