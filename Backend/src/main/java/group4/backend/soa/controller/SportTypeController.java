package group4.backend.soa.controller;

import group4.backend.soa.service.SportTypeService;
import group4.backend.soa.entity.SportType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;


@RestController
@RequestMapping("/sport-types")
@RequiredArgsConstructor
public class SportTypeController {
    private final SportTypeService sportTypeService;

    @GetMapping
    public ResponseEntity<List<SportType>> getAll() {
        return ResponseEntity.ok(sportTypeService.getAllSportTypes());
    }

    @PostMapping
    public ResponseEntity<SportType> create(@RequestBody SportType sportType) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sportTypeService.createSportType(sportType));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SportType> update(@PathVariable Long id, @RequestBody SportType sportType) {
        return ResponseEntity.ok(sportTypeService.updateSportType(id, sportType));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sportTypeService.deleteSportType(id);
        return ResponseEntity.noContent().build();
    }
}
