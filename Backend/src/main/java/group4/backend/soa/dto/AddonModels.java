package group4.backend.soa.dto;

import group4.backend.soa.enums.AddonKind;
import group4.backend.soa.enums.EquipmentAssetState;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class AddonModels {
    private AddonModels() {}

    public record CreateService(@NotBlank @Size(max = 120) String name, @NotNull AddonKind kind,
                                @NotNull @Min(0) @Max(100_000_000) Long unitPrice,
                                @Min(0) @Max(1_000_000) Integer stockOnHand) {}

    public record UpdateService(@NotBlank @Size(max = 120) String name,
                                @NotNull @Min(0) @Max(100_000_000) Long unitPrice,
                                @NotNull Boolean active, @Min(0) @Max(1_000_000) Integer stockOnHand) {}

    public record CreateAsset(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,50}") String code) {}

    public record UpdateAsset(@NotNull EquipmentAssetState state) {}

    public record OrderRequest(@NotNull @Positive Long serviceId, @NotNull @Min(1) @Max(100) Integer quantity) {}

    public record DeliverRequest(@Size(max = 100) List<@NotNull @Positive Long> assetIds) {}

    public record ServiceView(Long id, String name, AddonKind kind, BigDecimal unitPrice, boolean active,
                              Integer stockOnHand, Long availableStock) {}

    public record AssetView(Long id, Long serviceId, String code, EquipmentAssetState state) {}

    public record RentalView(Long id, Long assetId, String assetCode, LocalDateTime handedOutAt,
                             LocalDateTime returnedAt, Long returnedBy) {}

    public record OrderView(Long id, Long serviceId, String itemName, AddonKind kind, int quantity,
                            BigDecimal unitPrice, BigDecimal total, String status, LocalDateTime orderedAt,
                            LocalDateTime deliveredAt, Long deliveredBy, LocalDateTime cancelledAt,
                            List<RentalView> rentals) {}

    public record BookingServices(Long bookingId, LocalDateTime checkedInAt, LocalDateTime checkedOutAt,
                                  BigDecimal courtTotal, BigDecimal deliveredServicesTotal, BigDecimal total,
                                  List<OrderView> orders) {}

    public record PhotoView(Long id, RentalPhotoPhase phase, RentalPhotoAngle angle, LocalDateTime uploadedAt) {}

    public record PhotoContent(String contentType, byte[] bytes) {}

    public record PhotoLink(URI url, Instant expiresAt) {}
}
