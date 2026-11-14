package group4.backend.soa.service.impl;

import group4.backend.soa.service.SportTypeService;
import group4.backend.soa.entity.SportType;
import group4.backend.soa.repository.SportTypeRepository;
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sport types found");
        }
        return sportTypes;
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
        SportType existingSportType = sportTypeRepository.findById(id).orElse(null);
        if (existingSportType == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sport type not found");
        }
        if (!existingSportType.getName().equals(sportType.getName()) && sportTypeRepository.existsByName(sportType.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport type with this name already exists");
        }
        existingSportType.setName(sportType.getName());
        return sportTypeRepository.save(existingSportType);
    }

    @Override
    public void deleteSportType(Long id) {
        sportTypeRepository.deleteById(id);
        if (sportTypeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete sport type");
        }
    }
    
}
