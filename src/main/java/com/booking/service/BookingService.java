package com.booking.service;

import com.booking.entity.*;
import com.booking.exception.ApiException;
import com.booking.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BookingService {

    /** Default seat hold window if {@code booking.hold.duration-seconds} is not configured. */
    static final long DEFAULT_HOLD_DURATION_SECONDS = 300L;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @Value("${booking.hold.duration-seconds:#{T(com.booking.service.BookingService).DEFAULT_HOLD_DURATION_SECONDS}}")
    private long holdDurationSeconds;

    public BookingService(ShowSeatRepository showSeatRepository,
                          BookingRepository bookingRepository,
                          DiscountCodeRepository discountCodeRepository,
                          RefundPolicyRepository refundPolicyRepository,
                          PaymentService paymentService,
                          NotificationService notificationService) {
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.refundPolicyRepository = refundPolicyRepository;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    // ----------------------------- HOLD -----------------------------

    /**
     * Place HOLD on selected seats. Uses pessimistic locking to serialize concurrent attempts.
     * If any seat is already HELD (non-expired) or BOOKED, the entire request fails atomically.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking holdSeats(User user, Long showId, List<Long> showSeatIds) {
        List<ShowSeat> seats = lockSeatsInOrder(showSeatIds);
        LocalDateTime now = LocalDateTime.now();
        Show show = validateSeatsAvailableForShow(seats, showId, now);
        BigDecimal subtotal = sumSeatPrices(seats);
        applyHold(seats, user, now);
        return persistPendingBooking(user, show, seats, subtotal, now);
    }

    private List<ShowSeat> lockSeatsInOrder(List<Long> showSeatIds) {
        if (showSeatIds == null || showSeatIds.isEmpty()) {
            throw new ApiException("No seats requested", 400);
        }
        // Sort IDs to avoid deadlocks under concurrent locking
        List<Long> sortedIds = new ArrayList<>(new HashSet<>(showSeatIds));
        Collections.sort(sortedIds);
        List<ShowSeat> seats = showSeatRepository.findAllByIdsForUpdate(sortedIds);
        if (seats.size() != sortedIds.size()) {
            throw new ApiException("One or more seats not found", 404);
        }
        return seats;
    }

    private Show validateSeatsAvailableForShow(List<ShowSeat> seats, Long showId, LocalDateTime now) {
        Show show = null;
        for (ShowSeat ss : seats) {
            if (!Objects.equals(ss.getShow().getId(), showId)) {
                throw new ApiException("Seat " + ss.getId() + " does not belong to show " + showId, 400);
            }
            show = ss.getShow();
            assertSeatBookable(ss, now);
        }
        if (show.getStartTime().isBefore(now)) {
            throw new ApiException("Cannot book past shows", 400);
        }
        return show;
    }

    private void assertSeatBookable(ShowSeat ss, LocalDateTime now) {
        if (ss.getStatus() == ShowSeatStatus.BOOKED) {
            throw new ApiException("Seat " + ss.getId() + " is already booked", 409);
        }
        boolean hasActiveHold = ss.getStatus() == ShowSeatStatus.HELD
                && ss.getHoldExpiresAt() != null
                && ss.getHoldExpiresAt().isAfter(now);
        if (hasActiveHold) {
            throw new ApiException("Seat " + ss.getId() + " is currently on hold", 409);
        }
    }

    private BigDecimal sumSeatPrices(List<ShowSeat> seats) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShowSeat ss : seats) total = total.add(ss.getPrice());
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void applyHold(List<ShowSeat> seats, User user, LocalDateTime now) {
        LocalDateTime expiry = now.plusSeconds(holdDurationSeconds);
        for (ShowSeat ss : seats) {
            ss.setStatus(ShowSeatStatus.HELD);
            ss.setHoldExpiresAt(expiry);
            ss.setHeldByUserId(user.getId());
        }
        showSeatRepository.saveAll(seats);
    }

    private Booking persistPendingBooking(User user, Show show, List<ShowSeat> seats,
                                          BigDecimal subtotal, LocalDateTime now) {
        Booking booking = Booking.builder()
                .user(user).show(show).status(BookingStatus.PENDING)
                .seats(new HashSet<>(seats))
                .subtotal(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(subtotal)
                .createdAt(now).build();
        return bookingRepository.save(booking);
    }

    // ----------------------------- DISCOUNT -----------------------------

    @Transactional
    public Booking applyDiscount(User user, Long bookingId, String code) {
        Booking booking = loadOwnedBooking(user, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ApiException("Discount can only be applied to PENDING bookings", 400);
        }
        DiscountCode dc = discountCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException("Invalid discount code", 404));
        assertDiscountUsable(dc);

        BigDecimal discount = computeDiscount(booking.getSubtotal(), dc);
        BigDecimal total = booking.getSubtotal().subtract(discount).max(BigDecimal.ZERO);

        booking.setDiscountCode(dc.getCode());
        booking.setDiscountAmount(discount);
        booking.setTotalAmount(total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return bookingRepository.save(booking);
    }

    private void assertDiscountUsable(DiscountCode dc) {
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.FALSE.equals(dc.getActive())) {
            throw new ApiException("Discount code is inactive", 400);
        }
        if (now.isBefore(dc.getValidFrom()) || now.isAfter(dc.getValidTo())) {
            throw new ApiException("Discount code is not currently valid", 400);
        }
        if (dc.getMaxUses() != null && dc.getUsedCount() >= dc.getMaxUses()) {
            throw new ApiException("Discount code usage limit reached", 400);
        }
    }

    private BigDecimal computeDiscount(BigDecimal subtotal, DiscountCode dc) {
        BigDecimal discount = subtotal.multiply(dc.getPercentage())
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        if (dc.getMaxDiscount() != null && discount.compareTo(dc.getMaxDiscount()) > 0) {
            discount = dc.getMaxDiscount();
        }
        return discount;
    }

    // ----------------------------- PAY -----------------------------

    @Transactional
    public Booking confirmAndPay(User user, Long bookingId) {
        Booking booking = loadOwnedBooking(user, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ApiException("Booking is not pending", 400);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ShowSeat> seats = relockOwnedHeldSeats(booking, user, now);

        paymentService.charge(booking, booking.getTotalAmount());
        markSeatsBooked(seats);
        incrementDiscountUsageIfAny(booking);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);
        Booking saved = bookingRepository.save(booking);

        notificationService.sendBookingConfirmation(saved);
        return saved;
    }

    private List<ShowSeat> relockOwnedHeldSeats(Booking booking, User user, LocalDateTime now) {
        List<Long> ids = booking.getSeats().stream().map(ShowSeat::getId).sorted().toList();
        List<ShowSeat> seats = showSeatRepository.findAllByIdsForUpdate(ids);
        for (ShowSeat ss : seats) {
            boolean stillHeldByUser = ss.getStatus() == ShowSeatStatus.HELD
                    && Objects.equals(ss.getHeldByUserId(), user.getId())
                    && ss.getHoldExpiresAt() != null
                    && !ss.getHoldExpiresAt().isBefore(now);
            if (!stillHeldByUser) {
                throw new ApiException("Seat hold expired or invalid for seat " + ss.getId(), 409);
            }
        }
        return seats;
    }

    private void markSeatsBooked(List<ShowSeat> seats) {
        for (ShowSeat ss : seats) {
            ss.setStatus(ShowSeatStatus.BOOKED);
            ss.setHoldExpiresAt(null);
        }
        showSeatRepository.saveAll(seats);
    }

    private void incrementDiscountUsageIfAny(Booking booking) {
        if (booking.getDiscountCode() == null) return;
        discountCodeRepository.findByCodeIgnoreCase(booking.getDiscountCode()).ifPresent(dc -> {
            dc.setUsedCount(dc.getUsedCount() + 1);
            discountCodeRepository.save(dc);
        });
    }

    // ----------------------------- CANCEL -----------------------------

    @Transactional
    public Booking cancel(User user, Long bookingId) {
        Booking booking = loadOwnedBooking(user, bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException("Already cancelled", 400);
        }
        LocalDateTime now = LocalDateTime.now();

        BigDecimal refund = BigDecimal.ZERO;
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            refund = processRefund(booking, now);
        }
        releaseSeats(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setRefundAmount(refund);
        Booking saved = bookingRepository.save(booking);

        notificationService.sendCancellationNotice(saved);
        return saved;
    }

    private BigDecimal processRefund(Booking booking, LocalDateTime now) {
        if (booking.getShow().getStartTime().isBefore(now)) {
            throw new ApiException("Cannot cancel after show start", 400);
        }
        long hoursToShow = Duration.between(now, booking.getShow().getStartTime()).toHours();
        BigDecimal pct = pickRefundPercentage(hoursToShow);
        BigDecimal refund = booking.getTotalAmount().multiply(pct)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.refund(booking, refund);
        }
        return refund;
    }

    /** Picks the most generous active refund policy tier whose hours-threshold is satisfied. */
    private BigDecimal pickRefundPercentage(long hoursToShow) {
        for (RefundPolicy p : refundPolicyRepository.findByActiveTrueOrderByHoursBeforeShowDesc()) {
            if (hoursToShow >= p.getHoursBeforeShow()) return p.getRefundPercentage();
        }
        return BigDecimal.ZERO;
    }

    private void releaseSeats(Booking booking) {
        for (ShowSeat ss : booking.getSeats()) {
            ss.setStatus(ShowSeatStatus.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setHeldByUserId(null);
        }
        showSeatRepository.saveAll(booking.getSeats());
    }

    // ----------------------------- READS -----------------------------

    public List<Booking> myBookings(User user) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public Booking getBooking(User user, Long bookingId) {
        return loadOwnedBooking(user, bookingId);
    }

    private Booking loadOwnedBooking(User user, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("Booking not found", 404));
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isAdmin && !Objects.equals(b.getUser().getId(), user.getId())) {
            throw new ApiException("Access denied", 403);
        }
        return b;
    }
}
