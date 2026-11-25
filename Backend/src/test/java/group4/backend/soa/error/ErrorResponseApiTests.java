package group4.backend.soa.error;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.auth.AuthController;
import group4.backend.soa.auth.AuthPrincipal;
import group4.backend.soa.auth.AuthService;
import group4.backend.soa.auth.SecurityConfig;
import group4.backend.soa.controller.BookingAddonController;
import group4.backend.soa.controller.BookingController;
import group4.backend.soa.controller.BookingPaymentController;
import group4.backend.soa.controller.CourtController;
import group4.backend.soa.controller.SportTypeController;
import group4.backend.soa.controller.VenueAddonController;
import group4.backend.soa.controller.VenueController;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.User;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.Role;
import group4.backend.soa.service.AddonService;
import group4.backend.soa.service.BookingService;
import group4.backend.soa.service.CourtService;
import group4.backend.soa.service.PaymentService;
import group4.backend.soa.service.SportTypeService;
import group4.backend.soa.service.UserService;
import group4.backend.soa.service.VenueSearch;
import group4.backend.soa.service.VenueService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({
        AuthController.class,
        BookingController.class,
        BookingPaymentController.class,
        CourtController.class,
        SportTypeController.class,
        VenueController.class,
        VenueAddonController.class,
        BookingAddonController.class
})
@Import({SecurityConfig.class, AccessRules.class, GlobalExceptionHandler.class})
class ErrorResponseApiTests {
    private static final Instant NOW = Instant.parse("2026-11-25T09:30:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    @MockitoBean AuthService authService;
    @MockitoBean BookingService bookingService;
    @MockitoBean PaymentService paymentService;
    @MockitoBean CourtService courtService;
    @MockitoBean SportTypeService sportTypeService;
    @MockitoBean UserService userService;
    @MockitoBean VenueService venueService;
    @MockitoBean VenueSearch venueSearch;
    @MockitoBean AddonService addonService;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(authService.authenticate("customer")).thenReturn(Optional.of(new AuthPrincipal(1L, Role.CUSTOMER, 1L)));
        when(authService.authenticate("owner")).thenReturn(Optional.of(new AuthPrincipal(2L, Role.OWNER, 2L)));
        when(authService.authenticate("admin")).thenReturn(Optional.of(new AuthPrincipal(3L, Role.ADMIN, 3L)));
        when(authService.authenticate("expired-token")).thenReturn(Optional.empty());
    }

    @Test
    void securityUnauthorizedDistinguishesMissingInvalidAndUnsupportedAuthorization() throws Exception {
        mvc.perform(get("/bookings/20"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.type").value("urn:keosan:problem:authentication-required"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.detail").value(containsString("Bearer")))
                .andExpect(jsonPath("$.instance").value("/bookings/20"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-25T09:30:00Z"));

        mvc.perform(get("/bookings/20").header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"))
                .andExpect(jsonPath("$.detail").value(containsString("expired")));

        mvc.perform(get("/bookings/20").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_HEADER"))
                .andExpect(jsonPath("$.detail").value(containsString("Bearer scheme")));
    }

    @Test
    void securityForbiddenIncludesRoleAndRouteContext() throws Exception {
        mvc.perform(post("/bookings/20/confirm").header(HttpHeaders.AUTHORIZATION, "Bearer customer"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:keosan:problem:access-denied"))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value(allOf(containsString("CUSTOMER"), containsString("POST /bookings/20/confirm"))))
                .andExpect(jsonPath("$.instance").value("/bookings/20/confirm"));
    }

    @Test
    void validationErrorsReturnFieldDetailsAndSanitizeSensitiveValues() throws Exception {
        mvc.perform(post("/auth/register")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":"","phone":"12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:keosan:problem:validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].rejectedValue").value(hasItem("not-an-email")))
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')].message")
                        .value(hasItem(containsString("10 digits starting with 0"))))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].rejectedValue").isEmpty());
    }

    @Test
    void unreadableJsonAndInvalidEnumReturnFieldSpecificProblem() throws Exception {
        mvc.perform(post("/venues/1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Water","kind":"INVALID_KIND","unitPrice":10000,"stockOnHand":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.detail").value(allOf(containsString("CONSUMABLE"), containsString("RENTAL"))))
                .andExpect(jsonPath("$.errors[0].field").value("kind"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("INVALID_KIND"));

        mvc.perform(post("/venues/1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.detail").value(containsString("Malformed JSON")));
    }

    @Test
    void missingParametersHeadersAndTypeMismatchesReturnDetailedErrors() throws Exception {
        mvc.perform(get("/courts/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("not-a-number"));

        mvc.perform(get("/courts/1/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("date"));

        mvc.perform(get("/courts/1/availability").param("date", "invalid-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("YYYY-MM-DD")));

        mvc.perform(post("/bookings/1/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":1,"quantity":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    void businessValidationAndPaymentErrorsIncludeStructuredFieldDetails() throws Exception {
        mvc.perform(post("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer")
                        .header("Idempotency-Key", "invalid key with spaces!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"court":{"id":0},"startTime":"","endTime":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(4));

        var booking = new Booking();
        booking.setId(5L);
        booking.setStatus(BookingStatus.CONFIRMED);
        var customer = new User();
        customer.setId(1L);
        booking.setUser(customer);
        var venue = new Venue();
        var owner = new User();
        owner.setId(2L);
        venue.setOwner(owner);
        var court = new Court();
        court.setVenue(venue);
        booking.setCourt(court);
        when(bookingService.getBookingById(5L)).thenReturn(booking);

        mvc.perform(post("/bookings/5/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"CRYPTO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"))
                .andExpect(jsonPath("$.detail").value(allOf(containsString("CASH"), containsString("VNPAY"))))
                .andExpect(jsonPath("$.errors[0].field").value("method"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("CRYPTO"));
    }

    @Test
    void dataIntegrityAndUnexpectedErrorsAreSanitizedAndMappedCleanly() throws Exception {
        doThrow(new DataIntegrityViolationException("Cannot delete or update a parent row: a foreign key constraint fails (`defaultdb`.`courts`)"))
                .when(sportTypeService).deleteSportType(1L);

        mvc.perform(delete("/sport-types/1").header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.detail").value(containsString("referenced")))
                .andExpect(jsonPath("$.detail").value(not(containsString("defaultdb"))));

        doThrow(new RuntimeException("secret-internal-sql-details"))
                .when(sportTypeService).deleteSportType(2L);

        mvc.perform(delete("/sport-types/2").header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret-internal-sql-details"))));
    }

    @Test
    void unsupportedMediaTypeReturnsDetailed415Problem() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=a@b.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.detail").value(containsString("application/json")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApiSpecificationsParseAndDocumentUnifiedProblemDetailsSchema() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(500);
        Yaml yaml = new Yaml(options);
        for (String spec : new String[] {
                "src/main/resources/static/openapi.yaml",
                "openapi-booking.yaml",
                "openapi-services.yaml"
        }) {
            Map<String, Object> doc = yaml.load(Files.readString(Path.of(spec)));
            Map<String, Object> components = (Map<String, Object>) doc.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            org.assertj.core.api.Assertions.assertThat(schemas)
                    .as("Schemas in " + spec)
                    .containsKeys("ApiError", "FieldError");
        }
    }
}
