package group4.backend.soa.service.impl;

import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
        return venueRepository.findByStatus(VenueStatus.ACTIVE, pageable);
    }

    @Override
    public Page<Venue> getVenuesByCityAndStatus(String city, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndStatus(city, status, pageable);
        if (venues.isEmpty()) {
            throw AccessRules.notFound("Venue");
        }
        return venues;
    }

    @Override
    public Page<Venue> getVenuesByCityAndDistrictAndStatus(String city, String district, VenueStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venue> venues = venueRepository.findByCityAndDistrictAndStatus(city, district, status, pageable);
        if (venues.isEmpty()) {
            throw AccessRules.notFound("Venue");
        }
        return venues;
    }

    @Override
    public Venue getVenueById(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue"));
    }

    @Override
    public List<Venue> getVenuesByOwnerId(Long ownerId) {
        return venueRepository.findByOwnerId(ownerId);
    }

    @Override
    public Venue createVenue(Venue venue) {
        return venueRepository.save(venue);
    }

    @Override
    public Venue updateVenue(Long id, Venue venue) {
        Venue existingVenue = venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue"));
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
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Venue"));
        venueRepository.delete(venue);
    }
}
