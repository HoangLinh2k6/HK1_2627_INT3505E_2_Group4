package group4.backend.soa.service;

import group4.backend.soa.dto.AddonModels;
import group4.backend.soa.error.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

@Component
class CloudinaryRentalPhotos {
    private static final String DELIVERY_TYPE = "authenticated";
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(5);
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final Clock clock;
    private final RestClient http;
    private final String folder;
    private final URI imageApi;
    private final String apiKey;
    private final String apiSecret;

    @Autowired
    CloudinaryRentalPhotos(@Value("${CLOUDINARY_URL:}") String config,
                           @Value("${APP_CLOUDINARY_ASSET_FOLDER:rental-photos}") String folder,
                           Clock clock) {
        this(config, folder, clock, createHttpClient());
    }

    CloudinaryRentalPhotos(String config, String folder, Clock clock, RestClient http) {
        this.clock = clock;
        this.http = http;
        this.folder = folder;
        if (config.isBlank()) {
            imageApi = null;
            apiKey = null;
            apiSecret = null;
            return;
        }

        URI uri;
        try {
            uri = URI.create(config.trim());
        } catch (IllegalArgumentException invalid) {
            throw invalidConfig();
        }
        String authority = uri.getRawAuthority();
        if (!"cloudinary".equals(uri.getScheme()) || authority == null || !uri.getPath().isEmpty()
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw invalidConfig();
        }
        int separator = authority.lastIndexOf('@');
        if (separator < 0) {
            throw invalidConfig();
        }
        String cloudName = authority.substring(separator + 1);
        String[] keys = authority.substring(0, separator).split(":", 2);
        if (!cloudName.matches("[a-zA-Z0-9_-]+") || keys.length != 2 || keys[0].isBlank()
                || keys[1].isBlank() || folder.isBlank()) {
            throw invalidConfig();
        }
        apiKey = UriUtils.decode(keys[0], StandardCharsets.UTF_8);
        apiSecret = UriUtils.decode(keys[1], StandardCharsets.UTF_8);
        imageApi = URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/");
    }

    void upload(String publicId, AddonModels.PhotoContent photo) {
        requireConfigured();
        var parameters = new TreeMap<>(Map.of(
                "asset_folder", folder,
                "overwrite", "false",
                "public_id", publicId,
                "timestamp", Long.toString(clock.instant().getEpochSecond()),
                "type", DELIVERY_TYPE));
        sign(parameters);

        var body = new LinkedMultiValueMap<String, Object>();
        parameters.forEach(body::add);
        var fileHeaders = new HttpHeaders();
        fileHeaders.setContentDispositionFormData("file", "rental-photo." + format(photo.contentType()));
        fileHeaders.setContentType(MediaType.parseMediaType(photo.contentType()));
        body.add("file", new HttpEntity<>(new ByteArrayResource(photo.bytes()), fileHeaders));

        try {
            var result = http.post().uri(imageApi.resolve("upload"))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JsonNode.class);
            if (result == null || !publicId.equals(result.path("public_id").asString())
                    || !DELIVERY_TYPE.equals(result.path("type").asString())
                    || !format(photo.contentType()).equals(result.path("format").asString())) {
                throw storageError("Cloudinary returned an unexpected image reference");
            }
        } catch (RestClientException failure) {
            throw storageError("Cloudinary could not upload the image");
        }
    }

    AddonModels.PhotoLink link(String publicId, String contentType) {
        requireConfigured();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(LINK_LIFETIME);
        var parameters = new TreeMap<>(Map.of(
                "public_id", publicId,
                "format", format(contentType),
                "type", DELIVERY_TYPE,
                "timestamp", Long.toString(now.getEpochSecond()),
                "expires_at", Long.toString(expiresAt.getEpochSecond())));
        sign(parameters);

        String query = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return new AddonModels.PhotoLink(URI.create(imageApi.resolve("download") + "?" + query), expiresAt);
    }

    byte[] download(String publicId, String contentType) {
        var link = link(publicId, contentType);
        try {
            return http.get().uri(link.url()).exchange((request, response) -> {
                var receivedType = response.getHeaders().getContentType();
                if (!response.getStatusCode().is2xxSuccessful() || receivedType == null
                        || !(MediaType.parseMediaType(contentType).isCompatibleWith(receivedType)
                        || MediaType.APPLICATION_OCTET_STREAM.equals(receivedType))) {
                    throw storageError("Cloudinary could not return the image");
                }
                byte[] bytes = response.getBody().readNBytes(MAX_IMAGE_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                    throw storageError("Cloudinary returned an invalid image size");
                }
                return bytes;
            });
        } catch (RestClientException failure) {
            throw storageError("Cloudinary could not download the image");
        }
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private void sign(TreeMap<String, String> parameters) {
        String sorted = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        parameters.put("signature", sha256((sorted + apiSecret).getBytes(StandardCharsets.UTF_8)));
        parameters.put("api_key", apiKey);
    }

    private static String format(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw storageError("Unsupported stored image type");
        };
    }

    private void requireConfigured() {
        if (imageApi == null) {
            throw ApiException.serviceUnavailable("STORAGE_NOT_CONFIGURED",
                    "Rental photo storage is not configured on the server.");
        }
    }

    private static ResponseStatusException storageError(String message) {
        return ApiException.badGateway("UPSTREAM_STORAGE_ERROR", message);
    }

    private static IllegalStateException invalidConfig() {
        return new IllegalStateException("Set CLOUDINARY_URL to cloudinary://<API_KEY>:<API_SECRET>@<CLOUD_NAME>");
    }

    private static RestClient createHttpClient() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }
}
