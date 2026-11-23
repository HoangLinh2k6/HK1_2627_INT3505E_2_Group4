package group4.backend.soa.entity;

import group4.backend.soa.enums.AddonKind;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "venue_addons", indexes = @Index(name = "idx_addon_venue", columnList = "venue_id"))
@Getter
@Setter
public class VenueAddon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AddonKind kind;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private Integer stockOnHand;

    @Column(nullable = false)
    private boolean active;
}
