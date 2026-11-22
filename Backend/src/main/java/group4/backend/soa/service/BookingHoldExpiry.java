package group4.backend.soa.service;

import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnWebApplication
@RequiredArgsConstructor
public class BookingHoldExpiry {
    private final BookingRepository bookings;
    private final CourtSchedule schedule;

    @Scheduled(fixedDelayString = "${app.booking.expiry-interval-ms:60000}",
            initialDelayString = "${app.booking.expiry-interval-ms:60000}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireHolds() {
        bookings.expireHolds(BookingStatus.PENDING, BookingStatus.EXPIRED, schedule.now());
    }
}
