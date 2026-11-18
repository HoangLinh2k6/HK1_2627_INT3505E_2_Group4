package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.UserService;
import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueController {
    private final VenueService venueService;
    private final UserService userService;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<Venue>> getVenues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(venueService.getVenues(page, size).getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venue> getVenueById(@PathVariable Long id) {
        Venue venue = venueService.getVenueById(id);
        access.requireVisible(venue);
        return ResponseEntity.ok(venue);
    }

    @PostMapping
    public ResponseEntity<Venue> createVenue(@RequestBody Venue venue) {
        venue.setId(null);
        venue.setOwner(userService.getUserById(access.userId()));
        if (venue.getStatus() == null) {
            venue.setStatus(VenueStatus.ACTIVE);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.createVenue(venue));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Venue> updateVenue(@PathVariable Long id, @RequestBody Venue venue) {
        access.requireOwner(venueService.getVenueById(id));
        return ResponseEntity.ok(venueService.updateVenue(id, venue));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVenue(@PathVariable Long id) {
        access.requireOwner(venueService.getVenueById(id));
        venueService.deleteVenue(id);
        return ResponseEntity.noContent().build();
    }
}
