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

    public record CreateService(
            @NotBlank(message = "Service name is required.")
            @Size(max = 120, message = "Service name must not exceed 120 characters.")
            String name,
            @NotNull(message = "Service kind is required (CONSUMABLE or RENTAL).")
            AddonKind kind,
            @NotNull(message = "unitPrice is required.")
            @Min(value = 0, message = "unitPrice must be at least 0 VND.")
            @Max(value = 100_000_000, message = "unitPrice must not exceed 100,000,000 VND.")
            Long unitPrice,
            @Min(value = 0, message = "stockOnHand must be at least 0.")
            @Max(value = 1_000_000, message = "stockOnHand must not exceed 1,000,000.")
            Integer stockOnHand) {}

    public record UpdateService(
            @NotBlank(message = "Service name is required.")
            @Size(max = 120, message = "Service name must not exceed 120 characters.")
            String name,
            @NotNull(message = "unitPrice is required.")
            @Min(value = 0, message = "unitPrice must be at least 0 VND.")
            @Max(value = 100_000_000, message = "unitPrice must not exceed 100,000,000 VND.")
            Long unitPrice,
            @NotNull(message = "active flag is required.")
            Boolean active,
            @Min(value = 0, message = "stockOnHand must be at least 0.")
            @Max(value = 1_000_000, message = "stockOnHand must not exceed 1,000,000.")
            Integer stockOnHand) {}

    public record CreateAsset(
            @NotBlank(message = "Asset code is required.")
            @Pattern(regexp = "[A-Za-z0-9._-]{1,50}",
                    message = "Asset code must be 1 to 50 characters using letters, digits, '.', '_', or '-'.")
            String code) {}

    public record UpdateAsset(
            @NotNull(message = "Equipment state is required (AVAILABLE, MAINTENANCE, or RETIRED).")
            EquipmentAssetState state) {}

    public record OrderRequest(
            @NotNull(message = "serviceId is required.")
            @Positive(message = "serviceId must be a positive integer.")
            Long serviceId,
            @NotNull(message = "quantity is required.")
            @Min(value = 1, message = "quantity must be at least 1.")
            @Max(value = 100, message = "quantity must not exceed 100.")
            Integer quantity) {}

    public record DeliverRequest(
            @Size(max = 100, message = "assetIds must not contain more than 100 items.")
            List<@NotNull(message = "Each asset ID must not be null.")
                 @Positive(message = "Each asset ID must be a positive integer.") Long> assetIds) {}

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
