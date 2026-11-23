package group4.backend.soa.controller;

import group4.backend.soa.auth.AuthPrincipal;
import group4.backend.soa.auth.AuthService;
import group4.backend.soa.auth.SecurityConfig;
import group4.backend.soa.dto.AddonModels;
import group4.backend.soa.enums.AddonKind;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.enums.Role;
import group4.backend.soa.service.AddonService;
import group4.backend.soa.service.RentalPhotoService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({VenueAddonController.class, BookingAddonController.class, RentalPhotoController.class})
@Import(SecurityConfig.class)
class AddonApiTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean AddonService addons;
    @MockitoBean RentalPhotoService photos;

    @BeforeEach
    void authenticate() {
        when(auth.authenticate("customer")).thenReturn(Optional.of(new AuthPrincipal(1L, Role.CUSTOMER, 1L)));
        when(auth.authenticate("owner")).thenReturn(Optional.of(new AuthPrincipal(2L, Role.OWNER, 2L)));
    }

    @Test
    void catalogIsPublicAndCreationRequiresAnOwner() throws Exception {
        mvc.perform(get("/venues/1/services")).andExpect(status().isOk());
        var body = """
                {"name":"Water","kind":"CONSUMABLE","unitPrice":10000,"stockOnHand":2}
                """;
        mvc.perform(post("/venues/1/services").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer"))
                .andExpect(status().isForbidden());
        when(addons.createService(eq(1L), any())).thenReturn(new AddonModels.ServiceView(
                3L, "Water", AddonKind.CONSUMABLE, BigDecimal.valueOf(10_000), true, 2, 2L));
        mvc.perform(post("/venues/1/services").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("CONSUMABLE"));
    }

    @Test
    void rentalPhotoEndpointsUsePrivateBearerAuthAndBindMultipartFields() throws Exception {
        var file = new MockMultipartFile("file", "asset.png", "image/png", new byte[] {1});
        mvc.perform(get("/rentals/1/photos/2")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/rentals/1/photos").file(file).param("phase", "CHECK_IN").param("angle", "DETAIL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/rentals/1/photos").file(file).param("phase", "CHECK_IN").param("angle", "DETAIL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer"))
                .andExpect(status().isOk());
        verify(photos).upload(eq(1L), eq(RentalPhotoPhase.CHECK_IN), eq(RentalPhotoAngle.DETAIL), any());

        mvc.perform(get("/rentals/1/photos/2/link")).andExpect(status().isUnauthorized());
        when(photos.link(1L, 2L)).thenReturn(new AddonModels.PhotoLink(
                URI.create("https://api.cloudinary.com/test-link"), Instant.parse("2030-01-02T00:05:00Z")));
        mvc.perform(get("/rentals/1/photos/2/link").header(HttpHeaders.AUTHORIZATION, "Bearer customer"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.url").value("https://api.cloudinary.com/test-link"));
    }
}
