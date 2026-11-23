package group4.backend.soa.entity;

import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rental_photos", uniqueConstraints = @UniqueConstraint(
        name = "uk_rental_photo_angle", columnNames = {"rental_id", "phase", "angle"}))
@Getter
@Setter
public class RentalPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rental_id", nullable = false)
    private EquipmentRental rental;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RentalPhotoPhase phase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RentalPhotoAngle angle;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Lob
    @Column(columnDefinition = "mediumblob")
    private byte[] image;

    @Column(length = 255)
    private String cloudinaryPublicId;

    @Column(length = 64)
    private String contentSha256;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;
}
