package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.controller.ReviewController.View;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.ReviewService;
import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/venues/{venueId}/reviews")
@RequiredArgsConstructor
public class VenueReviewController {
    private final ReviewService reviewService;
    private final BookingService bookingService;
    private final VenueService venueService;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<Page<View>> getReviewsByVenueId(
            @PathVariable Long venueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        access.requireVisible(venueService.getVenueById(venueId));
        return ResponseEntity.ok(reviewService.getReviewsByVenueId(venueId, page, size).map(View::from));
    }

    @PostMapping
    public ResponseEntity<View> createReview(@PathVariable Long venueId, @Valid @RequestBody Create input) {
        var booking = bookingService.getBookingById(input.bookingId());
        access.requireCustomerBooking(booking);
        var venue = booking.getCourt().getVenue();
        if (!venueId.equals(venue.getId())) {
            throw AccessRules.notFound("Booking", input.bookingId());
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw ApiException.conflict("BOOKING_NOT_COMPLETED",
                    "Booking #" + input.bookingId() + " cannot be reviewed because its current status is "
                            + booking.getStatus() + " (only COMPLETED bookings can be reviewed).");
        }
        Review review = new Review();
        review.setVenue(venue);
        review.setBooking(booking);
        review.setUser(booking.getUser());
        review.setRating(input.rating());
        review.setComment(input.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(View.from(reviewService.createReview(review)));
    }

    public record Create(
            @NotNull(message = "bookingId is required.")
            @Positive(message = "bookingId must be a positive integer.")
            Long bookingId,
            @NotNull(message = "Rating is required.")
            @Min(value = 1, message = "Rating must be between 1 and 5.")
            @Max(value = 5, message = "Rating must be between 1 and 5.")
            Integer rating,
            @NotBlank(message = "Comment is required.")
            @Size(min = 3, max = 1000, message = "Comment must be between 3 and 1000 characters.")
            String comment) {}
}
