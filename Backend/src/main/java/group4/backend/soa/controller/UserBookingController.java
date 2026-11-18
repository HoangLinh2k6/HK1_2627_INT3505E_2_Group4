package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/bookings")
@RequiredArgsConstructor
public class UserBookingController {
    private final BookingRepository bookingRepository;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<Page<Booking>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingRepository.findByUserId(access.userId(), PageRequest.of(page, size)));
    }
}
