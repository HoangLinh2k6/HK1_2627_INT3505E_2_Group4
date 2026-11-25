package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.PaymentMethod;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.PaymentService;
import group4.backend.soa.entity.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


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
            throw ApiException.conflict("INVALID_BOOKING_STATUS",
                    "Booking #" + bookingId + " cannot be paid because its current status is "
                            + booking.getStatus() + " (only CONFIRMED bookings can be paid).");
        }
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(input.method().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(PaymentMethod.values()).map(Enum::name).collect(Collectors.joining(", "));
            String message = "Invalid payment method '" + input.method() + "'. Allowed values: " + allowed + ".";
            throw ApiException.badRequest("INVALID_PAYMENT_METHOD", message,
                    FieldError.of("method", "INVALID_ENUM", message, input.method()));
        }
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(method);
        Payment createdPayment = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPayment);
    }

    public record PaymentRequest(
            @NotBlank(message = "Payment method is required (CASH, BANK_TRANSFER, MOMO, or VNPAY).")
            @Size(max = 32, message = "Payment method must not exceed 32 characters.")
            String method) {}
}
