package group4.backend.soa.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "addon_orders",
       uniqueConstraints = @UniqueConstraint(name = "uk_addon_order_request", columnNames = {"booking_id", "request_key"}),
       indexes = @Index(name = "idx_addon_order_addon", columnList = "addon_id"))
@Getter
@Setter
public class AddonOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private VenueAddon addon;

    @Column(name = "request_key", nullable = false, length = 64,
            columnDefinition = "varchar(64) character set ascii collate ascii_bin")
    private String requestKey;

    @Column(nullable = false, length = 120)
    private String itemName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime cancelledAt;
    private LocalDateTime deliveredAt;
    private Long deliveredBy;

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
