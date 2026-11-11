package group4.backend.soa.repository;

import group4.backend.soa.entity.SportType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SportTypeRepository extends JpaRepository<SportType, Long> {
    Optional<SportType> findByName(String name);
    boolean existsByName(String name);
}