package group4.backend.soa.controller;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
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
public class CourController {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CourtService courtService;

    @GetMapping
    public ResponseEntity<List<Court>> getCourtsByVenueId(@RequestParam(required = false) Long venueId) {
        if (venueId == null) {
            return ResponseEntity.ok(courtService.getAllCourts());
        }
        return ResponseEntity.ok(courtService.getCourtsByVenueId(venueId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Court> getCourtById(@PathVariable Long id) {
        Court court = courtService.getCourtById(id);
        if (court != null) {
            return ResponseEntity.ok(court);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Court> updateCourt(@PathVariable Long id, @RequestBody Court court) {
        Court updatedCourt = courtService.updateCourt(id, court);
        if (updatedCourt != null) {
            return ResponseEntity.ok(updatedCourt);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourt(@PathVariable Long id) {
        courtService.deleteCourt(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courtId}/availability")
    public ResponseEntity<Object> getAvailability(@PathVariable Long courtId, @RequestParam String date) {
        return ResponseEntity.ok(courtService.getAvailability(courtId, LocalDate.parse(date)));
    }

    @GetMapping("/{courtId}/quote")
    public ResponseEntity<Object> getQuote(@PathVariable Long courtId, @RequestParam String start, @RequestParam String end) {
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
