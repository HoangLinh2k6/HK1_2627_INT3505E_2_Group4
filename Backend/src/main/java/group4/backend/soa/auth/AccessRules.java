package group4.backend.soa.auth;

import group4.backend.soa.entity.Booking;
import group4.backend.soa.entity.Court;
import group4.backend.soa.entity.Venue;
import group4.backend.soa.enums.CourtStatus;
import group4.backend.soa.enums.Role;
import group4.backend.soa.enums.VenueStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccessRules {
    public Long userId() {
        AuthPrincipal principal = principal();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
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
            throw notFound("Venue");
        }
    }

    public void requireVisible(Court court) {
        if (!canSee(court)) {
            throw notFound("Court");
        }
    }

    public void requireOwner(Venue venue) {
        if (!owns(venue)) {
            throw notFound("Venue");
        }
    }

    public void requireBookingAccess(Booking booking) {
        if (!(userId().equals(booking.getUser().getId()) || owns(booking.getCourt().getVenue()))) {
            throw notFound("Booking");
        }
    }

    public void requireBookingOwner(Booking booking) {
        if (!owns(booking.getCourt().getVenue())) {
            throw notFound("Booking");
        }
    }

    public void requireCustomerBooking(Booking booking) {
        if (!userId().equals(booking.getUser().getId())) {
            throw notFound("Booking");
        }
    }

    public static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    private static AuthPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
                ? principal : null;
    }
}
