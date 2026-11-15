package group4.backend.soa.controller;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
//Chưa hoàn thành
@RestController
@RequestMapping("/venues/{venueId}/courts")
@RequiredArgsConstructor
public class VenueCourtController {
    private final CourtService courtService;

    @GetMapping
    public ResponseEntity<List<Court>> getCourtsByVenueId(@PathVariable Long venueId) {
        List<Court> courts = courtService.getCourtsByVenueId(venueId);
        return ResponseEntity.ok(courts);
    }

    @PostMapping
    public ResponseEntity<Court> createCourt(@RequestBody Court court) {
        Court createdCourt = courtService.createCourt(court);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCourt);
    }
}
