package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/venues/{venueId}/bookings")
@RequiredArgsConstructor
public class VenueBookingController {
    private final BookingService bookingService;
    private final VenueService venueService;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<Page<Booking>> getVenueBookings(
            @PathVariable Long venueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        access.requireOwner(venueService.getVenueById(venueId));
        return ResponseEntity.ok(bookingService.getBookingsByVenueId(venueId, page, size));
    }
}
