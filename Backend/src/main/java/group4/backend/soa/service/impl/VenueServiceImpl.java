package group4.backend.soa.service.impl;

import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.enums.VenueStatus;
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
public class VenueServiceImpl implements VenueService {
    private final VenueRepository venueRepository;

    @Override
    public Page<Venue> getVenues(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findAll(pageable);
        if (venues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No venues found");
        }
        return venues;
    }

    @Override
    public Page<Venue> getVenuesByCityAndStatus(String city, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndStatus(city, status, pageable);
        if (venues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No venues found");
        }
        return venues;
    }

    @Override
    public Page<Venue> getVenuesByCityAndDistrictAndStatus(String city, String district, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndDistrictAndStatus(city, district, status, pageable);
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
    public List<Venue> getVenuesByOwnerId(Long ownerId) {
        List<Venue> venues = venueRepository.findByOwnerId(ownerId);
        if (venues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No venues found for the given owner");
        }
        return venues;
    }

    @Override
    public Venue createVenue(Venue venue) {
        return venueRepository.save(venue);
    }

    @Override
    public Venue updateVenue(Long id, Venue venue) {
        Venue existingVenue = venueRepository.findById(id).orElseThrow(() 
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        existingVenue.setName(venue.getName());
        existingVenue.setAddress(venue.getAddress());
        existingVenue.setDistrict(venue.getDistrict());
        existingVenue.setCity(venue.getCity());
        existingVenue.setDescription(venue.getDescription());
        existingVenue.setOpenTime(venue.getOpenTime());
        existingVenue.setCloseTime(venue.getCloseTime());
        existingVenue.setStatus(venue.getStatus());
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
