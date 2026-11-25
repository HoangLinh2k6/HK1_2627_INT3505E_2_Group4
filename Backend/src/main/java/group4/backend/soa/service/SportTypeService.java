package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.SportType;
import group4.backend.soa.error.ApiException;
import group4.backend.soa.repository.SportTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SportTypeService {
    private final SportTypeRepository sportTypeRepository;

    public List<SportType> getAllSportTypes() {
        List<SportType> sportTypes = sportTypeRepository.findAll();
        if (sportTypes.isEmpty()) {
            throw ApiException.notFound("SPORT_TYPES_NOT_FOUND", "No sport types have been configured yet.");
        }
        return sportTypes;
    }

    public SportType getSportTypeById(Long id) {
        return sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Sport type", id));
    }

    public SportType createSportType(SportType sportType) {
        if (sportTypeRepository.existsByName(sportType.getName())) {
            throw ApiException.conflict("SPORT_TYPE_ALREADY_EXISTS",
                    "Sport type '" + sportType.getName() + "' already exists.");
        }
        return sportTypeRepository.save(sportType);
    }

    public SportType updateSportType(Long id, SportType sportType) {
        SportType existingSportType = sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Sport type", id));
        if (!existingSportType.getName().equals(sportType.getName()) && sportTypeRepository.existsByName(sportType.getName())) {
            throw ApiException.conflict("SPORT_TYPE_ALREADY_EXISTS",
                    "Sport type '" + sportType.getName() + "' already exists.");
        }
        existingSportType.setName(sportType.getName());
        return sportTypeRepository.save(existingSportType);
    }

    public void deleteSportType(Long id) {
        SportType sportType = sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("Sport type", id));
        sportTypeRepository.delete(sportType);
    }
}
