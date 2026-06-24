package com.booking.service;

import com.booking.entity.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async
    public void sendBookingConfirmation(Booking booking) {
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        log.info("[NOTIFICATION] Confirmation sent to {} for booking #{} (show #{}, amount {})",
                booking.getUser().getEmail(), booking.getId(),
                booking.getShow().getId(), booking.getTotalAmount());
    }

    @Async
    public void sendCancellationNotice(Booking booking) {
        log.info("[NOTIFICATION] Cancellation processed for booking #{} (refund {})",
                booking.getId(), booking.getRefundAmount());
    }

    @Async
    public void sendReminder(Booking booking) {
        log.info("[NOTIFICATION] Reminder sent to {} for booking #{} (show at {})",
                booking.getUser().getEmail(), booking.getId(), booking.getShow().getStartTime());
    }
}
