package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.repository.SportTypeRepository;
import group4.backend.soa.service.VenueService;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
//Chưa hoàn thành
@RestController
@RequestMapping("/venues/{venueId}/courts")
@RequiredArgsConstructor
public class VenueCourtController {
    private final CourtService courtService;
    private final VenueService venueService;
    private final SportTypeRepository sportTypes;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<Court>> getCourtsByVenueId(@PathVariable Long venueId) {
        access.requireVisible(venueService.getVenueById(venueId));
        List<Court> courts = courtService.getCourtsByVenueId(venueId).stream().filter(access::canSee).toList();
        return ResponseEntity.ok(courts);
    }

    @PostMapping
    public ResponseEntity<Court> createCourt(@PathVariable Long venueId, @RequestBody Court court) {
        Venue venue = venueService.getVenueById(venueId);
        access.requireOwner(venue);
        if (court.getSportType() == null || court.getSportType().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport type is required");
        }
        court.setSportType(sportTypes.findById(court.getSportType().getId())
                .orElseThrow(() -> AccessRules.notFound("Sport type")));
        court.setId(null);
        court.setVenue(venue);
        if (court.getStatus() == null) {
            court.setStatus(CourtStatus.AVAILABLE);
        }
        Court createdCourt = courtService.createCourt(court);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCourt);
    }
}
