package group4.backend.soa.service;

import group4.backend.soa.entity.Review;
import org.springframework.data.domain.Page;

public interface ReviewService {
    Page<Review> getReviewsByVenueId(Long venueId, int page, int size);
    Review createReview(Review review);
    void updateReview(Long reviewId, Review review);
    void deleteReview(Long reviewId);
}
