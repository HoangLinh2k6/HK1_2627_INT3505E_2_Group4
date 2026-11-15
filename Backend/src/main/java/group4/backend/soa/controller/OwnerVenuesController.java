package group4.backend.soa.controller;

import group4.backend.soa.service.VenueService;
import group4.backend.soa.entity.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/owner/venues")
@RequiredArgsConstructor
public class OwnerVenuesController {
    private final VenueService venueService;

    @GetMapping("/{ownerId}")
    public ResponseEntity<List<Venue>> getVenuesByOwnerId(@PathVariable Long ownerId) {
        List<Venue> venues = venueService.getVenuesByOwnerId(ownerId);
        return ResponseEntity.ok(venues);
    }
}
