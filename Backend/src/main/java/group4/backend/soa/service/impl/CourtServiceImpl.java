package group4.backend.soa.service.impl;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;

    @Override
    public List<Court> getAllCourts() {
        return courtRepository.findAll();
    }

    @Override
    public List<Court> getCourtsByVenueId(Long venueId) {
        List<Court> courts = courtRepository.findByVenueId(venueId);
        if (courts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No courts found for the specified venue");
        }
        return courts;
    }

    public List<Court> getCourtsByVenueIdAndStatus(Long venueId, CourtStatus status) {
        List<Court> courts = courtRepository.findByVenueIdAndStatus(venueId, status);
        if (courts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No courts found for the specified venue and status");
        }
        return courts;
    }

    public List<Court> getCourtsByVenueIdAndSportTypeId(Long venueId, Long sportTypeId) {
        List<Court> courts = courtRepository.findByVenueIdAndSportTypeId(venueId, sportTypeId);
        if (courts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No courts found for the specified venue and sport type");
        }
        return courts;
    }

    @Override
    public Court createCourt(Court court) {
        return courtRepository.save(court);
    }

    @Override
    public Court getCourtById(Long id) {
        return courtRepository.findById(id).orElseThrow(()
                -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
    }

    @Override
    public Court updateCourt(Long id, Court court) {
        Court existingCourt = getCourtById(id);
        existingCourt.setName(court.getName());
        existingCourt.setVenue(court.getVenue());
        existingCourt.setSportType(court.getSportType());
        existingCourt.setStatus(court.getStatus());
        return courtRepository.save(existingCourt);
    }

    @Override
    public void deleteCourt(Long id) {
        courtRepository.deleteById(id);
        if (courtRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete court");
        }
    }

    @Override
    public Object getAvailability(Long courtId, LocalDate date) {
        Court court = getCourtById(courtId);
        Venue venue = court.getVenue();
        LocalTime openTime = venue != null && venue.getOpenTime() != null ? venue.getOpenTime() : LocalTime.of(6, 0);
        LocalTime closeTime = venue != null && venue.getCloseTime() != null ? venue.getCloseTime() : LocalTime.of(23, 0);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        List<Booking> activeBookings = bookingRepository.findByCourtIdAndStatusInAndStartTimeBetween(
                courtId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                dayStart,
                dayEnd
        );

        boolean courtActive = court.getStatus() == CourtStatus.AVAILABLE
                && (venue == null || venue.getStatus() == VenueStatus.ACTIVE);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (LocalDateTime t = date.atTime(openTime); t.isBefore(date.atTime(closeTime)); t = t.plusMinutes(30)) {
            LocalDateTime slotEnd = t.plusMinutes(30);
            LocalDateTime slotStart = t;
            boolean blocked = activeBookings.stream().anyMatch(b ->
                    (b.getStatus() != BookingStatus.PENDING || b.getExpiresAt() == null || b.getExpiresAt().isAfter(now))
                            && b.getStartTime().isBefore(slotEnd)
                            && b.getEndTime().isAfter(slotStart)
            );
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("startTime", slotStart.atZone(VN_ZONE).toInstant().toString());
            slot.put("endTime", slotEnd.atZone(VN_ZONE).toInstant().toString());
            slot.put("available", courtActive && slotStart.isAfter(now) && !blocked);
            slots.add(slot);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courtId", String.valueOf(courtId));
        result.put("date", date.toString());
        result.put("timezone", "Asia/Ho_Chi_Minh");
        result.put("slotMinutes", 30);
        result.put("slots", slots);
        return result;
    }

    @Override
    public Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end) {
        Court court = getCourtById(courtId);
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
        boolean overlapping = bookingRepository.existsOverlapping(
                courtId,
                start,
                end,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courtId", String.valueOf(court.getId()));
        result.put("startTime", start.atZone(VN_ZONE).toInstant().toString());
        result.put("endTime", end.atZone(VN_ZONE).toInstant().toString());
        result.put("durationMinutes", Duration.between(start, end).toMinutes());
        result.put("available", !overlapping);
        return result;
    }
}
