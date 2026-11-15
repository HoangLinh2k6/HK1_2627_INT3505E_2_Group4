package group4.backend.soa.service;

import group4.backend.soa.entity.Booking;
import org.springframework.data.domain.Page;

public interface BookingService {
    Page<Booking> getBookings(int page, int size);
    Booking getBookingById(Long id);
    Booking createBooking(Booking booking);
    void deleteBooking(Long id);
    void confirmBooking(Long id);
    void completeBooking(Long id);
    void cancelBooking(Long id);
}
