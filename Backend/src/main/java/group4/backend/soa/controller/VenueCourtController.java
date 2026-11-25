package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.repository.SportTypeRepository;
import group4.backend.soa.service.VenueService;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

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
        var errors = new ArrayList<FieldError>();
        if (court.getName() == null || court.getName().isBlank()) {
            errors.add(FieldError.of("name", "NotBlank", "Court name is required."));
        }
        if (court.getSportType() == null || court.getSportType().getId() == null || court.getSportType().getId() <= 0) {
            errors.add(FieldError.of("sportType.id", "REQUIRED",
                    "sportType.id is required and must be a positive integer.",
                    court.getSportType() == null ? null : court.getSportType().getId()));
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    errors.size() == 1 ? errors.getFirst().message()
                            : "Court creation request has " + errors.size() + " invalid or missing field(s).",
                    errors);
        }
        Long sportTypeId = court.getSportType().getId();
        court.setSportType(sportTypes.findById(sportTypeId)
                .orElseThrow(() -> AccessRules.notFound("Sport type", sportTypeId)));
        court.setId(null);
        court.setName(court.getName().strip());
        court.setVenue(venue);
        if (court.getStatus() == null) {
            court.setStatus(CourtStatus.AVAILABLE);
        }
        Court createdCourt = courtService.createCourt(court);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCourt);
    }
}
