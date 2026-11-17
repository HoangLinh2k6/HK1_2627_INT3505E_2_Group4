package group4.backend.soa.repository;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourtRepository extends JpaRepository<Court, Long> {
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
