package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
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
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<Venue>> getOwnVenues() {
        return ResponseEntity.ok(venueService.getVenuesByOwnerId(access.userId()));
    }

    @GetMapping("/{ownerId}")
    public ResponseEntity<List<Venue>> getVenuesByOwnerId(@PathVariable Long ownerId) {
        if (!ownerId.equals(access.userId())) {
            throw AccessRules.notFound("Owner", ownerId);
        }
        return getOwnVenues();
    }
}
