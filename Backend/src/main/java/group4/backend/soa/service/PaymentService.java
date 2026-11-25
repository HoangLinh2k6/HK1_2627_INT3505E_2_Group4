package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Payment;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.repository.PaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;

    public List<Payment> getPaymentsByBookingId(Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        if (payments.isEmpty()) {
            throw ApiException.notFound("PAYMENTS_NOT_FOUND",
                    "No payment records found for booking #" + bookingId + ".");
        }
        return payments;
    }

    public Payment createPayment(Payment payment) {
        return paymentRepository.save(payment);
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Payment", id));
    }
}
