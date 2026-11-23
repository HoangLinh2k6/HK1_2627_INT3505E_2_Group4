package group4.backend.soa.controller;

import group4.backend.soa.service.AddonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static group4.backend.soa.dto.AddonModels.*;

@RestController
@RequestMapping("/bookings/{bookingId}")
@RequiredArgsConstructor
public class BookingAddonController {
    private final AddonService addons;

    @GetMapping("/services")
    BookingServices services(@PathVariable Long bookingId) {
        return addons.bookingServices(bookingId);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView reserve(@PathVariable Long bookingId, @RequestHeader("Idempotency-Key") String key,
                      @Valid @RequestBody OrderRequest input) {
        return addons.reserve(bookingId, key, input);
    }

    @DeleteMapping("/services/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable Long bookingId, @PathVariable Long orderId) {
        addons.cancelOrder(bookingId, orderId);
    }

    @PostMapping("/services/{orderId}/deliver")
    OrderView deliver(@PathVariable Long bookingId, @PathVariable Long orderId, @Valid @RequestBody DeliverRequest input) {
        return addons.deliver(bookingId, orderId, input);
    }

    @PostMapping("/check-in")
    BookingServices checkIn(@PathVariable Long bookingId) {
        return addons.checkIn(bookingId);
    }

    @PostMapping("/check-out")
    BookingServices checkOut(@PathVariable Long bookingId) {
        return addons.checkOut(bookingId);
    }
}
