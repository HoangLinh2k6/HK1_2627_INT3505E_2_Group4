package group4.backend.soa.service;

import group4.backend.soa.entity.Court;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CourtService {
    List<Court> getCourtsByVenueId(Long venueId);
    Court getCourtById(Long id);
    Court createCourt(Court court);
    Court updateCourt(Long id, Court court);
    void deleteCourt(Long id);
    Object getAvailability(Long courtId, LocalDate date);
    Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end);
}
