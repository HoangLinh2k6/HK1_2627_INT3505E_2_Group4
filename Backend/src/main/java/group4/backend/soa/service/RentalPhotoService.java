package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.EquipmentRental;
import group4.backend.soa.entity.RentalPhoto;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.repository.AddonStore;
import group4.backend.soa.repository.BookingRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static group4.backend.soa.dto.AddonModels.*;

@Service
@RequiredArgsConstructor
public class RentalPhotoService {
    private static final long MAX_PIXELS = 20_000_000;
    private final AddonStore store;
    private final BookingRepository bookings;
    private final AccessRules access;
    private final CourtSchedule schedule;
    private final CloudinaryRentalPhotos cloudinary;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PhotoView upload(Long rentalId, RentalPhotoPhase phase, RentalPhotoAngle angle, MultipartFile file) {
        Long bookingId = store.bookingIdForRental(rentalId);
        var booking = lockBooking(bookingId);
        access.requireCustomerBooking(booking);
        var rental = store.require(EquipmentRental.class, rentalId, "Equipment rental");
        if (booking.getStatus() != BookingStatus.CONFIRMED || rental.getHandedOutAt() == null
                || rental.getReturnedAt() != null) {
            throw conflict("Photos require equipment that has been handed out and is due for return");
        }

        if (phase == RentalPhotoPhase.CHECK_IN && count(rentalId, RentalPhotoPhase.CHECK_OUT) > 0) {
            throw conflict("Check-in photos cannot change after return photos have been started");
        }
        if (phase == RentalPhotoPhase.CHECK_OUT && count(rentalId, RentalPhotoPhase.CHECK_IN) < RentalPhotoAngle.values().length) {
            throw conflict("Both check-in angles must be uploaded first");
        }

        var content = readImage(file);
        String digest = CloudinaryRentalPhotos.sha256(content.bytes());
        var current = store.photo(rentalId, phase, angle);
        if (current.isPresent() && current.get().getCloudinaryPublicId() != null
                && digest.equals(current.get().getContentSha256())) {
            return view(current.get());
        }
        var photo = current.orElseGet(RentalPhoto::new);
        photo.setRental(rental);
        photo.setPhase(phase);
        photo.setAngle(angle);
        photo.setUploadedAt(schedule.now());
        storeInCloud(photo, content, digest, false);
        return view(current.isEmpty() ? store.insert(photo) : photo);
    }

    @Transactional(readOnly = true)
    public List<PhotoView> list(Long rentalId) {
        requireAccess(rentalId);
        return store.photos(rentalId);
    }

    @Transactional(readOnly = true)
    public PhotoContent download(Long rentalId, Long photoId) {
        var photo = accessiblePhoto(rentalId, photoId);
        if (photo.getCloudinaryPublicId() != null) {
            byte[] bytes = cloudinary.download(photo.getCloudinaryPublicId(), photo.getContentType());
            verifyDigest(photo.getContentSha256(), bytes);
            return new PhotoContent(photo.getContentType(), bytes);
        }
        return new PhotoContent(photo.getContentType(), photo.getImage());
    }

    @Transactional(readOnly = true)
    public PhotoLink link(Long rentalId, Long photoId) {
        var photo = accessiblePhoto(rentalId, photoId);
        if (photo.getCloudinaryPublicId() == null) {
            throw conflict("The legacy image is available through the original download endpoint");
        }
        return cloudinary.link(photo.getCloudinaryPublicId(), photo.getContentType());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean migrateLegacyPhoto(Long photoId) {
        lockBooking(store.bookingIdForPhoto(photoId));
        var photo = store.require(RentalPhoto.class, photoId, "Rental photo");
        if (photo.getImage() == null) {
            return false;
        }
        var content = new PhotoContent(photo.getContentType(), photo.getImage());
        storeInCloud(photo, content, CloudinaryRentalPhotos.sha256(content.bytes()), true);
        return true;
    }

    private void storeInCloud(RentalPhoto photo, PhotoContent content, String digest, boolean verifyUpload) {
        // An immutable ID lets retries reuse an uploaded file and preserves the previous image on rollback.
        String publicId = "%s-%s-%s-%s".formatted(photo.getRental().getId(), photo.getPhase(), photo.getAngle(), digest);
        cloudinary.upload(publicId, content);
        if (verifyUpload) {
            verifyDigest(digest, cloudinary.download(publicId, content.contentType()));
        }
        photo.setContentType(content.contentType());
        photo.setCloudinaryPublicId(publicId);
        photo.setContentSha256(digest);
        photo.setImage(null);
    }

    private static void verifyDigest(String digest, byte[] bytes) {
        if (!CloudinaryRentalPhotos.sha256(bytes).equals(digest)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudinary returned different image data");
        }
    }

    private RentalPhoto accessiblePhoto(Long rentalId, Long photoId) {
        requireAccess(rentalId);
        return store.photoById(rentalId, photoId).orElseThrow(() -> AccessRules.notFound("Rental photo"));
    }

    private Booking lockBooking(Long id) {
        return bookings.lockById(id).orElseThrow(() -> AccessRules.notFound("Booking"));
    }

    private void requireAccess(Long rentalId) {
        var booking = store.require(Booking.class, store.bookingIdForRental(rentalId), "Booking");
        access.requireBookingAccess(booking);
    }

    private long count(Long rentalId, RentalPhotoPhase phase) {
        return store.photoCounts(List.of(rentalId), phase).getOrDefault(rentalId, 0L);
    }

    private static PhotoContent readImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > CloudinaryRentalPhotos.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be between 1 byte and 5 MiB");
        }

        try {
            byte[] bytes = file.getBytes();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        String type = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (type.equals("jpeg") || type.equals("png")) {
                            reader.setInput(input, true, true);
                            if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image exceeds 20 megapixels");
                            }
                            reader.read(0).flush();
                            return new PhotoContent("image/" + type, bytes);
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image could not be decoded", failure);
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a JPEG or PNG image");
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }

    private static PhotoView view(RentalPhoto photo) {
        return new PhotoView(photo.getId(), photo.getPhase(), photo.getAngle(), photo.getUploadedAt());
    }
}
