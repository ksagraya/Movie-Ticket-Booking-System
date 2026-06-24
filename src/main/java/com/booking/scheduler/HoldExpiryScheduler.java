package com.booking.scheduler;

import com.booking.entity.ShowSeat;
import com.booking.entity.ShowSeatStatus;
import com.booking.repository.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);
    private final ShowSeatRepository showSeatRepository;

    public HoldExpiryScheduler(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedDelayString = "${booking.hold.release-interval-ms:30000}")
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<ShowSeat> expired = showSeatRepository.findExpiredHolds(ShowSeatStatus.HELD, now);
        if (expired.isEmpty()) return;
        for (ShowSeat ss : expired) {
            ss.setStatus(ShowSeatStatus.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setHeldByUserId(null);
        }
        showSeatRepository.saveAll(expired);
        log.info("Released {} expired seat holds", expired.size());
    }
}
