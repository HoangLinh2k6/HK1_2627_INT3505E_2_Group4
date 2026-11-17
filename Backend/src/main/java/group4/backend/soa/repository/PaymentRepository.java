package group4.backend.soa.repository;

import group4.backend.soa.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Fetch booking and nested associations in a single query to avoid N+1 during serialization
    @Override
    @EntityGraph(attributePaths = {"booking", "booking.user", "booking.court", "booking.court.venue", "booking.court.venue.owner", "booking.court.sportType"})
    List<Payment> findAll();

    @Override
    @EntityGraph(attributePaths = {"booking", "booking.user", "booking.court", "booking.court.venue", "booking.court.venue.owner", "booking.court.sportType"})
    Optional<Payment> findById(Long id);

    @EntityGraph(attributePaths = {"booking", "booking.user", "booking.court", "booking.court.venue", "booking.court.venue.owner", "booking.court.sportType"})
    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionRef(String transactionRef);
}