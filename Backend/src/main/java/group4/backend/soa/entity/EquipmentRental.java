package group4.backend.soa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "equipment_rentals",
       uniqueConstraints = @UniqueConstraint(name = "uk_rental_order_asset", columnNames = {"order_id", "asset_id"}),
       indexes = @Index(name = "idx_rental_asset_return", columnList = "asset_id, returned_at"))
@Getter
@Setter
public class EquipmentRental {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private AddonOrder addonOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private EquipmentAsset asset;

    private LocalDateTime handedOutAt;
    private LocalDateTime returnedAt;
    private Long returnedBy;
}
