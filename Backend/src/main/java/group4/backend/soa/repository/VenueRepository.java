package group4.backend.soa.repository;

import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.VenueStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venue v where v.id = :id")
    Optional<Venue> lockById(Long id);

    String VENUE_KEYWORD = """
            (lower(v.name) like :keyword escape '!' or lower(v.address) like :keyword escape '!'
             or lower(v.city) like :keyword escape '!' or lower(v.district) like :keyword escape '!')
            """;

    String SEARCH_CANDIDATES = """
            from Venue v where v.status = group4.backend.soa.enums.VenueStatus.ACTIVE
              and (:city is null or lower(v.city) = :city)
              and (:district is null or lower(v.district) = :district)
              and (:keyword is null or
            """ + VENUE_KEYWORD + """
              or exists (select c.id from Court c where c.venue = v
                  and c.status = group4.backend.soa.enums.CourtStatus.AVAILABLE
                  and lower(c.name) like :keyword escape '!'))
            """;

    @EntityGraph(attributePaths = "owner")
    @Query("select v " + SEARCH_CANDIDATES)
    Slice<Venue> searchCandidates(String keyword, String city, String district, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query(value = "select v " + SEARCH_CANDIDATES, countQuery = "select count(v) " + SEARCH_CANDIDATES)
    Page<Venue> searchCatalog(String keyword, String city, String district, Pageable pageable);

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
