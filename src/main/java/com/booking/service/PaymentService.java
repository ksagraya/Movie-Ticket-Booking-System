package com.booking.service;

import com.booking.entity.Booking;
import com.booking.entity.Payment;
import com.booking.entity.PaymentStatus;
import com.booking.exception.ApiException;
import com.booking.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Mock payment service. Treats payments as immediately successful unless amount is negative.
 * In a real system, this would call Stripe/Razorpay etc.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment charge(Booking booking, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("Invalid amount", 400);
        }
        Payment p = Payment.builder()
                .booking(booking)
                .amount(amount)
                .status(PaymentStatus.SUCCESS)
                .reference("PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT))
                .createdAt(LocalDateTime.now())
                .build();
        return paymentRepository.save(p);
    }

    @Transactional
    public Payment refund(Booking booking, BigDecimal refundAmount) {
        Payment p = paymentRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ApiException("Payment not found for booking", 404));
        p.setStatus(PaymentStatus.REFUNDED);
        p.setRefundedAmount(refundAmount);
        p.setRefundedAt(LocalDateTime.now());
        return paymentRepository.save(p);
    }
}
