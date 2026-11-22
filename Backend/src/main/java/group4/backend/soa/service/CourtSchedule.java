package group4.backend.soa.service;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.repository.BookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CourtSchedule {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int SLOT_MINUTES = 30;
    private static final LocalTime PEAK_START = LocalTime.of(17, 0);
    private static final LocalTime PEAK_END = LocalTime.of(22, 0);
    private static final BigDecimal PEAK_SURCHARGE = BigDecimal.valueOf(100_000);
    private static final BigDecimal MAX_HOURLY_RATE = BigDecimal.valueOf(100_000_000);
    private static final BigDecimal SLOTS_PER_HOUR = BigDecimal.valueOf(2);

    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZONE).truncatedTo(ChronoUnit.MICROS);
    }

    public static LocalDateTime parseTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date and time");
            }
        }
    }

    public static void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start) || !start.toLocalDate().equals(end.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive interval within one local day is required");
        }
        if (!aligned(start, end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration must be a multiple of 30 minutes");
        }
    }

    public static void validateRate(BigDecimal rate) {
        if (rate != null && (rate.signum() <= 0 || rate.compareTo(MAX_HOURLY_RATE) > 0
                || rate.stripTrailingZeros().scale() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid basePricePerHour");
        }
    }

    public static Hours hours(Court court, LocalDate date) {
        var venue = court.getVenue();
        LocalTime open = venue.getOpenTime() == null ? LocalTime.of(6, 0) : venue.getOpenTime();
        LocalTime close = venue.getCloseTime() == null ? LocalTime.of(23, 0) : venue.getCloseTime();
        return new Hours(date.atTime(open), date.atTime(close));
    }

    public static boolean active(Court court) {
        return court.getStatus() == CourtStatus.AVAILABLE && court.getVenue().getStatus() == VenueStatus.ACTIVE;
    }

    public static boolean offered(Court court) {
        return active(court) && court.getBasePricePerHour() != null && court.getBasePricePerHour().signum() > 0;
    }

    public static boolean canBook(Court court, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return offered(court) && start.isAfter(now) && hours(court, start.toLocalDate()).contains(start, end);
    }

    public static boolean overlaps(List<BookingRepository.OccupiedInterval> occupied, LocalDateTime start, LocalDateTime end) {
        return occupied.stream().anyMatch(interval -> interval.getStartTime().isBefore(end)
                && interval.getEndTime().isAfter(start));
    }

    public static BigDecimal hourlyRate(Court court, LocalDateTime slot) {
        LocalTime time = slot.toLocalTime();
        return !time.isBefore(PEAK_START) && time.isBefore(PEAK_END)
                ? court.getBasePricePerHour().add(PEAK_SURCHARGE) : court.getBasePricePerHour();
    }

    public static BigDecimal price(Court court, LocalDateTime start, LocalDateTime end) {
        BigDecimal amount = BigDecimal.ZERO;
        for (var slot = start; slot.isBefore(end); slot = slot.plusMinutes(SLOT_MINUTES)) {
            amount = amount.add(slotPrice(court, slot));
        }
        return amount;
    }

    public static BigDecimal slotPrice(Court court, LocalDateTime slot) {
        return hourlyRate(court, slot).divide(SLOTS_PER_HOUR, 0, RoundingMode.HALF_UP);
    }

    private static boolean aligned(LocalDateTime anchor, LocalDateTime time) {
        return Duration.between(anchor, time).toNanos() % Duration.ofMinutes(SLOT_MINUTES).toNanos() == 0;
    }

    public record Hours(LocalDateTime open, LocalDateTime close) {
        public boolean contains(LocalDateTime start, LocalDateTime end) {
            return !start.isBefore(open) && !end.isAfter(close) && aligned(open, start) && aligned(open, end);
        }
    }
}
