package group4.backend.soa.repository;

import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    @Override
    @EntityGraph(attributePaths = {"owner"})
    Page<Venue> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Venue> findByStatus(VenueStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"owner"})
    Optional<Venue> findById(Long id);

    @EntityGraph(attributePaths = {"owner"})
    List<Venue> findByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"owner"})
    Page<Venue> findByCityAndStatus(String city, VenueStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Venue> findByCityAndDistrictAndStatus(String city, String district,
                                               VenueStatus status, Pageable pageable);
}
