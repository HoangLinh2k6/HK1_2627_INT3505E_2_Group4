package group4.backend.soa.service.impl;

import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private final VenueRepository venueRepository;

    @Override
    public List<Venue> getVenues() {
        List<Venue> venues = venueRepository.findAll();
        if (venues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No venues found");
        }
        return venues;
    }

    @Override
    public Venue getVenueById(Long id) {
        return venueRepository.findById(id).orElseThrow(()
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
    }

    @Override
    public Venue createVenue(Venue venue) {
        return venueRepository.save(venue);
    }

    @Override
    public Venue updateVenue(Long id, Venue venue) {
        Venue existingVenue = venueRepository.findById(id).orElseThrow(() 
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        return venueRepository.save(existingVenue);
    }

    @Override
    public void deleteVenue(Long id) {
        venueRepository.deleteById(id);
        if (venueRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete venue");
        }
    }
}
