package group4.backend.soa.service;

import group4.backend.soa.dto.AddonModels;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudinaryRentalPhotosTests {
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-02T10:00:00Z"), ZoneOffset.UTC);
    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private final CloudinaryRentalPhotos cloudinary =
            new CloudinaryRentalPhotos("cloudinary://123:fake-secret@keosan_dev", "rental-photos", clock, http.build());

    @Test
    void uploadsAnAuthenticatedImageWithAnImmutableReference() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/keosan_dev/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                        .contains("rental-photos", "authenticated", "test-image", "overwrite",
                                "5ab0e180e83233bd6959d04c49e59e2286c921c0aa1225f0ece490508a69806a")
                        .doesNotContain("fake-secret"))
                .andRespond(withSuccess("""
                        {"public_id":"test-image","type":"authenticated","format":"png"}
                        """, MediaType.APPLICATION_JSON));

        cloudinary.upload("test-image", new AddonModels.PhotoContent("image/png", new byte[] {1, 2, 3}));
        server.verify();
    }

    @Test
    void signsALinkThatExpiresAfterFiveMinutesAndCanDownloadThroughIt() {
        var link = cloudinary.link("test-image", "image/png");
        assertThat(link.expiresAt()).isEqualTo(Instant.parse("2030-01-02T10:05:00Z"));
        assertThat(link.url().toString())
                .startsWith("https://api.cloudinary.com/v1_1/keosan_dev/image/download?")
                .contains("expires_at=1893578700", "type=authenticated",
                        "signature=81e9c781d7362dcb54735e77403cc90ba5312085d42b8a523e9592b46226271f")
                .doesNotContain("fake-secret");
        server.expect(requestTo(link.url())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(cloudinary.download("test-image", "image/png")).containsExactly((byte) 1, (byte) 2, (byte) 3);
        server.verify();
    }

    @Test
    void uploadFailureReturnsASanitizedError() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/keosan_dev/image/upload"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("internal provider response"));
        assertThatThrownBy(() -> cloudinary.upload("test-image",
                new AddonModels.PhotoContent("image/png", new byte[] {1})))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(failure.getMessage()).doesNotContain("internal provider response", "fake-secret");
                });
        server.verify();
    }
}
