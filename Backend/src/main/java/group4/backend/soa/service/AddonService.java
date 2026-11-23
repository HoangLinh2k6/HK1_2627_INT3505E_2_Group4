package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.AddonOrder;
import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.EquipmentAsset;
import group4.backend.soa.entity.EquipmentRental;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.entity.VenueAddon;
import group4.backend.soa.enums.AddonKind;
import group4.backend.soa.enums.BookingStatus;
import group4.backend.soa.enums.EquipmentAssetState;
import group4.backend.soa.enums.RentalPhotoAngle;
import group4.backend.soa.enums.RentalPhotoPhase;
import group4.backend.soa.repository.AddonStore;
import group4.backend.soa.repository.BookingRepository;
import group4.backend.soa.repository.VenueRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static group4.backend.soa.dto.AddonModels.*;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AddonService {
    private final AddonStore store;
    private final VenueRepository venues;
    private final BookingRepository bookings;
    private final AccessRules access;
    private final CourtSchedule schedule;

    @Transactional(readOnly = true)
    public List<ServiceView> catalog(Long venueId) {
        var venue = venue(venueId);
        access.requireVisible(venue);
        boolean owner = access.owns(venue);
        var items = store.catalog(venueId, owner);
        var stock = store.reservedStock(items.stream().filter(item -> item.getStockOnHand() != null)
                .map(VenueAddon::getId).toList(), schedule.now());
        return items.stream().map(item -> serviceView(item, stock.getOrDefault(item.getId(), 0L), owner)).toList();
    }

    public ServiceView createService(Long venueId, CreateService input) {
        var venue = ownerVenue(venueId);
        validateStock(input.kind(), input.stockOnHand());
        var item = new VenueAddon();
        item.setVenue(venue);
        item.setName(input.name().trim());
        item.setKind(input.kind());
        item.setUnitPrice(BigDecimal.valueOf(input.unitPrice()));
        item.setStockOnHand(input.stockOnHand());
        item.setActive(true);
        return serviceView(store.insert(item), 0L, true);
    }

    public ServiceView updateService(Long venueId, Long serviceId, UpdateService input) {
        ownerVenue(venueId);
        var item = serviceAtVenue(venueId, serviceId);
        validateStock(item.getKind(), input.stockOnHand());
        var reserved = store.reservedStock(List.of(serviceId), schedule.now()).getOrDefault(serviceId, 0L);
        if (input.stockOnHand() != null && input.stockOnHand() < reserved) {
            throw conflict("Stock cannot go below the quantity reserved by active bookings");
        }
        item.setName(input.name().trim());
        item.setUnitPrice(BigDecimal.valueOf(input.unitPrice()));
        item.setActive(input.active());
        item.setStockOnHand(input.stockOnHand());
        return serviceView(item, reserved, true);
    }

    @Transactional(readOnly = true)
    public List<AssetView> assets(Long venueId, Long serviceId) {
        access.requireOwner(venue(venueId));
        requireRentalService(venueId, serviceId);
        return store.assets(serviceId).stream().map(AddonService::assetView).toList();
    }

    public AssetView createAsset(Long venueId, Long serviceId, CreateAsset input) {
        var venue = ownerVenue(venueId);
        var addon = requireRentalService(venueId, serviceId);
        String code = input.code().toUpperCase(Locale.ROOT);
        if (store.assetCodeExists(venueId, code)) {
            throw conflict("Asset code is already used at this venue");
        }
        var asset = new EquipmentAsset();
        asset.setVenue(venue);
        asset.setAddon(addon);
        asset.setCode(code);
        asset.setState(EquipmentAssetState.AVAILABLE);
        return assetView(store.insert(asset));
    }

    public AssetView updateAsset(Long venueId, Long serviceId, Long assetId, UpdateAsset input) {
        ownerVenue(venueId);
        requireRentalService(venueId, serviceId);
        var asset = store.require(EquipmentAsset.class, assetId, "Equipment asset");
        if (!asset.getAddon().getId().equals(serviceId)) {
            throw AccessRules.notFound("Equipment asset");
        }
        if (input.state() != EquipmentAssetState.AVAILABLE && store.assetsInUse(List.of(assetId))) {
            throw conflict("Return the equipment before changing its availability");
        }
        asset.setState(input.state());
        return assetView(asset);
    }

    @Transactional(readOnly = true)
    public BookingServices bookingServices(Long bookingId) {
        var booking = store.require(Booking.class, bookingId, "Booking");
        access.requireBookingAccess(booking);
        return bookingView(booking);
    }

    public OrderView reserve(Long bookingId, String key, OrderRequest input) {
        if (!key.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw badRequest("Invalid Idempotency-Key");
        }
        var booking = lockBooking(bookingId);
        access.requireCustomerBooking(booking);
        var existing = store.orderByKey(bookingId, key);
        if (existing.isPresent()) {
            var order = existing.get();
            if (!order.getAddon().getId().equals(input.serviceId()) || order.getQuantity() != input.quantity()) {
                throw conflict("Idempotency-Key is already used for another service order");
            }
            return orderView(order, store.rentalsForOrder(order.getId()), schedule.now());
        }
        var venueId = booking.getCourt().getVenue().getId();
        lockVenue(venueId);
        var now = schedule.now();
        if (!reservesStock(booking, now)
                || (booking.getCheckedInAt() == null && !booking.getStartTime().isAfter(now))) {
            throw conflict("This booking is no longer accepting service orders");
        }
        var item = serviceAtVenue(venueId, input.serviceId());
        if (!item.isActive()) {
            throw conflict("This service is unavailable");
        }

        List<EquipmentAsset> allocated = List.of();
        if (item.getKind() == AddonKind.CONSUMABLE) {
            long reserved = store.reservedStock(List.of(item.getId()), now).getOrDefault(item.getId(), 0L);
            if (item.getStockOnHand() - reserved < input.quantity()) {
                throw conflict("Insufficient stock for this service");
            }
        } else {
            allocated = store.availableAssets(item.getId(), booking, now, input.quantity());
            if (allocated.size() != input.quantity()) {
                throw conflict("Insufficient equipment for the booking interval");
            }
        }

        var order = new AddonOrder();
        order.setBooking(booking);
        order.setAddon(item);
        order.setRequestKey(key);
        order.setItemName(item.getName());
        order.setQuantity(input.quantity());
        order.setUnitPrice(item.getUnitPrice());
        order.setOrderedAt(now);
        store.insert(order);
        var rentals = allocated.stream().map(asset -> {
            var rental = new EquipmentRental();
            rental.setAsset(asset);
            rental.setAddonOrder(order);
            return store.insert(rental);
        }).toList();
        return orderView(order, rentals, now);
    }

    public void cancelOrder(Long bookingId, Long orderId) {
        var booking = lockBooking(bookingId);
        access.requireBookingAccess(booking);
        var order = orderForBooking(bookingId, orderId);
        if (order.getDeliveredAt() != null) {
            throw conflict("Delivered orders are already part of the bill");
        }
        if (order.getCancelledAt() == null) {
            order.setCancelledAt(schedule.now());
        }
    }

    public BookingServices checkIn(Long bookingId) {
        var booking = lockBooking(bookingId);
        access.requireBookingOwner(booking);
        if (booking.getCheckedInAt() == null) {
            var now = schedule.now();
            if (booking.getStatus() != BookingStatus.CONFIRMED
                    || now.isBefore(booking.getStartTime()) || !now.isBefore(booking.getEndTime())) {
                throw conflict("Check-in requires a confirmed booking in its scheduled interval");
            }
            booking.setCheckedInAt(now);
        }
        return bookingView(booking);
    }

    public OrderView deliver(Long bookingId, Long orderId, DeliverRequest input) {
        var booking = lockBooking(bookingId);
        access.requireBookingOwner(booking);
        lockVenue(booking.getCourt().getVenue().getId());
        var order = orderForBooking(bookingId, orderId);
        var rentals = store.rentalsForOrder(orderId);
        var assetIds = rentals.stream().map(rental -> rental.getAsset().getId()).toList();
        var actualIds = input.assetIds() == null ? List.<Long>of() : input.assetIds();
        if (assetIds.size() != actualIds.size() || !new HashSet<>(assetIds).equals(new HashSet<>(actualIds))) {
            throw conflict("Scanned asset IDs must match the allocated equipment");
        }
        if (order.getDeliveredAt() != null) {
            return orderView(order, rentals, schedule.now());
        }

        var now = schedule.now();
        if (booking.getCheckedInAt() == null || !reservesStock(booking, now) || order.getCancelledAt() != null) {
            throw conflict("Only a reserved order on a current checked-in booking can be delivered");
        }
        var item = order.getAddon();
        if (item.getKind() == AddonKind.CONSUMABLE) {
            if (item.getStockOnHand() < order.getQuantity()) {
                throw conflict("Insufficient physical stock");
            }
            item.setStockOnHand(item.getStockOnHand() - order.getQuantity());
        } else {
            if (rentals.stream().anyMatch(rental -> rental.getAsset().getState() != EquipmentAssetState.AVAILABLE)
                    || store.assetsInUse(assetIds)) {
                throw conflict("Allocated equipment is currently unavailable");
            }
            rentals.forEach(rental -> rental.setHandedOutAt(now));
        }
        order.setDeliveredAt(now);
        order.setDeliveredBy(access.userId());
        return orderView(order, rentals, now);
    }

    public BookingServices checkOut(Long bookingId) {
        var booking = lockBooking(bookingId);
        access.requireBookingAccess(booking);
        if (booking.getCheckedOutAt() != null) {
            return bookingView(booking);
        }
        if (booking.getCheckedInAt() == null || booking.getStatus() != BookingStatus.CONFIRMED) {
            throw conflict("Check-out requires a checked-in booking");
        }
        lockVenue(booking.getCourt().getVenue().getId());
        var handedOut = store.rentals(bookingId).stream().filter(rental -> rental.getHandedOutAt() != null).toList();
        var rentalIds = handedOut.stream().map(EquipmentRental::getId).toList();
        requirePhotos(rentalIds, RentalPhotoPhase.CHECK_IN);
        requirePhotos(rentalIds, RentalPhotoPhase.CHECK_OUT);
        var now = schedule.now();
        Long actor = access.userId();
        handedOut.forEach(rental -> {
            rental.setReturnedAt(now);
            rental.setReturnedBy(actor);
        });
        store.orders(bookingId).stream()
                .filter(order -> order.getCancelledAt() == null && order.getDeliveredAt() == null)
                .forEach(order -> order.setCancelledAt(now));
        booking.setCheckedOutAt(now);
        booking.setStatus(BookingStatus.COMPLETED);
        return bookingView(booking);
    }

    private BookingServices bookingView(Booking booking) {
        var now = schedule.now();
        var rentals = store.rentals(booking.getId()).stream().collect(groupingBy(rental -> rental.getAddonOrder().getId()));
        var orders = store.orders(booking.getId()).stream()
                .map(order -> orderView(order, rentals.getOrDefault(order.getId(), List.of()), now)).toList();
        var deliveredTotal = orders.stream().filter(order -> order.deliveredAt() != null)
                .map(OrderView::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BookingServices(booking.getId(), booking.getCheckedInAt(), booking.getCheckedOutAt(),
                booking.getTotalPrice(), deliveredTotal, booking.getTotalPrice().add(deliveredTotal), orders);
    }

    private static OrderView orderView(AddonOrder order, List<EquipmentRental> rentals, LocalDateTime now) {
        String state;
        if (order.getDeliveredAt() != null) {
            state = "DELIVERED";
        } else {
            state = order.getCancelledAt() == null && reservesStock(order.getBooking(), now) ? "RESERVED" : "RELEASED";
        }
        var entries = rentals.stream().map(rental -> new RentalView(rental.getId(), rental.getAsset().getId(),
                rental.getAsset().getCode(), rental.getHandedOutAt(), rental.getReturnedAt(), rental.getReturnedBy())).toList();
        return new OrderView(order.getId(), order.getAddon().getId(), order.getItemName(), order.getAddon().getKind(),
                order.getQuantity(), order.getUnitPrice(), order.total(), state, order.getOrderedAt(),
                order.getDeliveredAt(), order.getDeliveredBy(), order.getCancelledAt(), entries);
    }

    private static ServiceView serviceView(VenueAddon item, long reserved, boolean owner) {
        Integer stock = item.getStockOnHand();
        return new ServiceView(item.getId(), item.getName(), item.getKind(), item.getUnitPrice(),
                item.isActive(), owner ? stock : null, stock == null ? null : Math.max(0, stock - reserved));
    }

    private static AssetView assetView(EquipmentAsset asset) {
        return new AssetView(asset.getId(), asset.getAddon().getId(), asset.getCode(), asset.getState());
    }

    private static void validateStock(AddonKind kind, Integer stock) {
        if ((kind == AddonKind.CONSUMABLE) != (stock != null)) {
            throw badRequest("stockOnHand is required for consumables and omitted for rentals");
        }
    }

    private static boolean reservesStock(Booking booking, LocalDateTime now) {
        if (!booking.getEndTime().isAfter(now)) {
            return false;
        }
        return booking.getStatus() == BookingStatus.CONFIRMED || (booking.getStatus() == BookingStatus.PENDING
                && booking.getStartTime().isAfter(now)
                && (booking.getExpiresAt() == null || booking.getExpiresAt().isAfter(now)));
    }

    private Venue venue(Long id) {
        return store.require(Venue.class, id, "Venue");
    }

    private Venue ownerVenue(Long id) {
        var venue = venue(id);
        access.requireOwner(venue);
        lockVenue(id);
        return venue;
    }

    private void lockVenue(Long id) {
        venues.lockById(id).orElseThrow(() -> AccessRules.notFound("Venue"));
    }

    private Booking lockBooking(Long id) {
        return bookings.lockById(id).orElseThrow(() -> AccessRules.notFound("Booking"));
    }

    private VenueAddon serviceAtVenue(Long venueId, Long serviceId) {
        var item = store.require(VenueAddon.class, serviceId, "Venue service");
        if (!item.getVenue().getId().equals(venueId)) {
            throw AccessRules.notFound("Venue service");
        }
        return item;
    }

    private VenueAddon requireRentalService(Long venueId, Long serviceId) {
        var item = serviceAtVenue(venueId, serviceId);
        if (item.getKind() != AddonKind.RENTAL) {
            throw badRequest("Only rental services have individually tracked assets");
        }
        return item;
    }

    private AddonOrder orderForBooking(Long bookingId, Long orderId) {
        var order = store.require(AddonOrder.class, orderId, "Service order");
        if (!order.getBooking().getId().equals(bookingId)) {
            throw AccessRules.notFound("Service order");
        }
        return order;
    }

    private void requirePhotos(List<Long> rentalIds, RentalPhotoPhase phase) {
        var counts = store.photoCounts(rentalIds, phase);
        if (rentalIds.stream().anyMatch(id -> counts.getOrDefault(id, 0L) < RentalPhotoAngle.values().length)) {
            throw conflict("Both " + phase + " angles are required for each delivered rental");
        }
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
