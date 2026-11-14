package group4.backend.soa.service;

import group4.backend.soa.entity.SportType;
import java.util.List;

public interface SportTypeService {
    List<SportType> getAllSportTypes();
    SportType createSportType(SportType sportType);
    SportType updateSportType(Long id, SportType sportType);
    void deleteSportType(Long id);
}
