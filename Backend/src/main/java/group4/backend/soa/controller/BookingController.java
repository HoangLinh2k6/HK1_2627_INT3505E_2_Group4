package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.CourtSchedule;
import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
        var errors = new ArrayList<FieldError>();
        if (input.court() == null || input.court().id() == null || input.court().id() <= 0) {
            errors.add(FieldError.of("court.id", "INVALID_COURT_ID",
                    "court.id is required and must be a positive integer.",
                    input.court() == null ? null : input.court().id()));
        }
        if (input.startTime() == null || input.startTime().isBlank()) {
            errors.add(FieldError.of("startTime", "REQUIRED",
                    "startTime is required (ISO-8601 date-time, e.g. 2030-01-02T17:00:00+07:00)."));
        }
        if (input.endTime() == null || input.endTime().isBlank()) {
            errors.add(FieldError.of("endTime", "REQUIRED",
                    "endTime is required (ISO-8601 date-time, e.g. 2030-01-02T18:00:00+07:00)."));
        }
        if (requestKey != null && !requestKey.matches("[A-Za-z0-9._:-]{1,64}")) {
            errors.add(FieldError.of("Idempotency-Key", "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1 to 64 characters matching [A-Za-z0-9._:-].", requestKey));
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    errors.size() == 1 ? errors.getFirst().message()
                            : "Booking request has " + errors.size() + " invalid or missing field(s).",
                    errors);
        }
        var booking = new Booking();
        var court = new Court();
        court.setId(input.court().id());
        booking.setStartTime(CourtSchedule.parseTime("startTime", input.startTime()));
        booking.setEndTime(CourtSchedule.parseTime("endTime", input.endTime()));
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
