package group4.backend.soa.booking;

import group4.backend.soa.auth.AuthPrincipal;
import group4.backend.soa.auth.AuthService;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.SportType;
import group4.backend.soa.entity.User;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.Role;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.CourtRepository;
import group4.backend.soa.repository.SportTypeRepository;
import group4.backend.soa.repository.UserRepository;
import group4.backend.soa.repository.VenueRepository;
import group4.backend.soa.service.BookingHoldExpiry;
import group4.backend.soa.service.CourtSchedule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.booking.expiry-interval-ms=3600000"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_MYSQL_URL", matches = ".+")
class BookingMysqlTests {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2030, 1, 2);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CourtRepository courts;
    @Autowired VenueRepository venues;
    @Autowired SportTypeRepository sports;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired BookingHoldExpiry expiry;
    @MockitoBean AuthService auth;
    @MockitoBean Clock clock;

    private User customer;
    private Venue venue;
    private SportType badminton;
    private Court court;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("BOOKING_TEST_MYSQL_URL");
        if (!url.matches("jdbc:mysql://[A-Za-z0-9.:-]+/booking_test_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("Use a dedicated MySQL schema named booking_test_<suffix>");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("BOOKING_TEST_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("BOOKING_TEST_MYSQL_PASSWORD", ""));
    }

    @BeforeEach
    void seedCatalog() {
        when(clock.instant()).thenReturn(NOW);
        customer = user("customer", Role.CUSTOMER);
        var owner = user("owner", Role.OWNER);
        authenticate(customer, "customer");
        authenticate(owner, "owner");

        venue = new Venue();
        venue.setOwner(owner);
        venue.setName("Venue " + UUID.randomUUID());
        venue.setCity("Hà Nội");
        venue.setDistrict("Cầu Giấy");
        venue.setOpenTime(LocalTime.of(8, 0));
        venue.setCloseTime(LocalTime.of(22, 15));
        venue.setStatus(VenueStatus.ACTIVE);
        venue = venues.saveAndFlush(venue);
        badminton = sport("Badminton");
        court = court("Court A", badminton, 150_000);
    }

    @Test
    void onlyOneCustomerCanBookTheSameCourtAtTheSameTime() throws Exception {
        authenticate(user("other", Role.CUSTOMER), "other");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> concurrentBooking(ready, start, "customer"));
            var second = pool.submit(() -> concurrentBooking(ready, start, "other"));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(new int[] {first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(bookings.findOccupied(List.of(court.getId()), time("17:00"), time("18:00"),
                LocalDateTime.ofInstant(NOW, CourtSchedule.ZONE))).hasSize(1);
    }

    @Test
    void slotsQuoteAndBookingAgreeAtExpiryAndHonorAdjacentIntervals() throws Exception {
        var expired = hold(court, "16:30", "17:30", LocalDateTime.ofInstant(NOW, CourtSchedule.ZONE));
        slot("16:30", true);
        quote("16:30", "17:30").andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.totalPrice").value(200_000))
                .andExpect(jsonPath("$.lines[0].amount").value(75_000))
                .andExpect(jsonPath("$.lines[1].amount").value(125_000));
        mvc.perform(post("/bookings/{id}/confirm", expired.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isConflict());
        var result = book("customer", "16:30", "17:30", "stable-request")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalPrice").value(200_000)).andReturn();
        var id = bodyId(result);
        book("customer", "16:30", "17:30", "stable-request").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        book("customer", "17:30", "18:00", "stable-request").andExpect(status().isConflict());
        book("customer", "17:30", "18:00", null).andExpect(status().isCreated());
        slot("16:30", false);
        quote("16:30", "17:30").andExpect(jsonPath("$.available").value(false));
        expiry.expireHolds();
        assertThat(bookings.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void openingHoursAndMissingLegacyPricesPreventBookings() throws Exception {
        book("customer", "07:30", "08:00", null).andExpect(status().isBadRequest());
        quote("07:30", "08:00").andExpect(jsonPath("$.available").value(false));
        book("customer", "22:00", "22:30", null).andExpect(status().isBadRequest());
        mvc.perform(get("/courts/{id}/availability", court.getId()).param("date", DATE.toString()))
                .andExpect(jsonPath("$.slots.length()").value(28));

        court.setBasePricePerHour(null);
        courts.saveAndFlush(court);
        slot("17:00", false);
        quote("17:00", "18:00").andExpect(jsonPath("$.available").value(false));
        book("customer", "17:00", "18:00", null).andExpect(status().isConflict());
        mvc.perform(put("/courts/{id}", court.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"basePricePerHour":140000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(court.getName()))
                .andExpect(jsonPath("$.basePricePerHour").value(140_000));
        slot("17:00", true);
    }

    @Test
    void searchRequiresOneCourtToMatchSportHourlyBudgetAndTime() throws Exception {
        court("Football court", sport("Football"), 50_000);
        hold(court, "17:00", "17:30", time("16:00"));
        mvc.perform(get("/venues").param("q", venue.getName()).param("sportTypeId", badminton.getId().toString())
                        .param("maxPrice", "200000").param("start", time("17:30").toString())
                        .param("end", time("18:00").toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/venues").param("q", venue.getName()).param("sportTypeId", badminton.getId().toString())
                        .param("date", DATE.toString()).param("from", "17:00").param("to", "17:30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/venues").param("q", "Court A").param("city", venue.getCity())
                        .param("district", venue.getDistrict()).param("sportTypeId", badminton.getId().toString())
                        .param("date", DATE.toString()).param("from", "17:00").param("to", "18:00")
                        .param("maxPrice", "250000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(venue.getId()));
        mvc.perform(get("/venues").param("q", venue.getName()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private int concurrentBooking(CountDownLatch ready, CountDownLatch start, String token) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Requests did not start together");
        }
        return book(token, "17:00", "18:00", null).andReturn().getResponse().getStatus();
    }

    private ResultActions book(String token, String start, String end, String key) throws Exception {
        var request = post("/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"court":{"id":%d},"startTime":"%s","endTime":"%s","totalPrice":1}
                        """.formatted(court.getId(), time(start).atZone(CourtSchedule.ZONE).toOffsetDateTime(), time(end)));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private ResultActions quote(String start, String end) throws Exception {
        return mvc.perform(get("/courts/{id}/quote", court.getId()).param("start", time(start).toString())
                .param("end", time(end).toString()));
    }

    private void slot(String start, boolean available) throws Exception {
        String instant = time(start).atZone(CourtSchedule.ZONE).toInstant().toString();
        mvc.perform(get("/courts/{id}/availability", court.getId()).param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startTime == '%s')].available".formatted(instant)).value(List.of(available)));
    }

    private Booking hold(Court court, String start, String end, LocalDateTime expiresAt) {
        var booking = new Booking();
        booking.setCourt(court);
        booking.setUser(customer);
        booking.setStartTime(time(start));
        booking.setEndTime(time(end));
        booking.setExpiresAt(expiresAt);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalPrice(BigDecimal.ONE);
        return bookings.saveAndFlush(booking);
    }

    private long bodyId(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private void authenticate(User user, String token) {
        when(auth.authenticate(token)).thenReturn(Optional.of(new AuthPrincipal(user.getId(), user.getRole(), 1L)));
    }

    private User user(String name, Role role) {
        var user = new User();
        user.setRole(role);
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("unused");
        return users.saveAndFlush(user);
    }

    private SportType sport(String name) {
        var sport = new SportType();
        sport.setName(name + " " + UUID.randomUUID());
        return sports.saveAndFlush(sport);
    }

    private Court court(String name, SportType sport, long rate) {
        var result = new Court();
        result.setVenue(venue);
        result.setName(name);
        result.setSportType(sport);
        result.setBasePricePerHour(BigDecimal.valueOf(rate));
        result.setStatus(CourtStatus.AVAILABLE);
        return courts.saveAndFlush(result);
    }

    private static LocalDateTime time(String value) {
        return DATE.atTime(LocalTime.parse(value));
    }
}
