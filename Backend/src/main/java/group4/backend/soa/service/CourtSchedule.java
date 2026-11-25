package group4.backend.soa.service;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
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
import org.springframework.stereotype.Component;

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
        return parseTime("time", value);
    }

    public static LocalDateTime parseTime(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            String message = fieldName + " is required (ISO-8601 date-time, e.g. '2030-01-02T17:00:00+07:00').";
            throw ApiException.badRequest("INVALID_DATETIME", message,
                    FieldError.of(fieldName, "REQUIRED", message));
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException invalid) {
                String message = "Invalid date-time format for '" + fieldName
                        + "'. Use ISO-8601 format such as '2030-01-02T17:00:00+07:00' or '2030-01-02T17:00:00'.";
                throw ApiException.badRequest("INVALID_DATETIME", message,
                        FieldError.of(fieldName, "INVALID_FORMAT", message, value));
            }
        }
    }

    public static void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw ApiException.badRequest("INVALID_TIME_INTERVAL",
                    "Both startTime and endTime are required.");
        }
        if (!end.isAfter(start)) {
            String message = "endTime (" + end + ") must be strictly after startTime (" + start + ").";
            throw ApiException.badRequest("INVALID_TIME_INTERVAL", message,
                    FieldError.of("endTime", "INVALID_TIME_RANGE", message, end.toString()));
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            String message = "startTime and endTime must fall on the same local date in " + ZONE + ".";
            throw ApiException.badRequest("INVALID_TIME_INTERVAL", message,
                    FieldError.of("endTime", "DIFFERENT_LOCAL_DATE", message, end.toString()));
        }
        if (!aligned(start, end)) {
            long minutes = Duration.between(start, end).toMinutes();
            String message = "Duration (" + minutes + " minutes) must be a positive multiple of "
                    + SLOT_MINUTES + " minutes.";
            throw ApiException.badRequest("INVALID_DURATION", message,
                    FieldError.of("endTime", "INVALID_DURATION", message, end.toString()));
        }
    }

    public static void validateRate(BigDecimal rate) {
        if (rate != null && (rate.signum() <= 0 || rate.compareTo(MAX_HOURLY_RATE) > 0
                || rate.stripTrailingZeros().scale() > 0)) {
            String message = "basePricePerHour must be a whole integer between 1 and 100,000,000 VND.";
            throw ApiException.badRequest("INVALID_BASE_PRICE", message,
                    FieldError.of("basePricePerHour", "INVALID_VALUE", message, rate));
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
