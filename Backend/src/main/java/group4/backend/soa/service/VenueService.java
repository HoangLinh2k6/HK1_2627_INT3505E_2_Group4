package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.repository.VenueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepository;

    public Page<Venue> getVenues(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return venueRepository.findByStatus(VenueStatus.ACTIVE, pageable);
    }

    public Page<Venue> getVenuesByCityAndStatus(String city, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndStatus(city, status, pageable);
        if (venues.isEmpty()) {
            throw AccessRules.notFound("Venue");
        }
        return venues;
    }

    public Page<Venue> getVenuesByCityAndDistrictAndStatus(String city, String district, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndDistrictAndStatus(city, district, status, pageable);
        if (venues.isEmpty()) {
            throw AccessRules.notFound("Venue");
        }
        return venues;
    }

    public Venue getVenueById(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue", id));
    }

    public List<Venue> getVenuesByOwnerId(Long ownerId) {
        return venueRepository.findByOwnerId(ownerId);
    }

    public Venue createVenue(Venue venue) {
        return venueRepository.save(venue);
    }

    public Venue updateVenue(Long id, Venue venue) {
        Venue existingVenue = venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue", id));
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

    public void deleteVenue(Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue", id));
        venueRepository.delete(venue);
    }
}
