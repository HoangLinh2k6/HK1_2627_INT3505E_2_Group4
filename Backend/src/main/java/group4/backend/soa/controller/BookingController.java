package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.CourtSchedule;
import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    private final UserService userService;
    private final AccessRules access;

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id);
        access.requireBookingAccess(booking);
        return ResponseEntity.ok(booking);
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody Create input,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestKey) {
        if (input.court() == null || input.court().id() == null || input.court().id() <= 0
                || input.startTime() == null || input.endTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court and times are required");
        }
        if (requestKey != null && !requestKey.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }
        var booking = new Booking();
        var court = new Court();
        court.setId(input.court().id());
        booking.setStartTime(CourtSchedule.parseTime(input.startTime()));
        booking.setEndTime(CourtSchedule.parseTime(input.endTime()));
        booking.setUser(userService.getUserById(access.userId()));
        booking.setCourt(court);
        booking.setRequestKey(requestKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(booking));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Create(CourtReference court, String startTime, String endTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourtReference(Long id) {}

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
