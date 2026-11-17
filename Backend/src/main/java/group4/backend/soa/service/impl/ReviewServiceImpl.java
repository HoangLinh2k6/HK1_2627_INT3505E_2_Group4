package group4.backend.soa.service.impl;

import group4.backend.soa.entity.Review;
import group4.backend.soa.repository.ReviewRepository;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return reviewRepository.findByVenueId(venueId, pageable);
    }

    @Override
    public Review createReview(Review review) {
        if (reviewRepository.existsByBookingId(review.getBooking().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review for this booking already exists.");
        }
        return reviewRepository.save(review);
    }

    @Override
    public void updateReview(Long reviewId, Review review) {
        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        existingReview.setRating(review.getRating());
        existingReview.setComment(review.getComment());
        reviewRepository.save(existingReview);
    }

    @Override
    public void deleteReview(Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        reviewRepository.deleteById(reviewId);
    }
}
