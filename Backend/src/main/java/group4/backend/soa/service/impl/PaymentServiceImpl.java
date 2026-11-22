package group4.backend.soa.service.impl;

import group4.backend.soa.entity.Payment;
import group4.backend.soa.repository.PaymentRepository;
import group4.backend.soa.service.PaymentService;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;

    @Override
    public List<Payment> getPaymentsByBookingId(Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        if (payments.isEmpty()) {
            throw AccessRules.notFound("Payment");
        }
        return payments;
    }

    @Override
    public Payment createPayment(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Override
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Payment"));
    }
}
