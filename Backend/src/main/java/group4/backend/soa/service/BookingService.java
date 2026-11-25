package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.CourtRepository;
import group4.backend.soa.repository.UserRepository;
import group4.backend.soa.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final CourtRepository courts;
    private final UserRepository users;
    private final VenueRepository venues;
    private final CourtSchedule schedule;
    private final EntityManager entities;

    public Page<Booking> getBookingsByUserId(Long id, int page, int size) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByUserId(id, pageable);
        if (bookings.isEmpty()) {
            throw ApiException.notFound("BOOKINGS_NOT_FOUND",
                    "No bookings found for the current user on page " + page + ".");
        }
        return bookings;
    }

    public Page<Booking> getBookingsByVenueId(Long id, int page, int size) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByCourtVenueId(id, pageable);
        if (bookings.isEmpty()) {
            throw ApiException.notFound("BOOKINGS_NOT_FOUND",
                    "No bookings found for venue #" + id + " on page " + page + ".");
        }
        return bookings;
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking", id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(Booking booking) {
        var start = booking.getStartTime();
        var end = booking.getEndTime();
        var courtId = booking.getCourt().getId();
        CourtSchedule.validateInterval(start, end);

        if (booking.getRequestKey() != null) {
            var userId = booking.getUser().getId();
            booking.setUser(users.lockById(userId).orElseThrow(() -> AccessRules.notFound("User", userId)));
            var previous = bookingRepository.findByUserIdAndRequestKey(userId, booking.getRequestKey());
            if (previous.isPresent()) {
                var existing = previous.get();
                if (!existing.getCourt().getId().equals(courtId) || !existing.getStartTime().equals(start)
                        || !existing.getEndTime().equals(end)) {
                    throw conflict("IDEMPOTENCY_KEY_CONFLICT",
                            "Idempotency-Key '" + booking.getRequestKey()
                                    + "' was already used for another booking (court or time interval). Use a new key for a different booking.");
                }
                return existing;
            }
        }

        // Serialize the availability check and insert for each physical court.
        var court = courts.lockById(courtId).orElseThrow(() -> AccessRules.notFound("Court", courtId));
        if (!CourtSchedule.active(court)) {
            throw AccessRules.notFound("Court", courtId);
        }
        var now = schedule.now();
        if (!start.isAfter(now)) {
            String message = "Booking startTime (" + start + ") must be in the future (current time in "
                    + CourtSchedule.ZONE + ": " + now + ").";
            throw ApiException.badRequest("BOOKING_IN_PAST", message,
                    FieldError.of("startTime", "BOOKING_IN_PAST", message, start.toString()));
        }
        var hours = CourtSchedule.hours(court, start.toLocalDate());
        if (!hours.contains(start, end)) {
            String message = "Booking interval (" + start.toLocalTime() + " - " + end.toLocalTime()
                    + ") must fall within venue opening hours (" + hours.open().toLocalTime() + " - "
                    + hours.close().toLocalTime() + ") and align to 30-minute slots from opening time.";
            throw ApiException.badRequest("OUTSIDE_OPENING_HOURS", message,
                    FieldError.of("startTime", "OUTSIDE_OPENING_HOURS", message, start.toString()));
        }
        if (!CourtSchedule.canBook(court, start, end, now)) {
            throw conflict("COURT_PRICE_NOT_CONFIGURED",
                    "Court #" + courtId + " cannot be booked because it has no basePricePerHour configured.");
        }
        if (bookingRepository.existsOccupied(courtId, start, end, now)) {
            throw conflict("BOOKING_TIME_OVERLAP",
                    "The requested time interval (" + start + " to " + end + ") on court #" + courtId
                            + " overlaps with an existing active booking.");
        }
        var holdDeadline = now.plusMinutes(30);
        booking.setCourt(court);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(start.isBefore(holdDeadline) ? start : holdDeadline);
        booking.setTotalPrice(CourtSchedule.price(court, start, end));
        return bookingRepository.save(booking);
    }

    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking", id));
        bookingRepository.delete(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirmBooking(Long id) {
        // A hold may expire while confirmation waits. Recheck it under the same court lock as creation.
        Long courtId = bookingRepository.findCourtId(id).orElseThrow(() -> AccessRules.notFound("Booking", id));
        courts.lockById(courtId).orElseThrow(() -> AccessRules.notFound("Court", courtId));
        Booking booking = lockBooking(id);
        // Confirmation can extend inventory reservations past the hold deadline.
        Long venueId = booking.getCourt().getVenue().getId();
        venues.lockById(venueId).orElseThrow(() -> AccessRules.notFound("Venue", venueId));
        var now = schedule.now();
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw conflict("INVALID_BOOKING_STATUS",
                    "Booking #" + id + " cannot be confirmed because its current status is "
                            + booking.getStatus() + " (expected PENDING).");
        }
        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(now)) {
            throw conflict("BOOKING_HOLD_EXPIRED",
                    "Booking #" + id + " hold expired at " + booking.getExpiresAt() + " and can no longer be confirmed.");
        }
        if (!booking.getStartTime().isAfter(now)) {
            throw conflict("BOOKING_ALREADY_STARTED",
                    "Booking #" + id + " cannot be confirmed because its start time (" + booking.getStartTime()
                            + ") has already passed.");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeBooking(Long id) {
        Booking booking = lockBooking(id);
        if (booking.getCheckedOutAt() != null && booking.getStatus() == BookingStatus.COMPLETED) {
            return;
        }
        if (booking.getCheckedInAt() != null || bookingRepository.hasServiceOrders(id)) {
            throw conflict("CHECKOUT_REQUIRED",
                    "Booking #" + id + " has an active check-in or service orders and must be completed via POST /bookings/"
                            + id + "/check-out.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw conflict("INVALID_BOOKING_STATUS",
                    "Booking #" + id + " cannot be completed because its current status is "
                            + booking.getStatus() + " (expected CONFIRMED).");
        }
        if (booking.getEndTime().isAfter(schedule.now())) {
            throw conflict("BOOKING_NOT_ENDED",
                    "Booking #" + id + " cannot be completed before its scheduled end time (" + booking.getEndTime() + ").");
        }
        booking.setStatus(BookingStatus.COMPLETED);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelBooking(Long id) {
        Booking booking = lockBooking(id);
        if (booking.getCheckedInAt() != null) {
            throw conflict("BOOKING_ALREADY_CHECKED_IN",
                    "Booking #" + id + " is already checked in and cannot be cancelled. Use POST /bookings/"
                            + id + "/check-out instead.");
        }
        switch (booking.getStatus()) {
            case PENDING:
            case CONFIRMED:
                booking.setStatus(BookingStatus.CANCELLED);
                break;
            default:
                throw ApiException.badRequest("INVALID_BOOKING_STATUS",
                        "Booking #" + id + " cannot be cancelled because its current status is "
                                + booking.getStatus() + " (only PENDING or CONFIRMED bookings can be cancelled).");
        }
    }

    private Booking lockBooking(Long id) {
        Booking booking = bookingRepository.lockById(id).orElseThrow(() -> AccessRules.notFound("Booking", id));
        // The controller can have loaded this entity before the transaction for access checks.
        entities.refresh(booking);
        return booking;
    }

    private static void validatePagination(int page, int size) {
        var errors = new ArrayList<FieldError>();
        if (page < 0) {
            errors.add(FieldError.of("page", "Min", "page must be greater than or equal to 0.", page));
        }
        if (size < 1 || size > 100) {
            errors.add(FieldError.of("size", "Range", "size must be between 1 and 100.", size));
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    errors.size() == 1 ? errors.getFirst().message()
                            : "Invalid pagination parameters: page must be >= 0 and size must be between 1 and 100.",
                    errors);
        }
    }

    private static ResponseStatusException conflict(String code, String detail) {
        return ApiException.conflict(code, detail);
    }
}
