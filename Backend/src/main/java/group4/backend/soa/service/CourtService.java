package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.CourtRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static group4.backend.soa.service.CourtSchedule.SLOT_MINUTES;
import static group4.backend.soa.service.CourtSchedule.ZONE;

@Service
@RequiredArgsConstructor
public class CourtService {
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final CourtSchedule schedule;
    private final EntityManager entities;

    public List<Court> getAllCourts() {
        return courtRepository.findAll();
    }

    public List<Court> getCourtsByVenueId(Long venueId) {
        return courtRepository.findByVenueId(venueId);
    }

    public List<Court> getCourtsByVenueIdAndStatus(Long venueId, CourtStatus status) {
        List<Court> courts = courtRepository.findByVenueIdAndStatus(venueId, status);
        if (courts.isEmpty()) {
            throw AccessRules.notFound("Court");
        }
        return courts;
    }

    public List<Court> getCourtsByVenueIdAndSportTypeId(Long venueId, Long sportTypeId) {
        List<Court> courts = courtRepository.findByVenueIdAndSportTypeId(venueId, sportTypeId);
        if (courts.isEmpty()) {
            throw AccessRules.notFound("Court");
        }
        return courts;
    }

    public Court createCourt(Court court) {
        CourtSchedule.validateRate(court.getBasePricePerHour());
        return courtRepository.save(court);
    }

    public Court getCourtById(Long id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Court", id));
    }

    @Transactional
    public Court updateCourt(Long id, Court court) {
        CourtSchedule.validateRate(court.getBasePricePerHour());
        Court existingCourt = courtRepository.lockById(id).orElseThrow(() -> AccessRules.notFound("Court", id));
        entities.refresh(existingCourt);
        if (court.getName() != null) {
            existingCourt.setName(court.getName().strip());
        }
        if (court.getSportType() != null) {
            existingCourt.setSportType(court.getSportType());
        }
        if (court.getStatus() != null) {
            existingCourt.setStatus(court.getStatus());
        }
        if (court.getBasePricePerHour() != null) {
            existingCourt.setBasePricePerHour(court.getBasePricePerHour());
        }
        return existingCourt;
    }

    public void deleteCourt(Long id) {
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Court", id));
        courtRepository.delete(court);
    }

    public Object getAvailability(Long courtId, LocalDate date) {
        Court court = getCourtById(courtId);
        var hours = CourtSchedule.hours(court, date);
        var now = schedule.now();
        var occupied = bookingRepository.findOccupied(List.of(courtId), hours.open(), hours.close(), now);
        var slots = new ArrayList<Slot>();
        for (var start = hours.open(); !start.plusMinutes(SLOT_MINUTES).isAfter(hours.close());
                start = start.plusMinutes(SLOT_MINUTES)) {
            var end = start.plusMinutes(SLOT_MINUTES);
            boolean available = CourtSchedule.canBook(court, start, end, now)
                    && !CourtSchedule.overlaps(occupied, start, end);
            slots.add(new Slot(instant(start), instant(end), available));
        }
        return new Availability(courtId.toString(), date, ZONE.getId(), SLOT_MINUTES, slots);
    }

    public Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end) {
        CourtSchedule.validateInterval(start, end);
        Court court = getCourtById(courtId);
        var now = schedule.now();
        boolean priced = CourtSchedule.offered(court) && CourtSchedule.hours(court, start.toLocalDate()).contains(start, end);
        boolean available = CourtSchedule.canBook(court, start, end, now)
                && !bookingRepository.existsOccupied(courtId, start, end, now);
        var lines = new ArrayList<QuoteLine>();
        if (priced) {
            for (var slot = start; slot.isBefore(end); slot = slot.plusMinutes(SLOT_MINUTES)) {
                lines.add(new QuoteLine(instant(slot), instant(slot.plusMinutes(SLOT_MINUTES)),
                        CourtSchedule.slotPrice(court, slot)));
            }
        }
        return new Quote(courtId.toString(), instant(start), instant(end), Duration.between(start, end).toMinutes(),
                "VND", priced ? CourtSchedule.price(court, start, end) : null, available, lines);
    }

    private static String instant(LocalDateTime value) {
        return value.atZone(ZONE).toInstant().toString();
    }

    public record Slot(String startTime, String endTime, boolean available) {}
    public record Availability(String courtId, LocalDate date, String timezone, int slotMinutes, List<Slot> slots) {}
    public record QuoteLine(String startTime, String endTime, BigDecimal amount) {}
    public record Quote(String courtId, String startTime, String endTime, long durationMinutes, String currency,
                        BigDecimal totalPrice, boolean available, List<QuoteLine> lines) {}
}
