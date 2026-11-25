package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Review;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.repository.ReviewRepository;
import group4.backend.soa.repository.VenueRepository;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final VenueRepository venueRepository;

    public Page<Review> getReviewsByVenueId(Long venueId, int page, int size) {
        var errors = new ArrayList<FieldError>();
        if (page < 0) {
            errors.add(FieldError.of("page", "Min", "page must be greater than or equal to 0.", page));
        }
        if (size < 1 || size > 100) {
            errors.add(FieldError.of("size", "Range", "size must be between 1 and 100.", size));
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    errors.size() == 1 ? errors.getFirst().message()
                            : "Invalid pagination parameters: page must be >= 0 and size must be between 1 and 100.",
                    errors);
        }
        Pageable pageable = PageRequest.of(page, size);
        if (!venueRepository.existsById(venueId)) {
            throw AccessRules.notFound("Venue", venueId);
        }
        return reviewRepository.findVerifiedByVenue(venueId, BookingStatus.COMPLETED, pageable);
    }

    public Review getReviewById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Review", id));
    }

    public Review createReview(Review review) {
        return reviewRepository.save(review);
    }

    public void updateReview(Long reviewId, Review review) {
        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> AccessRules.notFound("Review", reviewId));
        existingReview.setRating(review.getRating());
        existingReview.setComment(review.getComment());
        reviewRepository.save(existingReview);
    }

    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> AccessRules.notFound("Review", reviewId));
        reviewRepository.delete(review);
    }
}
