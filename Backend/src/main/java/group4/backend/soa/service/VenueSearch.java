package group4.backend.soa.service;

import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.BookingRepository.OccupiedInterval;
import group4.backend.soa.repository.CourtRepository;
import group4.backend.soa.repository.VenueRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static group4.backend.soa.service.CourtSchedule.SLOT_MINUTES;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class VenueSearch {
    private final VenueRepository venues;
    private final CourtRepository courts;
    private final BookingRepository bookings;
    private final CourtSchedule schedule;

    @Transactional(readOnly = true)
    public Page<Venue> search(Filters filters, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page must be non-negative and size must be between 1 and 100");
        }
        if ((filters.sportTypeId() != null && filters.sportTypeId() <= 0)
                || (filters.maxPrice() != null && filters.maxPrice().signum() < 0)) {
            throw badRequest("Invalid sportTypeId or maxPrice");
        }
        var time = searchTime(filters);
        String keyword = keyword(filters.q());
        String city = normalized(filters.city());
        String district = normalized(filters.district());
        var order = Sort.by("id");
        var requestedPage = PageRequest.of(page, size, order);
        if (time == null && filters.sportTypeId() == null && filters.maxPrice() == null) {
            return venues.searchCatalog(keyword, city, district, requestedPage);
        }

        var now = schedule.now();
        var items = new ArrayList<Venue>(size);
        long total = 0;
        // Apply court-level criteria before venue pagination. Fetch courts and bookings once per batch.
        for (int batch = 0; ; batch++) {
            var candidates = venues.searchCandidates(keyword, city, district, PageRequest.of(batch, 100, order));
            if (candidates.isEmpty()) {
                break;
            }
            var venueIds = candidates.stream().map(Venue::getId).toList();
            var candidatesCourts = courts.findSearchCourts(venueIds, filters.sportTypeId(), filters.maxPrice(), keyword);
            var matches = matchingVenues(candidatesCourts, time, now, filters.maxPrice());
            for (var venue : candidates) {
                if (matches.contains(venue.getId())) {
                    if (total >= requestedPage.getOffset() && items.size() < size) {
                        items.add(venue);
                    }
                    total++;
                }
            }
            if (!candidates.hasNext()) {
                break;
            }
        }
        return new PageImpl<>(items, requestedPage, total);
    }

    private Set<Long> matchingVenues(List<Court> candidates, SearchTime time, LocalDateTime now, BigDecimal maxPrice) {
        var matches = new HashSet<Long>();
        if (time == null || candidates.isEmpty()) {
            candidates.forEach(court -> matches.add(court.getVenue().getId()));
            return matches;
        }
        var intervals = bookings.findOccupied(candidates.stream().map(Court::getId).toList(), time.start(), time.end(), now)
                .stream().collect(groupingBy(OccupiedInterval::getCourtId));
        for (var court : candidates) {
            var venueId = court.getVenue().getId();
            if (!matches.contains(venueId) && matchesTime(court, time, now, maxPrice,
                    intervals.getOrDefault(court.getId(), List.of()))) {
                matches.add(venueId);
            }
        }
        return matches;
    }

    private boolean matchesTime(Court court, SearchTime time, LocalDateTime now, BigDecimal cap, List<OccupiedInterval> occupied) {
        if (time.exact()) {
            if (!CourtSchedule.canBook(court, time.start(), time.end(), now)
                    || CourtSchedule.overlaps(occupied, time.start(), time.end())) {
                return false;
            }
            for (var slot = time.start(); slot.isBefore(time.end()); slot = slot.plusMinutes(SLOT_MINUTES)) {
                if (!withinBudget(court, slot, cap)) {
                    return false;
                }
            }
            return true;
        }

        var hours = CourtSchedule.hours(court, time.start().toLocalDate());
        for (var slot = hours.open(); !slot.plusMinutes(SLOT_MINUTES).isAfter(hours.close());
                slot = slot.plusMinutes(SLOT_MINUTES)) {
            var end = slot.plusMinutes(SLOT_MINUTES);
            if (!slot.isBefore(time.start()) && !end.isAfter(time.end())
                    && CourtSchedule.canBook(court, slot, end, now)
                    && withinBudget(court, slot, cap) && !CourtSchedule.overlaps(occupied, slot, end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinBudget(Court court, LocalDateTime slot, BigDecimal cap) {
        return cap == null || CourtSchedule.hourlyRate(court, slot).compareTo(cap) <= 0;
    }

    private static SearchTime searchTime(Filters filters) {
        if (filters.start() != null || filters.end() != null) {
            if (filters.start() == null || filters.end() == null || filters.date() != null
                    || filters.from() != null || filters.to() != null) {
                throw badRequest("Supply both start and end, or use date with optional from and to");
            }
            var start = CourtSchedule.parseTime(filters.start());
            var end = CourtSchedule.parseTime(filters.end());
            CourtSchedule.validateInterval(start, end);
            return new SearchTime(start, end, true);
        }
        if (filters.date() == null) {
            if (filters.from() != null || filters.to() != null) {
                throw badRequest("from and to require a date");
            }
            return null;
        }
        var start = filters.date().atTime(filters.from() == null ? LocalTime.MIN : filters.from());
        var end = filters.date().atTime(filters.to() == null ? LocalTime.MAX : filters.to());
        if (!end.isAfter(start)) {
            throw badRequest("to must be after from");
        }
        return new SearchTime(start, end, false);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String keyword(String value) {
        String normalized = normalized(value);
        return normalized == null ? null : "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private record SearchTime(LocalDateTime start, LocalDateTime end, boolean exact) {}

    public record Filters(String q, String city, String district, Long sportTypeId, BigDecimal maxPrice,
                          String start, String end,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime from,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime to) {}
}
