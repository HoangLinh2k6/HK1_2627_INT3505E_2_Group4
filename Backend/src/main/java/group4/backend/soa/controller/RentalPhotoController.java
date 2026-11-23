package group4.backend.soa.controller;

import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.service.RentalPhotoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static group4.backend.soa.dto.AddonModels.*;

@RestController
@RequestMapping("/rentals/{rentalId}/photos")
@RequiredArgsConstructor
public class RentalPhotoController {
    private final RentalPhotoService photos;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PhotoView upload(@PathVariable Long rentalId, @RequestParam RentalPhotoPhase phase,
                     @RequestParam RentalPhotoAngle angle, @RequestParam MultipartFile file) {
        return photos.upload(rentalId, phase, angle, file);
    }

    @GetMapping
    List<PhotoView> list(@PathVariable Long rentalId) {
        return photos.list(rentalId);
    }

    @GetMapping("/{photoId}")
    ResponseEntity<byte[]> download(@PathVariable Long rentalId, @PathVariable Long photoId) {
        var photo = photos.download(rentalId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(photo.bytes());
    }

    @GetMapping("/{photoId}/link")
    ResponseEntity<PhotoLink> link(@PathVariable Long rentalId, @PathVariable Long photoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(photos.link(rentalId, photoId));
    }
}
