package group4.backend.soa.repository;

import group4.backend.soa.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByVenueId(Long venueId, Pageable pageable);
    boolean existsByBookingId(Long bookingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.venue.id = :venueId")
    Double findAverageRatingByVenueId(@Param("venueId") Long venueId);
}