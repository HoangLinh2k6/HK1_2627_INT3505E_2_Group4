package group4.backend.soa.repository;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CourtRepository extends JpaRepository<Court, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :id")
    Optional<Court> lockById(Long id);

    @EntityGraph(attributePaths = {"venue", "sportType"})
    @Query("""
            select c from Court c join c.venue v where v.id in :venueIds
              and c.status = group4.backend.soa.enums.CourtStatus.AVAILABLE
              and c.basePricePerHour > 0 and (:maxPrice is null or c.basePricePerHour <= :maxPrice)
              and (:sportTypeId is null or c.sportType.id = :sportTypeId)
              and (:keyword is null or lower(c.name) like :keyword escape '!' or
            """ + VenueRepository.VENUE_KEYWORD + ")")
    List<Court> findSearchCourts(Collection<Long> venueIds, Long sportTypeId, BigDecimal maxPrice, String keyword);
    @Override
    @EntityGraph(attributePaths = {"venue", "venue.owner", "sportType"})
    List<Court> findAll();

    @Override
    @EntityGraph(attributePaths = {"venue", "venue.owner", "sportType"})
    Optional<Court> findById(Long id);

    @EntityGraph(attributePaths = {"venue", "venue.owner", "sportType"})
    List<Court> findByVenueId(Long venueId);

    @EntityGraph(attributePaths = {"venue", "venue.owner", "sportType"})
    List<Court> findByVenueIdAndStatus(Long venueId, CourtStatus status);

    @EntityGraph(attributePaths = {"venue", "venue.owner", "sportType"})
    List<Court> findByVenueIdAndSportTypeId(Long venueId, Long sportTypeId);
}
