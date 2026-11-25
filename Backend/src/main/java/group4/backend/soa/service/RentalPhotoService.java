package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.EquipmentRental;
import group4.backend.soa.entity.RentalPhoto;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.error.ApiErrorResponse.FieldError;
import group4.backend.soa.error.ApiException;
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
            throw ApiException.conflict("RENTAL_PHOTO_NOT_ALLOWED",
                    "Rental #" + rentalId
                            + " cannot accept photos unless the booking is CONFIRMED and the equipment has been handed out and not yet returned.");
        }

        if (phase == RentalPhotoPhase.CHECK_IN && count(rentalId, RentalPhotoPhase.CHECK_OUT) > 0) {
            throw ApiException.conflict("CHECK_IN_PHOTOS_LOCKED",
                    "CHECK_IN photos for rental #" + rentalId
                            + " cannot be modified after CHECK_OUT photos have already been uploaded.");
        }
        if (phase == RentalPhotoPhase.CHECK_OUT && count(rentalId, RentalPhotoPhase.CHECK_IN) < RentalPhotoAngle.values().length) {
            throw ApiException.conflict("CHECK_IN_PHOTOS_REQUIRED",
                    "Both CHECK_IN photo angles (OVERVIEW and DETAIL) must be uploaded for rental #" + rentalId
                            + " before uploading CHECK_OUT photos.");
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
            throw ApiException.conflict("LEGACY_PHOTO_LINK_UNAVAILABLE",
                    "Signed links are not available for legacy database-stored photo #" + photoId
                            + ". Download it via GET /rentals/" + rentalId + "/photos/" + photoId + ".");
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
            throw ApiException.badGateway("UPSTREAM_STORAGE_ERROR",
                    "Image storage provider returned data whose integrity checksum does not match.");
        }
    }

    private RentalPhoto accessiblePhoto(Long rentalId, Long photoId) {
        requireAccess(rentalId);
        return store.photoById(rentalId, photoId).orElseThrow(() -> AccessRules.notFound("Rental photo", photoId));
    }

    private Booking lockBooking(Long id) {
        return bookings.lockById(id).orElseThrow(() -> AccessRules.notFound("Booking", id));
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
            String message = "Image file size (" + file.getSize()
                    + " bytes) must be between 1 byte and 5 MiB (5,242,880 bytes).";
            throw ApiException.badRequest("INVALID_IMAGE_SIZE", message,
                    FieldError.of("file", "INVALID_IMAGE_SIZE", message));
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
                            int width = reader.getWidth(0);
                            int height = reader.getHeight(0);
                            if ((long) width * height > MAX_PIXELS) {
                                String message = "Image dimensions (" + width + "x" + height
                                        + ") exceed the maximum allowed 20 megapixels.";
                                throw ApiException.badRequest("IMAGE_DIMENSIONS_TOO_LARGE", message,
                                        FieldError.of("file", "IMAGE_DIMENSIONS_TOO_LARGE", message));
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_DATA",
                    "Uploaded image file is corrupted or could not be decoded.",
                    List.of(FieldError.of("file", "INVALID_IMAGE_DATA", "Image file could not be decoded.")),
                    failure);
        }
        throw ApiException.unsupportedMediaType("UNSUPPORTED_IMAGE_TYPE",
                "Unsupported image format. Only valid JPEG and PNG image files are accepted.");
    }

    private static PhotoView view(RentalPhoto photo) {
        return new PhotoView(photo.getId(), photo.getPhase(), photo.getAngle(), photo.getUploadedAt());
    }
}
