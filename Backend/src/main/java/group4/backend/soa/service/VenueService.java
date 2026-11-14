package group4.backend.soa.service;

import group4.backend.soa.entity.Venue;
import java.util.List;

public interface VenueService {
    List<Venue> getVenues();
    Venue getVenueById(Long id);
    Venue createVenue(Venue venue);
    Venue updateVenue(Long id, Venue venue);
    void deleteVenue(Long id);
}
