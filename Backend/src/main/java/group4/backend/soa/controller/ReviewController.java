package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.repository.ReviewRepository;
import group4.backend.soa.service.ReviewService;
import group4.backend.soa.entity.Review;
import group4.backend.soa.enums.BookingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<View>> getAllReviews() {
        return ResponseEntity.ok(reviewRepository.findAll().stream()
                .filter(review -> access.canSee(review.getVenue()) && verified(review))
                .map(View::from).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<View> updateReview(@PathVariable Long id, @Valid @RequestBody Edit input) {
        requireAuthor(id);
        Review edit = new Review();
        edit.setRating(input.rating());
        edit.setComment(input.comment());
        reviewService.updateReview(id, edit);
        return ResponseEntity.ok(View.from(reviewService.getReviewById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        requireAuthor(id);
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAuthor(Long id) {
        Review review = reviewService.getReviewById(id);
        if (!access.userId().equals(review.getUser().getId())) {
            throw AccessRules.notFound("Review");
        }
    }

     static boolean verified(Review review) {
        var booking = review.getBooking();
        return booking.getStatus() == BookingStatus.COMPLETED
                && booking.getUser().getId().equals(review.getUser().getId())
                && booking.getCourt().getVenue().getId().equals(review.getVenue().getId());
    }

    public record Edit(@NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(min = 3, max = 1000) String comment) {}

    public record View(Long id, Reference venue, Reference booking, Author user, Integer rating, String comment,
                       LocalDateTime createdAt) {
        static View from(Review review) {
            return new View(review.getId(), new Reference(review.getVenue().getId()),
                    new Reference(review.getBooking().getId()),
                    new Author(review.getUser().getId(), review.getUser().getFullName()),
                    review.getRating(), review.getComment(), review.getCreatedAt());
        }
    }

    public record Reference(Long id) {}

    public record Author(Long id, String fullName) {}
}
