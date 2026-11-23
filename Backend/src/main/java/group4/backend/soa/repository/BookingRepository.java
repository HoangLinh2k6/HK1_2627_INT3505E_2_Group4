package group4.backend.soa.repository;

import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    String OCCUPIED = """
            (b.status = group4.backend.soa.enums.BookingStatus.CONFIRMED
             or (b.status = group4.backend.soa.enums.BookingStatus.PENDING
                 and b.startTime > :now and (b.expiresAt is null or b.expiresAt > :now)))
            and b.startTime < :end and b.endTime > :start
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(Long id);

    @Query("select b.court.id from Booking b where b.id = :id")
    Optional<Long> findCourtId(Long id);

    @Query("select count(o) > 0 from AddonOrder o where o.booking.id = :id and o.cancelledAt is null")
    boolean hasServiceOrders(Long id);

    @EntityGraph(attributePaths = {"user", "court", "court.venue", "court.venue.owner", "court.sportType"})
    Optional<Booking> findByUserIdAndRequestKey(Long userId, String requestKey);

    // Eagerly fetch many-to-one associations to prevent Hibernate N+1 queries during serialization
    @Override
    @EntityGraph(attributePaths = {"user", "court", "court.venue", "court.venue.owner", "court.sportType"})
    Page<Booking> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "court", "court.venue", "court.venue.owner", "court.sportType"})
    Optional<Booking> findById(Long id);

    @EntityGraph(attributePaths = {"user", "court", "court.venue", "court.venue.owner", "court.sportType"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "court", "court.venue", "court.venue.owner", "court.sportType"})
    Page<Booking> findByCourtVenueId(Long venueId, Pageable pageable);


    @Query("select count(b) > 0 from Booking b where b.court.id = :courtId and " + OCCUPIED)
    boolean existsOccupied(Long courtId, LocalDateTime start, LocalDateTime end, LocalDateTime now);

    @Query("select b.court.id as courtId, b.startTime as startTime, b.endTime as endTime "
            + "from Booking b where b.court.id in :courtIds and " + OCCUPIED)
    List<OccupiedInterval> findOccupied(Collection<Long> courtIds, LocalDateTime start,
                                         LocalDateTime end, LocalDateTime now);

    @Modifying
    @Query("""
            update Booking b set b.status = :expired where b.status = :pending
              and (b.expiresAt <= :now or b.startTime <= :now)
            """)
    int expireHolds(BookingStatus pending, BookingStatus expired, LocalDateTime now);

    interface OccupiedInterval {
        Long getCourtId();
        LocalDateTime getStartTime();
        LocalDateTime getEndTime();
    }
}
