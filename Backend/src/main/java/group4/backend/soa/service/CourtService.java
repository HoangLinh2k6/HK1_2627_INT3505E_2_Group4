package group4.backend.soa.service;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CourtService {
    List<Court> getAllCourts();
    List<Court> getCourtsByVenueId(Long venueId);
    List<Court> getCourtsByVenueIdAndStatus(Long venueId, CourtStatus status);
    List<Court> getCourtsByVenueIdAndSportTypeId(Long venueId, Long sportTypeId);
    Court createCourt(Court court);
    Court getCourtById(Long id);
    Court updateCourt(Long id, Court court);
    void deleteCourt(Long id);
    Object getAvailability(Long courtId, LocalDate date);
    Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end);
}
