package group4.backend.soa.repository;

import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByOwnerId(Long ownerId);
    Page<Venue> findByCityAndStatus(String city, VenueStatus status, Pageable pageable);
    Page<Venue> findByCityAndDistrictAndStatus(String city, String district,
                                               VenueStatus status, Pageable pageable);
}