package group4.backend.soa.auth;

import group4.backend.soa.controller.BookingController;
import group4.backend.soa.controller.CourtController;
import group4.backend.soa.controller.SportTypeController;
import group4.backend.soa.controller.UserController;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.User;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.Role;
import group4.backend.soa.repository.SportTypeRepository;
import group4.backend.soa.repository.UserRepository;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.service.SportTypeService;
import group4.backend.soa.service.UserService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, UserController.class, BookingController.class, CourtController.class,
        SportTypeController.class})
@Import({AuthService.class, AuthBeans.class, SecurityConfig.class, AccessRules.class})
class AuthApiTests {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String PASSWORD = "ValidPass123";
    private static final String EMAIL = "person@example.test";
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @MockitoBean Clock clock;
    @MockitoBean UserRepository users;
    @MockitoBean AuthSessionRepository sessions;
    @MockitoBean UserService userService;
    @MockitoBean BookingService bookingService;
    @MockitoBean CourtService courtService;
    @MockitoBean SportTypeRepository sportTypes;
    @MockitoBean SportTypeService sportTypeService;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void apiDocumentationIsPublicWhilePrivateRoutesStillRequireAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("Đặt Sân"));
        mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/bookings:")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui.css"))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk());
        mvc.perform(post("/openapi.yaml"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationHashesPasswordAndAssignsCustomerRole() throws Exception {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
            assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
            user.setId(1L);
            return user;
        });
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"Person@example.test","password":"ValidPass123","fullName":"Test Customer",
                 "phone":"0901234567","role":"ADMIN"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void accessExpiresRefreshRotatesAndLogoutRevokesTheSession() throws Exception {
        var user = user(1L, Role.CUSTOMER);
        user.setPasswordHash(passwords.encode(PASSWORD));
        when(users.lockByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userService.getUserById(1L)).thenReturn(user);

        var stored = new AtomicReference<AuthSession>();
        when(sessions.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0);
            session.setId(10L);
            stored.set(session);
            return session;
        });
        when(sessions.findByAccessHash(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get())
                .filter(session -> session.getAccessHash().equals(invocation.getArgument(0))));
        when(sessions.findByRefreshHash(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get())
                .filter(session -> session.getRefreshHash().equals(invocation.getArgument(0))));
        doAnswer(invocation -> {
            stored.set(null);
            return null;
        }).when(sessions).delete(any(AuthSession.class));

        mvc.perform(login("IncorrectPass")).andExpect(status().isUnauthorized());
        var initial = mvc.perform(login(PASSWORD)).andExpect(status().isOk()).andReturn();
        String access = accessToken(initial);
        String refresh = refreshToken(initial);
        assertThat(initial.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "Secure", "SameSite=Strict");
        assertThat(stored.get().getAccessHash()).isNotEqualTo(access).hasSize(64);
        assertThat(stored.get().getRefreshHash()).isNotEqualTo(refresh).hasSize(64);
        mvc.perform(get("/users/me?id=99").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("1"));

        when(clock.instant()).thenReturn(NOW.plusSeconds(900));
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("keosan_refresh", refresh))
                        .header(HttpHeaders.ORIGIN, "https://another.example"))
                .andExpect(status().isForbidden());
        var refreshed = mvc.perform(post("/auth/refresh").cookie(new Cookie("keosan_refresh", refresh)))
                .andExpect(status().isOk()).andReturn();
        assertThat(accessToken(refreshed)).isNotEqualTo(access);
        mvc.perform(post("/auth/refresh").cookie(new Cookie("keosan_refresh", refresh)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed))
                        .cookie(new Cookie("keosan_refresh", refreshToken(refreshed))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("keosan_refresh", refreshToken(refreshed))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAndOwnershipBothApplyToBookingAndCourtOperations() throws Exception {
        var court = new Court();
        var venue = new Venue();
        venue.setOwner(user(2L, Role.OWNER));
        court.setVenue(venue);
        var booking = new Booking();
        booking.setCourt(court);
        booking.setUser(user(1L, Role.CUSTOMER));
        when(bookingService.getBookingById(20L)).thenReturn(booking);
        when(courtService.getCourtById(30L)).thenReturn(court);

        mvc.perform(get("/bookings/20")).andExpect(status().isUnauthorized());
        String otherCustomer = bearer(user(4L, Role.CUSTOMER));
        mvc.perform(get("/bookings/20").header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/bookings/20/confirm").header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isForbidden());
        String otherOwner = bearer(user(3L, Role.OWNER));
        mvc.perform(patch("/bookings/20/confirm").header(HttpHeaders.AUTHORIZATION, otherOwner))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/courts/30").header(HttpHeaders.AUTHORIZATION, otherOwner))
                .andExpect(status().isNotFound());
        verify(bookingService, never()).confirmBooking(anyLong());
        verify(courtService, never()).deleteCourt(anyLong());

        String venueOwner = bearer(venue.getOwner());
        mvc.perform(patch("/bookings/20/confirm").header(HttpHeaders.AUTHORIZATION, venueOwner))
                .andExpect(status().isOk());
        verify(bookingService).confirmBooking(20L);
        mvc.perform(post("/sport-types").header(HttpHeaders.AUTHORIZATION, venueOwner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Football\"}"))
                .andExpect(status().isForbidden());
    }

    private String bearer(User user) throws Exception {
        when(users.lockByEmail(EMAIL)).thenReturn(Optional.of(user));
        user.setPasswordHash(passwords.encode(PASSWORD));
        when(sessions.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0);
            session.setId(user.getId());
            when(sessions.findByAccessHash(session.getAccessHash())).thenReturn(Optional.of(session));
            return session;
        });
        return "Bearer " + accessToken(mvc.perform(login(PASSWORD)).andExpect(status().isOk()).andReturn());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String password) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}");
    }

    private String accessToken(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asString();
    }

    private static String refreshToken(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE).split("[=;]", 3)[1];
    }

    private static User user(Long id, Role role) {
        var user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEmail(EMAIL);
        user.setFullName("Test account");
        user.setPhone("0901234567");
        return user;
    }
}
