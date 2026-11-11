package group4.backend.soa.repository;

import group4.backend.soa.entity.Booking;
import group4.backend.soa.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserId(Long userId, Pageable pageable);


    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.court.id = :courtId
          AND b.status IN :statuses
          AND b.startTime < :endTime
          AND b.endTime > :startTime
        """)
    boolean existsOverlapping(@Param("courtId") Long courtId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("statuses") Collection<BookingStatus> statuses);

    
    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime time);


    List<Booking> findByCourtIdAndStatusInAndStartTimeBetween(Long courtId,
                                                              Collection<BookingStatus> statuses,
                                                              LocalDateTime from,
                                                              LocalDateTime to);
}