package group4.backend.soa.auth;

import group4.backend.soa.entity.User;
import group4.backend.soa.enums.Role;

public record AuthUser(String id, String email, String fullName, String phone, Role role) {
    public static AuthUser from(User user) {
        return new AuthUser(user.getId().toString(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole());
    }
}
