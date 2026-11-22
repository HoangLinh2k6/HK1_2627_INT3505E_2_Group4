package group4.backend.soa.service.impl;

import group4.backend.soa.service.BookingService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;


@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;

    @Override
    public Page<Booking> getBookingsByUserId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByUserId(id, pageable);
        if (bookings.isEmpty()) {
            throw AccessRules.notFound("Booking");
        }
        return bookings;
    }

    @Override 
    public Page<Booking> getBookingsByVenueId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings = bookingRepository.findByCourtVenueId(id, pageable);
        if (bookings.isEmpty()) {
            throw AccessRules.notFound("Booking");
        }
        return bookings;
    }

    @Override
    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking"));
    }

    @Override
    public Booking createBooking(Booking booking) {
        if (bookingRepository.existsOverlapping(
            booking.getCourt().getId(),
            booking.getStartTime(),
            booking.getEndTime(),
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking time overlaps with an existing booking");
        }
        return bookingRepository.save(booking);
    }

    @Override
    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Booking"));
        bookingRepository.delete(booking);
    }

    @Override
    public void confirmBooking(Long id) {
        Booking booking = getBookingById(id);
        switch (booking.getStatus()) {
            case PENDING:
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending bookings can be confirmed");
        }
    }

    @Override
    public void completeBooking(Long id) {
        Booking booking = getBookingById(id);
        switch (booking.getStatus()) {
            case CONFIRMED:
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only confirmed bookings can be completed");
        }
    }

    @Override
    public void cancelBooking(Long id) {
        Booking booking = getBookingById(id);
        switch (booking.getStatus()) {
            case PENDING:
            case CONFIRMED:
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending or confirmed bookings can be cancelled");
        }
    }
}
