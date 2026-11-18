package group4.backend.soa.auth;

import group4.backend.soa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_session_user", columnList = "user_id"))
@Getter
@Setter
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 64, nullable = false, unique = true)
    private String accessHash;

    @Column(length = 64, nullable = false, unique = true)
    private String refreshHash;

    @Column(nullable = false)
    private Instant accessExpiresAt;

    @Column(nullable = false)
    private Instant refreshExpiresAt;
}
