package group4.backend.soa.service.impl;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
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

    @Override
    public Court createCourt(Court court) {
        
        return courtRepository.save(court);
    }

    @Override
    public Court getCourtById(Long id) {
        return courtRepository.findById(id).orElse(null);
    }

    @Override
    public Court updateCourt(Long id, Court court) {
        Court existingCourt = getCourtById(id);
        if (existingCourt != null) {
            existingCourt.setName(court.getName());
            return courtRepository.save(existingCourt);
        }
        return null;
    }

    @Override
    public void deleteCourt(Long id) {
        courtRepository.deleteById(id);
    }

    @Override
    public Object getAvailability(Long courtId, LocalDate date) {
        return null;
    }

    @Override
    public Object getQuote(Long courtId, LocalDateTime start, LocalDateTime end) {
        return null;
    }
}
