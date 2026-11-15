package group4.backend.soa.controller;

import group4.backend.soa.service.ReviewService;
import group4.backend.soa.entity.Review;
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

    @GetMapping
    public ResponseEntity<Page<Review>> getReviewsByVenueId(
            @PathVariable Long venueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Review> reviews = reviewService.getReviewsByVenueId(venueId, page, size);
        return ResponseEntity.ok(reviews);
    }
    //Chưa hoàn thành
    @PostMapping
    public ResponseEntity<Review> createReview(@RequestBody Review review) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(review));
    }
}
