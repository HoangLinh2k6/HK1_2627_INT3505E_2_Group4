package group4.backend.soa.service;

import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import org.springframework.data.domain.Page;
import java.util.List;

public interface VenueService {
    Page<Venue> getVenues(int page, int size);
    Page<Venue> getVenuesByCityAndStatus(String city, VenueStatus status, int page, int size);
    Page<Venue> getVenuesByCityAndDistrictAndStatus(String city, String district, VenueStatus status, int page, int size);
    Venue getVenueById(Long id);
    List<Venue> getVenuesByOwnerId(Long ownerId);
    Venue createVenue(Venue venue);
    Venue updateVenue(Long id, Venue venue);
    void deleteVenue(Long id);
}
