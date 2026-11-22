package group4.backend.soa.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import group4.backend.soa.enums.CourtStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "courts")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
public class Court {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_type_id", nullable = false)
    private SportType sportType;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_price_per_hour", precision = 12, scale = 2)
    private BigDecimal basePricePerHour;

    @Enumerated(EnumType.STRING)
    private CourtStatus status;
}
