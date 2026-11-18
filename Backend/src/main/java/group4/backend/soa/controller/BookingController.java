package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.VenueStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;
    private final CourtService courtService;
    private final UserService userService;
    private final AccessRules access;
    private static final ZoneId VENUE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id);
        access.requireBookingAccess(booking);
        return ResponseEntity.ok(booking);
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
        if (booking.getCourt() == null || booking.getCourt().getId() == null
                || booking.getStartTime() == null || booking.getEndTime() == null
                || booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court, times and non-negative price are required");
        }
        var court = courtService.getCourtById(booking.getCourt().getId());
        if (court.getStatus() != CourtStatus.AVAILABLE || court.getVenue().getStatus() != VenueStatus.ACTIVE) {
            throw AccessRules.notFound("Court");
        }
        LocalDateTime now = LocalDateTime.now(VENUE_ZONE);
        if (!booking.getStartTime().isAfter(now) || !booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking interval");
        }
        LocalDateTime expiresAt = now.plusMinutes(30);
        booking.setId(null);
        booking.setUser(userService.getUserById(access.userId()));
        booking.setCourt(court);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(booking.getStartTime().isBefore(expiresAt) ? booking.getStartTime() : expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(booking));
    }

    @RequestMapping(path = "/{id}/confirm", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Void> confirmBooking(@PathVariable Long id) {
        access.requireBookingOwner(bookingService.getBookingById(id));
        bookingService.confirmBooking(id);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(path = "/{id}/complete", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Void> completeBooking(@PathVariable Long id) {
        access.requireBookingOwner(bookingService.getBookingById(id));
        bookingService.completeBooking(id);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(path = "/{id}/cancel", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        access.requireBookingAccess(bookingService.getBookingById(id));
        bookingService.cancelBooking(id);
        return ResponseEntity.ok().build();
    }

}
