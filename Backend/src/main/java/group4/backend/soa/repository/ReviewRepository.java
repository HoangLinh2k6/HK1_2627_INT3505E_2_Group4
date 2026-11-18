package group4.backend.soa.repository;

import group4.backend.soa.entity.Review;
import group4.backend.soa.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Override
    @EntityGraph(attributePaths = {"venue", "venue.owner", "user", "booking", "booking.user", "booking.court", "booking.court.venue", "booking.court.venue.owner", "booking.court.sportType"})
    List<Review> findAll();

    @Override
    @EntityGraph(attributePaths = {"user"})
    Optional<Review> findById(Long id);

    @EntityGraph(attributePaths = {"venue", "venue.owner", "user", "booking", "booking.user", "booking.court", "booking.court.venue", "booking.court.venue.owner", "booking.court.sportType"})
    @Query("""
            select r from Review r
            where r.venue.id = :venueId and r.booking.status = :status
              and r.booking.user.id = r.user.id and r.booking.court.venue.id = r.venue.id
            """)
    Page<Review> findVerifiedByVenue(@Param("venueId") Long venueId, @Param("status") BookingStatus status, Pageable pageable);
    boolean existsByBookingId(Long bookingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.venue.id = :venueId")
    Double findAverageRatingByVenueId(@Param("venueId") Long venueId);
}
