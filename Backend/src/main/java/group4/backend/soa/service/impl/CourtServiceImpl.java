package group4.backend.soa.service.impl;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;

@Service 
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {
    private final CourtRepository courtRepository;

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
        if (existingCourt == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found");
        }
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
        Court existingCourt = getCourtById(courtId);
        if (existingCourt == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found");
        }
        //logic
        return null;
        
    }

    @Override
    public Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end) {
        Court existingCourt = getCourtById(courtId);
        if (existingCourt == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found");
        }
        //logic
        return null;
    }
}
