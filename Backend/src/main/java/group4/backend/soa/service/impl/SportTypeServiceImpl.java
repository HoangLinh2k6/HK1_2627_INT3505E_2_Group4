package group4.backend.soa.service.impl;

import group4.backend.soa.service.SportTypeService;
import group4.backend.soa.entity.SportType;
import group4.backend.soa.repository.SportTypeRepository;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SportTypeServiceImpl implements SportTypeService {
    private final SportTypeRepository sportTypeRepository;

    @Override
    public List<SportType> getAllSportTypes() {
        List<SportType> sportTypes = sportTypeRepository.findAll();
        if (sportTypes.isEmpty()) {
            throw AccessRules.notFound("SportType");
        }
        return sportTypes;
    }

    @Override
    public SportType getSportTypeById(Long id) {
        return sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("SportType"));
    }

    @Override
    public SportType createSportType(SportType sportType) {
        if (sportTypeRepository.existsByName(sportType.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport type with this name already exists");
        }
        return sportTypeRepository.save(sportType);
    }

    @Override
    public SportType updateSportType(Long id, SportType sportType) {
        SportType existingSportType = sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("SportType"));
        if (!existingSportType.getName().equals(sportType.getName()) && sportTypeRepository.existsByName(sportType.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport type with this name already exists");
        }
        existingSportType.setName(sportType.getName());
        return sportTypeRepository.save(existingSportType);
    }

    @Override
    public void deleteSportType(Long id) {
        SportType sportType = sportTypeRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("SportType"));
        sportTypeRepository.delete(sportType);
    }
    
}
