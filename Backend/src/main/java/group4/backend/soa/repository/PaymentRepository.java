package group4.backend.soa.repository;

import group4.backend.soa.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByTransactionRef(String transactionRef);
}