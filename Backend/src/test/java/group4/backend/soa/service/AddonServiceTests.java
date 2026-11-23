package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.AddonOrder;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.EquipmentAsset;
import group4.backend.soa.entity.EquipmentRental;
import group4.backend.soa.entity.RentalPhoto;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.entity.VenueAddon;
import group4.backend.soa.enums.AddonKind;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.repository.AddonStore;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.VenueRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static group4.backend.soa.dto.AddonModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AddonServiceTests {
    private final AddonStore store = mock(AddonStore.class);
    private final VenueRepository venues = mock(VenueRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final AccessRules access = mock(AccessRules.class);
    private final CourtSchedule schedule = new CourtSchedule(
            Clock.fixed(Instant.parse("2030-01-02T10:00:00Z"), ZoneOffset.UTC));
    private final AddonService addons = new AddonService(store, venues, bookings, access, schedule);
    private final CloudinaryRentalPhotos cloudinary = mock(CloudinaryRentalPhotos.class);
    private final RentalPhotoService photos = new RentalPhotoService(store, bookings, access, schedule, cloudinary);
    private final Venue venue = new Venue();
    private final Booking booking = new Booking();

    @BeforeEach
    void setup() {
        venue.setId(2L);
        var court = new Court();
        court.setVenue(venue);
        booking.setId(1L);
        booking.setCourt(court);
        booking.setStartTime(schedule.now().minusMinutes(10));
        booking.setEndTime(schedule.now().plusMinutes(20));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckedInAt(booking.getStartTime());
        booking.setTotalPrice(BigDecimal.valueOf(100_000));
        when(bookings.lockById(1L)).thenReturn(Optional.of(booking));
        when(venues.lockById(2L)).thenReturn(Optional.of(venue));
    }

    @Test
    void consumablesUseAvailableStockAndKeepPriceWithoutChangingPhysicalStock() {
        var water = new VenueAddon();
        water.setId(3L);
        water.setVenue(venue);
        water.setName("Nước");
        water.setUnitPrice(BigDecimal.valueOf(10_000));
        water.setStockOnHand(3);
        water.setKind(AddonKind.CONSUMABLE);
        water.setActive(true);
        when(store.require(VenueAddon.class, 3L, "Venue service")).thenReturn(water);
        when(store.reservedStock(List.of(3L), schedule.now())).thenReturn(Map.of(3L, 2L));
        assertThatThrownBy(() -> addons.reserve(1L, "water", new OrderRequest(3L, 2)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        var result = addons.reserve(1L, "water", new OrderRequest(3L, 1));
        assertThat(result.total()).isEqualByComparingTo("10000");
        assertThat(result.rentals()).isEmpty();
        assertThat(water.getStockOnHand()).isEqualTo(3);
    }

    @Test
    void checkoutRequiresBothPhotoPairsAndReplaysTheFinalTotal() {
        var rental = rental();
        when(store.rentals(1L)).thenReturn(List.of(rental));
        when(store.orders(1L)).thenReturn(List.of(rental.getAddonOrder()));
        when(store.photoCounts(List.of(4L), RentalPhotoPhase.CHECK_IN)).thenReturn(Map.of(4L, 2L));
        when(store.photoCounts(List.of(4L), RentalPhotoPhase.CHECK_OUT)).thenReturn(Map.of(4L, 1L));
        assertThatThrownBy(() -> addons.checkOut(1L)).isInstanceOf(ResponseStatusException.class);
        assertThat(booking.getCheckedOutAt()).isNull();
        assertThat(rental.getReturnedAt()).isNull();

        when(store.photoCounts(List.of(4L), RentalPhotoPhase.CHECK_OUT)).thenReturn(Map.of(4L, 2L));
        when(access.userId()).thenReturn(8L);
        var result = addons.checkOut(1L);
        assertThat(result.total()).isEqualByComparingTo("120000");
        assertThat(rental.getReturnedBy()).isEqualTo(8L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(addons.checkOut(1L)).isEqualTo(result);
    }

    @Test
    void uploadDetectsRealImageTypeAndBlocksChangingTheCheckInPair() throws Exception {
        var rental = rental();
        when(store.bookingIdForRental(4L)).thenReturn(1L);
        when(store.require(EquipmentRental.class, 4L, "Equipment rental")).thenReturn(rental);
        when(store.insert(any(RentalPhoto.class))).thenAnswer(invocation -> {
            RentalPhoto photo = invocation.getArgument(0);
            photo.setId(1L);
            assertThat(photo.getContentType()).isEqualTo("image/png");
            assertThat(photo.getCloudinaryPublicId()).startsWith("4-CHECK_IN-DETAIL-");
            assertThat(photo.getImage()).isNull();
            return photo;
        });
        var fake = new MockMultipartFile("file", "rental.png", "image/png", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> photos.upload(4L, RentalPhotoPhase.CHECK_IN, RentalPhotoAngle.DETAIL, fake))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "rental.png", "application/octet-stream", bytes.toByteArray());
        assertThat(photos.upload(4L, RentalPhotoPhase.CHECK_IN, RentalPhotoAngle.DETAIL, file).id()).isEqualTo(1L);
        verify(cloudinary).upload(startsWith("4-CHECK_IN-DETAIL-"), any(PhotoContent.class));

        when(store.photoCounts(List.of(4L), RentalPhotoPhase.CHECK_OUT)).thenReturn(Map.of(4L, 1L));
        assertThatThrownBy(() -> photos.upload(4L, RentalPhotoPhase.CHECK_IN, RentalPhotoAngle.DETAIL, file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void legacyPhotoStaysReadableUntilTheCloudCopyMatches() {
        var photo = new RentalPhoto();
        byte[] original = {1, 2, 3};
        photo.setRental(rental());
        photo.setPhase(RentalPhotoPhase.CHECK_IN);
        photo.setAngle(RentalPhotoAngle.OVERVIEW);
        photo.setContentType("image/png");
        photo.setImage(original);
        when(store.bookingIdForRental(4L)).thenReturn(1L);
        when(store.require(Booking.class, 1L, "Booking")).thenReturn(booking);
        when(store.photoById(4L, 9L)).thenReturn(Optional.of(photo));
        when(store.bookingIdForPhoto(9L)).thenReturn(1L);
        when(store.require(RentalPhoto.class, 9L, "Rental photo")).thenReturn(photo);
        assertThat(photos.download(4L, 9L).bytes()).containsExactly(original);

        when(cloudinary.download(anyString(), eq("image/png"))).thenReturn(new byte[] {4}, original);
        assertThatThrownBy(() -> photos.migrateLegacyPhoto(9L)).isInstanceOf(ResponseStatusException.class);
        assertThat(photo.getCloudinaryPublicId()).isNull();
        assertThat(photo.getImage()).containsExactly(original);

        assertThat(photos.migrateLegacyPhoto(9L)).isTrue();
        assertThat(photo.getImage()).isNull();
        assertThat(photos.download(4L, 9L).bytes()).containsExactly(original);
        assertThat(photos.migrateLegacyPhoto(9L)).isFalse();
    }

    private EquipmentRental rental() {
        var addon = new VenueAddon();
        addon.setId(6L);
        addon.setKind(AddonKind.RENTAL);
        var order = new AddonOrder();
        order.setId(5L);
        order.setBooking(booking);
        order.setAddon(addon);
        order.setItemName("Vợt");
        order.setQuantity(1);
        order.setUnitPrice(BigDecimal.valueOf(20_000));
        order.setDeliveredAt(booking.getStartTime());
        var asset = new EquipmentAsset();
        asset.setId(7L);
        asset.setCode("VOT-007");
        var rental = new EquipmentRental();
        rental.setId(4L);
        rental.setAsset(asset);
        rental.setAddonOrder(order);
        rental.setHandedOutAt(booking.getStartTime());
        return rental;
    }
}
