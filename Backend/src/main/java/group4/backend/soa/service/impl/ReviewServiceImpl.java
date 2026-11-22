package group4.backend.soa.service.impl;

import group4.backend.soa.entity.Review;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.repository.ReviewRepository;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.service.ReviewService;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final VenueRepository venueRepository;

    @Override
    public Page<Review> getReviewsByVenueId(Long venueId, int page, int size) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (!venueRepository.existsById(venueId)) {
            throw AccessRules.notFound("Venue");
        }
        return reviewRepository.findVerifiedByVenue(venueId, BookingStatus.COMPLETED, pageable);
    }

    @Override
    public Review getReviewById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Review"));
    }

    @Override
    public Review createReview(Review review) {
        return reviewRepository.save(review);
    }

    @Override
    public void updateReview(Long reviewId, Review review) {
        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> AccessRules.notFound("Review"));
        existingReview.setRating(review.getRating());
        existingReview.setComment(review.getComment());
        reviewRepository.save(existingReview);
    }

    @Override
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> AccessRules.notFound("Review"));
        reviewRepository.delete(review);
    }
}
