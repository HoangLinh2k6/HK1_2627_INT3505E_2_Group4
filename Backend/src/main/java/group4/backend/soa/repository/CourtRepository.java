package group4.backend.soa.repository;

import group4.backend.soa.entity.Court;
import group4.backend.soa.enums.CourtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourtRepository extends JpaRepository<Court, Long> {
    List<Court> findByVenueId(Long venueId);
    List<Court> findByVenueIdAndStatus(Long venueId, CourtStatus status);
    List<Court> findByVenueIdAndSportTypeId(Long venueId, Long sportTypeId);
}