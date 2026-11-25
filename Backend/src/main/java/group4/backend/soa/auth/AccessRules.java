package group4.backend.soa.auth;

import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.Role;
import group4.backend.soa.enums.VenueStatus;
import group4.backend.soa.error.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccessRules {
    public Long userId() {
        AuthPrincipal principal = principal();
        if (principal == null) {
            throw ApiException.unauthorized("AUTHENTICATION_REQUIRED",
                    "Authentication is required. Provide a valid Bearer access token in the Authorization header.");
        }
        return principal.userId();
    }

    public boolean owns(Venue venue) {
        AuthPrincipal principal = principal();
        return principal != null && principal.role() == Role.OWNER
                && principal.userId().equals(venue.getOwner().getId());
    }

    public boolean canSee(Venue venue) {
        return venue.getStatus() == VenueStatus.ACTIVE || owns(venue);
    }

    public boolean canSee(Court court) {
        return owns(court.getVenue()) || (court.getStatus() == CourtStatus.AVAILABLE
                && court.getVenue().getStatus() == VenueStatus.ACTIVE);
    }

    public void requireVisible(Venue venue) {
        if (!canSee(venue)) {
            throw notFound("Venue", venue.getId());
        }
    }

    public void requireVisible(Court court) {
        if (!canSee(court)) {
            throw notFound("Court", court.getId());
        }
    }

    public void requireOwner(Venue venue) {
        if (!owns(venue)) {
            throw notFound("Venue", venue.getId());
        }
    }

    public void requireBookingAccess(Booking booking) {
        if (!(userId().equals(booking.getUser().getId()) || owns(booking.getCourt().getVenue()))) {
            throw notFound("Booking", booking.getId());
        }
    }

    public void requireBookingOwner(Booking booking) {
        if (!owns(booking.getCourt().getVenue())) {
            throw notFound("Booking", booking.getId());
        }
    }

    public void requireCustomerBooking(Booking booking) {
        if (!userId().equals(booking.getUser().getId())) {
            throw notFound("Booking", booking.getId());
        }
    }

    public static ResponseStatusException notFound(String resource) {
        return ApiException.notFound("RESOURCE_NOT_FOUND", resource + " was not found or is not accessible.");
    }

    public static ResponseStatusException notFound(String resource, Long id) {
        if (id == null) {
            return notFound(resource);
        }
        return ApiException.notFound("RESOURCE_NOT_FOUND", resource + " #" + id + " was not found or is not accessible.");
    }

    private static AuthPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
                ? principal : null;
    }
}
