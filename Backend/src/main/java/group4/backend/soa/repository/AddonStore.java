package group4.backend.soa.repository;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.dto.AddonModels;
import group4.backend.soa.entity.AddonOrder;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.EquipmentAsset;
import group4.backend.soa.entity.EquipmentRental;
import group4.backend.soa.entity.RentalPhoto;
import group4.backend.soa.entity.VenueAddon;
import group4.backend.soa.enums.EquipmentAssetState;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AddonStore {
    private static final String ACTIVE_BOOKING = """
            (b.endTime > :now and (b.status = group4.backend.soa.enums.BookingStatus.CONFIRMED
               or (b.status = group4.backend.soa.enums.BookingStatus.PENDING and b.startTime > :now
                   and (b.expiresAt is null or b.expiresAt > :now))))
            """;
    private final EntityManager entities;

    public <T> T require(Class<T> type, Long id, String resource) {
        var entity = entities.find(type, id);
        if (entity == null) {
            throw AccessRules.notFound(resource);
        }
        return entity;
    }

    public <T> T insert(T value) {
        entities.persist(value);
        return value;
    }

    public List<VenueAddon> catalog(Long venueId, boolean owner) {
        return entities.createQuery("""
                select a from VenueAddon a where a.venue.id = :venueId and (:owner = true or a.active = true)
                order by a.id
                """, VenueAddon.class).setParameter("venueId", venueId).setParameter("owner", owner).getResultList();
    }

    public Map<Long, Long> reservedStock(Collection<Long> addonIds, LocalDateTime now) {
        if (addonIds.isEmpty()) {
            return Map.of();
        }
        return entities.createQuery("""
                select o.addon.id as addonId, sum(o.quantity) as quantity from AddonOrder o join o.booking b
                where o.addon.id in :addonIds and o.cancelledAt is null and o.deliveredAt is null and
                """ + ACTIVE_BOOKING + " group by o.addon.id", Tuple.class)
                .setParameter("addonIds", addonIds).setParameter("now", now).getResultList().stream()
                .collect(Collectors.toMap(row -> row.get("addonId", Long.class), row -> row.get("quantity", Long.class)));
    }

    public List<EquipmentAsset> assets(Long addonId) {
        return entities.createQuery("select a from EquipmentAsset a where a.addon.id = :id order by a.id",
                EquipmentAsset.class).setParameter("id", addonId).getResultList();
    }

    public boolean assetCodeExists(Long venueId, String code) {
        return entities.createQuery("""
                select count(a) > 0 from EquipmentAsset a where a.venue.id = :venueId and a.code = :code
                """, Boolean.class).setParameter("venueId", venueId).setParameter("code", code).getSingleResult();
    }

    public List<EquipmentAsset> availableAssets(Long addonId, Booking booking, LocalDateTime now, int quantity) {
        return entities.createQuery("""
                select a from EquipmentAsset a where a.addon.id = :addonId
                  and a.state = :available
                  and not exists (select r.id from EquipmentRental r join r.addonOrder o join o.booking b
                    where r.asset = a and o.cancelledAt is null and r.returnedAt is null
                      and ((r.handedOutAt is not null and b.endTime <= :now) or (
                """ + ACTIVE_BOOKING + " and b.startTime < :end and b.endTime > :start))) order by a.id",
                EquipmentAsset.class).setParameter("addonId", addonId).setParameter("now", now)
                .setParameter("available", EquipmentAssetState.AVAILABLE)
                .setParameter("start", booking.getStartTime()).setParameter("end", booking.getEndTime())
                .setMaxResults(quantity).getResultList();
    }

    public boolean assetsInUse(Collection<Long> assetIds) {
        return !assetIds.isEmpty() && entities.createQuery("""
                select count(r) > 0 from EquipmentRental r where r.asset.id in :assetIds
                  and r.handedOutAt is not null and r.returnedAt is null
                """, Boolean.class).setParameter("assetIds", assetIds).getSingleResult();
    }

    public List<AddonOrder> orders(Long bookingId) {
        return entities.createQuery("""
                select o from AddonOrder o join fetch o.addon where o.booking.id = :id order by o.id
                """, AddonOrder.class).setParameter("id", bookingId).getResultList();
    }

    public Optional<AddonOrder> orderByKey(Long bookingId, String key) {
        return entities.createQuery("""
                select o from AddonOrder o where o.booking.id = :id and o.requestKey = :key
                """, AddonOrder.class).setParameter("id", bookingId).setParameter("key", key)
                .getResultList().stream().findFirst();
    }

    public List<EquipmentRental> rentals(Long bookingId) {
        return entities.createQuery("""
                select r from EquipmentRental r join fetch r.asset join fetch r.addonOrder o
                where o.booking.id = :id order by r.id
                """, EquipmentRental.class).setParameter("id", bookingId).getResultList();
    }

    public List<EquipmentRental> rentalsForOrder(Long orderId) {
        return entities.createQuery("""
                select r from EquipmentRental r join fetch r.asset where r.addonOrder.id = :id order by r.id
                """, EquipmentRental.class).setParameter("id", orderId).getResultList();
    }

    public Long bookingIdForRental(Long rentalId) {
        return entities.createQuery("select r.addonOrder.booking.id from EquipmentRental r where r.id = :id", Long.class)
                .setParameter("id", rentalId).getResultList().stream().findFirst()
                .orElseThrow(() -> AccessRules.notFound("Equipment rental"));
    }

    public List<Long> legacyPhotoIds(long afterId) {
        return entities.createQuery("""
                select p.id from RentalPhoto p where p.id > :afterId and p.image is not null order by p.id
                """, Long.class).setParameter("afterId", afterId).setMaxResults(100).getResultList();
    }

    public Long bookingIdForPhoto(Long photoId) {
        return entities.createQuery("""
                select p.rental.addonOrder.booking.id from RentalPhoto p where p.id = :id
                """, Long.class).setParameter("id", photoId).getResultList().stream().findFirst()
                .orElseThrow(() -> AccessRules.notFound("Rental photo"));
    }

    public Optional<RentalPhoto> photoById(Long rentalId, Long photoId) {
        return entities.createQuery("""
                select p from RentalPhoto p where p.rental.id = :rentalId and p.id = :photoId
                """, RentalPhoto.class).setParameter("rentalId", rentalId).setParameter("photoId", photoId)
                .getResultList().stream().findFirst();
    }

    public Optional<RentalPhoto> photo(Long rentalId, RentalPhotoPhase phase, RentalPhotoAngle angle) {
        return entities.createQuery("""
                select p from RentalPhoto p where p.rental.id = :id and p.phase = :phase and p.angle = :angle
                """, RentalPhoto.class).setParameter("id", rentalId).setParameter("phase", phase)
                .setParameter("angle", angle).getResultList().stream().findFirst();
    }

    public List<AddonModels.PhotoView> photos(Long rentalId) {
        return entities.createQuery("""
                select p.id as id, p.phase as phase, p.angle as angle, p.uploadedAt as uploadedAt
                from RentalPhoto p where p.rental.id = :id order by p.id
                """, Tuple.class).setParameter("id", rentalId).getResultList().stream()
                .map(row -> new AddonModels.PhotoView(row.get("id", Long.class),
                        row.get("phase", RentalPhotoPhase.class), row.get("angle", RentalPhotoAngle.class),
                        row.get("uploadedAt", LocalDateTime.class))).toList();
    }

    public Map<Long, Long> photoCounts(Collection<Long> rentalIds, RentalPhotoPhase phase) {
        if (rentalIds.isEmpty()) {
            return Map.of();
        }
        return entities.createQuery("""
                select p.rental.id as rentalId, count(p) as count from RentalPhoto p
                where p.rental.id in :rentalIds and p.phase = :phase group by p.rental.id
                """, Tuple.class).setParameter("rentalIds", rentalIds).setParameter("phase", phase).getResultList()
                .stream().collect(Collectors.toMap(row -> row.get("rentalId", Long.class), row -> row.get("count", Long.class)));
    }
}
