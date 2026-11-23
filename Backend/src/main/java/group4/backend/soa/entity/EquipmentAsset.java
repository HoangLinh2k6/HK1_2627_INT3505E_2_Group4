package group4.backend.soa.entity;

import group4.backend.soa.enums.EquipmentAssetState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "equipment_assets",
       uniqueConstraints = @UniqueConstraint(name = "uk_asset_venue_code", columnNames = {"venue_id", "code"}),
       indexes = @Index(name = "idx_asset_addon_state", columnList = "addon_id, state"))
@Getter
@Setter
public class EquipmentAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private VenueAddon addon;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EquipmentAssetState state;
}
