package group4.backend.soa.controller;

import group4.backend.soa.service.CourtService;
import group4.backend.soa.entity.Court;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/courts")
@RequiredArgsConstructor
public class CourController {
    private final CourtService courtService;

    

}
