package group4.backend.soa.booking;

import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.service.CourtSchedule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourtScheduleTests {
    private final CourtSchedule schedule = new CourtSchedule(
            Clock.fixed(Instant.parse("2030-01-02T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void pricesEachSlotAcrossPeakBoundaries() {
        var court = court();
        assertThat(CourtSchedule.price(court, time("16:30"), time("17:30"))).isEqualByComparingTo("200000");
        assertThat(CourtSchedule.price(court, time("21:30"), time("22:00"))).isEqualByComparingTo("125000");
        assertThat(CourtSchedule.parseTime("2030-01-02T10:00:00Z")).isEqualTo(time("17:00"));
    }

    @Test
    void onlyAllowsWholeFutureSlotsForPricedActiveCourts() {
        var court = court();
        assertThat(schedule.now()).isEqualTo(time("07:00"));
        assertThat(CourtSchedule.canBook(court, time("08:00"), time("08:30"), schedule.now())).isTrue();
        assertThat(CourtSchedule.canBook(court, time("07:30"), time("08:00"), schedule.now())).isFalse();
        assertThat(CourtSchedule.canBook(court, time("21:45"), time("22:15"), schedule.now())).isFalse();
        assertThat(CourtSchedule.canBook(court, time("22:00"), time("22:30"), schedule.now())).isFalse();
        assertThatThrownBy(() -> CourtSchedule.validateInterval(time("08:00"), time("08:45")))
                .isInstanceOf(ResponseStatusException.class);
        court.setBasePricePerHour(null);
        assertThat(CourtSchedule.canBook(court, time("08:00"), time("08:30"), schedule.now())).isFalse();
    }

    private static LocalDateTime time(String value) {
        return LocalDateTime.parse("2030-01-02T" + value);
    }

    private static Court court() {
        var court = new Court();
        var venue = new Venue();
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setOpenTime(LocalTime.of(8, 0));
        venue.setCloseTime(LocalTime.of(22, 15));
        court.setVenue(venue);
        court.setStatus(CourtStatus.AVAILABLE);
        court.setBasePricePerHour(BigDecimal.valueOf(150_000));
        return court;
    }
}
