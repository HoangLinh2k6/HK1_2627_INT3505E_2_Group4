package group4.backend.soa.auth;

import group4.backend.soa.enums.Role;

public record AuthPrincipal(Long userId, Role role, Long sessionId) {}
