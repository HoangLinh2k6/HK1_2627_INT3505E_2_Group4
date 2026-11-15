package group4.backend.soa.service;

import group4.backend.soa.entity.Payment;
import java.util.List;

public interface PaymentService {
    List<Payment> getPaymentsByBookingId(Long bookingId);
    Payment createPayment(Payment payment);
    Payment getPaymentById(Long id);
    //callback chưa hoàn thành
}
