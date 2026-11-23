package group4.backend.soa.service.impl;

import group4.backend.soa.service.BookingService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.CourtRepository;
import group4.backend.soa.repository.UserRepository;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.CourtSchedule;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final CourtRepository courts;
    private final UserRepository users;
    private final VenueRepository venues;
    private final CourtSchedule schedule;
    private final EntityManager entities;

    @Override
    public Page<Booking> getBookingsByUserId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByUserId(id, pageable);
        if (bookings.isEmpty()) {
            throw AccessRules.notFound("Booking");
        }
        return bookings;
    }

    @Override 
    public Page<Booking> getBookingsByVenueId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByCourtVenueId(id, pageable);
        if (bookings.isEmpty()) {
            throw AccessRules.notFound("Booking");
        }
        return bookings;
    }

    @Override
    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking"));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(Booking booking) {
        var start = booking.getStartTime();
        var end = booking.getEndTime();
        var courtId = booking.getCourt().getId();
        CourtSchedule.validateInterval(start, end);

        if (booking.getRequestKey() != null) {
            var userId = booking.getUser().getId();
            booking.setUser(users.lockById(userId).orElseThrow(() -> AccessRules.notFound("User")));
            var previous = bookingRepository.findByUserIdAndRequestKey(userId, booking.getRequestKey());
            if (previous.isPresent()) {
                var existing = previous.get();
                if (!existing.getCourt().getId().equals(courtId) || !existing.getStartTime().equals(start)
                        || !existing.getEndTime().equals(end)) {
                    throw conflict("Idempotency-Key is already used for another booking");
                }
                return existing;
            }
        }

        // Serialize the availability check and insert for each physical court.
        var court = courts.lockById(courtId).orElseThrow(() -> AccessRules.notFound("Court"));
        if (!CourtSchedule.active(court)) {
            throw AccessRules.notFound("Court");
        }
        var now = schedule.now();
        if (!start.isAfter(now) || !CourtSchedule.hours(court, start.toLocalDate()).contains(start, end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must cover future slots within opening hours");
        }
        if (!CourtSchedule.canBook(court, start, end, now)) {
            throw conflict("Court has no configured price");
        }
        if (bookingRepository.existsOccupied(courtId, start, end, now)) {
            throw conflict("Booking time overlaps with an existing booking");
        }
        var holdDeadline = now.plusMinutes(30);
        booking.setCourt(court);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(start.isBefore(holdDeadline) ? start : holdDeadline);
        booking.setTotalPrice(CourtSchedule.price(court, start, end));
        return bookingRepository.save(booking);
    }

    @Override
    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking"));
        bookingRepository.delete(booking);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirmBooking(Long id) {
        // A hold may expire while confirmation waits. Recheck it under the same court lock as creation.
        Long courtId = bookingRepository.findCourtId(id).orElseThrow(() -> AccessRules.notFound("Booking"));
        courts.lockById(courtId).orElseThrow(() -> AccessRules.notFound("Court"));
        Booking booking = lockBooking(id);
        // Confirmation can extend inventory reservations past the hold deadline.
        venues.lockById(booking.getCourt().getVenue().getId()).orElseThrow(() -> AccessRules.notFound("Venue"));
        var now = schedule.now();
        if (booking.getStatus() != BookingStatus.PENDING || !booking.getStartTime().isAfter(now)
                || (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(now))) {
            throw conflict("Only an unexpired booking hold can be confirmed");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeBooking(Long id) {
        Booking booking = lockBooking(id);
        if (booking.getCheckedOutAt() != null && booking.getStatus() == BookingStatus.COMPLETED) {
            return;
        }
        if (booking.getCheckedInAt() != null || bookingRepository.hasServiceOrders(id)) {
            throw conflict("Use check-out to complete a booking with services or a check-in");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED || booking.getEndTime().isAfter(schedule.now())) {
            throw conflict("Only confirmed bookings whose time has ended can be completed");
        }
        booking.setStatus(BookingStatus.COMPLETED);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelBooking(Long id) {
        Booking booking = lockBooking(id);
        if (booking.getCheckedInAt() != null) {
            throw conflict("Checked-in bookings must use check-out");
        }
        switch (booking.getStatus()) {
            case PENDING:
            case CONFIRMED:
                booking.setStatus(BookingStatus.CANCELLED);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending or confirmed bookings can be cancelled");
        }
    }

    private Booking lockBooking(Long id) {
        Booking booking = bookingRepository.lockById(id).orElseThrow(() -> AccessRules.notFound("Booking"));
        // The controller can have loaded this entity before the transaction for access checks.
        entities.refresh(booking);
        return booking;
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
