package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.PaymentMethod;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.PaymentService;
import group4.backend.soa.entity.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;


@RestController
@RequestMapping("/bookings/{bookingId}/payments")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<Payment>> getPaymentsByBookingId(@PathVariable Long bookingId) {
        access.requireBookingAccess(bookingService.getBookingById(bookingId));
        List<Payment> payments = paymentService.getPaymentsByBookingId(bookingId);
        return ResponseEntity.ok(payments);
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@PathVariable Long bookingId, @Valid @RequestBody PaymentRequest input) {
        var booking = bookingService.getBookingById(bookingId);
        access.requireCustomerBooking(booking);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed bookings can be paid");
        }
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(input.method().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(method);
        Payment createdPayment = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPayment);
    }

    public record PaymentRequest(@NotBlank @Size(max = 32) String method) {}
}
