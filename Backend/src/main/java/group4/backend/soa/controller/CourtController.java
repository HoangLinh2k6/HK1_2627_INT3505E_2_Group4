package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import group4.backend.soa.service.SportTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/courts")
@RequiredArgsConstructor
public class CourtController {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CourtService courtService;
    private final SportTypeService sportTypeService;
    private final AccessRules access;

    @GetMapping
    public ResponseEntity<List<Court>> getCourtsByVenueId(@RequestParam(required = false) Long venueId) {
        var courts = venueId == null ? courtService.getAllCourts() : courtService.getCourtsByVenueId(venueId);
        return ResponseEntity.ok(courts.stream().filter(access::canSee).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Court> getCourtById(@PathVariable Long id) {
        Court court = courtService.getCourtById(id);
        access.requireVisible(court);
        return ResponseEntity.ok(court);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Court> updateCourt(@PathVariable Long id, @RequestBody Court court) {
        Court current = courtService.getCourtById(id);
        access.requireOwner(current.getVenue());
        if (court.getSportType() != null && court.getSportType().getId() != null) {
            court.setSportType(sportTypeService.getSportTypeById(court.getSportType().getId()));
        } else {
            court.setSportType(current.getSportType());
        }
        return ResponseEntity.ok(courtService.updateCourt(id, court));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourt(@PathVariable Long id) {
        access.requireOwner(courtService.getCourtById(id).getVenue());
        courtService.deleteCourt(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courtId}/availability")
    public ResponseEntity<Object> getAvailability(@PathVariable Long courtId, @RequestParam String date) {
        access.requireVisible(courtService.getCourtById(courtId));
        return ResponseEntity.ok(courtService.getAvailability(courtId, LocalDate.parse(date)));
    }

    @GetMapping("/{courtId}/quote")
    public ResponseEntity<Object> getQuote(@PathVariable Long courtId, @RequestParam String start, @RequestParam String end) {
        access.requireVisible(courtService.getCourtById(courtId));
        return ResponseEntity.ok(courtService.getQuote(courtId, parseDateTime(start), parseDateTime(end)));
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(VN_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.parse(value);
        }
    }
}
