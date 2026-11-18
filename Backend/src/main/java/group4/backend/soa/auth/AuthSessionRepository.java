package group4.backend.soa.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findByAccessHash(String accessHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshHash(String refreshHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> lockById(@Param("id") Long id);

    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId and s.refreshExpiresAt <= :now")
    void deleteExpiredForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId")
    void revokeForUser(@Param("userId") Long userId);
}
